# Architecture

How SPLIT is put together, and why. This document explains decisions; the code explains
mechanics.

## The central constraint

A 9mm at the shooter's ear runs 160–165 dB SPL. The MEMS capsule in a watch clips at roughly
120–130 dB. Every shot overdrives the microphone by 30–45 dB.

Three consequences follow, and they shape the whole design:

1. **Amplitude carries no information.** The converter is railed. You cannot measure how loud a
   shot was, only that it happened.
2. **Therefore the only quantity worth extracting is time.** This is fine — a shot timer reports
   arrival times, not levels.
3. **Saturation is a feature.** Your muzzle rails the converter for milliseconds. A shooter two
   bays over arrives ~30 dB down and never rails. Gating on *clip-run length* is a far better
   discriminator than any loudness threshold.

## Module layout

```
core/     pure Kotlin: models, statistics, the wire contract   (42 unit tests)
wear/     the instrument                                        com.carlb.split
mobile/   the replica                                           com.carlb.split
docs/     browser prototype, diagrams
```

`:core` has no Android dependencies beyond the library plugin, which is what makes it unit
testable on the JVM. Everything with a real-world side effect lives in `:wear` or `:mobile`.

Both apps share one `applicationId`. That is what makes Google Play deliver the watch APK to a
paired watch when the phone app installs. It also means the watch module must use fully
qualified component names in its manifest — a relative `.MainActivity` resolves against the
shared namespace and points at a class that does not exist. That mistake shipped once; see the
comment in `wear/src/main/AndroidManifest.xml`.

## Timing

This is the part that has to be right, so it does not use the obvious approach.

**Naive approach:** timestamp the audio buffer when your callback runs. This is wrong. Buffer
delivery is jittered by thread scheduling, easily several milliseconds, and it varies.

**What SPLIT does:** `AudioRecord.getTimestamp()` returns a `(framePosition, nanoTime)` pair
from the audio HAL — a known frame, anchored to a known instant on `CLOCK_MONOTONIC`. Any
sample's true wall-clock time is interpolated from that anchor:

```
nanos = anchorNanos + (absoluteFrame - anchorFrame) * 1e9 / sampleRate
```

Resolution is therefore one sample — 20.8 µs at 48 kHz — regardless of when the buffer happened
to arrive.

The start instant gets the same treatment. `AudioTrack.getTimestamp()` reports frames already
presented; rewinding to frame 0 gives the moment the tone physically left the speaker.
**First-shot time is a difference of two HAL timestamps**, not a difference of two thread
wakeups.

> Resolution is not accuracy. The method resolves to a sample; end-to-end accuracy depends on
> hardware latencies that have not been measured on any device. See `ShotDetector.kt`.

## Detection pipeline

`wear/audio/ShotDetector.kt`, running on a dedicated max-priority thread.

**Source selection.** `MediaRecorder.AudioSource.UNPROCESSED` where the device reports support,
otherwise `VOICE_RECOGNITION`. This matters more than anything else in the file: with automatic
gain control live, gain ducks hard after the first round and every subsequent split is measured
through a moving target.

**Two passes per block.** Block statistics are computed before onset detection, because the
clip gate asks "did this transient rail?" and the answer lives in the samples *after* the
leading edge, not at it. A single forward pass would evaluate the gate against a clip run that
had not happened yet.

**Onset criteria**, all of which must hold:

| Criterion | Purpose |
| --- | --- |
| Peak ≥ threshold | Basic sensitivity gate (`sensitivityDb`) |
| Clip run ≥ 3 samples | Own-muzzle discrimination (optional, on by default) |
| Outside blanking window | Rejects reverb tails |
| After the start tone | The tone must never count as shot one |

**Recoil correlation** (`wear/sensor/RecoilGate.kt`) is the second, independent question: did
this wrist take an impulse within ±40 ms? A report with no matching impulse is somebody else's.

One subtlety: `SensorEvent.timestamp` is boot-based while audio timestamps are
`CLOCK_MONOTONIC`. They tick at the same rate while awake, so one offset measured at start-up
converts between them.

This gate ships **off**. Transfer depends on grip and on which wrist wears the watch, so it is
scored as a vote rather than enforced as a veto.

## The two transports

The design driver is physical: at a range your phone is in a bag thirty feet away and Bluetooth
drops constantly.

| | `MessageClient` | `DataClient` |
| --- | --- | --- |
| Path | `/split/live` | `/split/string/<id>` |
| Carries | Live mirror of a running string | Completed strings |
| Delivery | Best effort, dropped if disconnected | Replicated, syncs when the link returns |
| If it fails | Nothing is lost — a late mirror is worthless anyway | Cannot fail; that is the point |

**The watch writes to its own store before it attempts to reach the phone.** The watch is the
system of record; the phone is a replica. Nothing in the timing path ever awaits a network call
— `WearSync.sendLive()` is fire-and-forget by construction.

On the phone, `PhoneListener` is a `WearableListenerService`, not something bound to the UI, so
data items are delivered even if the phone app has never been opened. Upserts are keyed by
string id because `DataClient` may redeliver.

## State machine

`wear/timer/TimerEngine.kt`:

```
Idle ──arm()──▶ Armed ──(random delay)──▶ Running ──stop()──▶ Complete
  ▲               │                          │                    │
  └───reset()─────┴──────────────────────────┴────auto-repeat─────┘
```

`TimerService` is a foreground service of type `microphone`. Android 14 / Wear OS 5 only permit
starting one while the app is already foregrounded, which is exactly what happens when the user
opens the timer and presses start. Once running it holds the microphone and a partial wake lock
through ambient mode, so the watch keeps timing with the display dimmed.

## Rendering

The bezel is the instrument. The acoustic envelope draws inward from the band and each detected
shot burns a tick outward at its angular position in the string, so by the end of a run the ring
*is* the string — readable without parsing digits.

The whole face runs off a **single `withFrameNanos` loop** rather than recomposing per tick. The
readout updates at display rate while the composition itself stays still.

Motion is physics, not duration curves:

| Element | Spec | Why |
| --- | --- | --- |
| Start shockwave | `spring(dampingRatio = 0.34)` | Low damping overshoots — reads as an impact, not a screen wipe |
| Per-shot ring kick | `spring(dampingRatio = 0.45)` | Independent of the shockwave, so a fast split stacks rather than cancels |
| Trend path (phone) | `tween(900, LinearEasing)` | The one place duration beats a spring: constant-rate reveal reads as *writing* |

The ring auto-rescales (5 s → 10 s → 20 s) as a string runs long. On rescale the envelope is
folded in half so the trace keeps lining up with the ticks.

## Storage

DataStore with kotlinx-serialization on both sides — no Room, no KSP, no code generation. A shot
log is a small append-mostly list; a database would be more moving parts for no benefit.

## Things deliberately not done

- **No network code.** Neither app requests `INTERNET`. There is no telemetry and no audio
  retention; samples are analysed in memory and discarded.
- **No Room/KSP.** See above.
- **No ktlint Gradle plugin.** Formatting is enforced by the ktlint CLI in CI, which keeps the
  build graph smaller and gives the same check locally.
- **No instrumented tests.** They would need a device or emulator in CI and, more importantly,
  could not exercise the part that actually matters — real gunshot audio.
