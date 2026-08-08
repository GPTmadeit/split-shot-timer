# SPLIT — shot timer for Pixel Watch 4 + phone

The watch is the instrument. It beeps, it listens, it times. The phone is a
replica that shows you what happened.

## Why it works at all

A 9mm at the shooter's ear runs 160–165 dB SPL. The MEMS capsule in the watch
clips at roughly 120–130 dB. Every shot overdrives the microphone by 30–45 dB,
so the amplitude coming off the ADC is meaningless — it is simply *railed*.

That does not matter, because a shot timer does not need a level. It needs an
arrival time. And saturation turns out to be the best discriminator available:
your own muzzle rails the converter for milliseconds, while the shooter two bays
over arrives ~30 dB down and never rails at all. `clipGate` keys on exactly that,
which is how this rejects neighbours when a plain energy threshold cannot.

Three details carry the accuracy:

| Concern | How it is handled |
|---|---|
| Timing | `AudioRecord.getTimestamp()` gives a (framePosition, nanoTime) anchor from the audio HAL. Sample times are interpolated from it, so resolution is one sample (20.8 µs @ 48 kHz), not one buffer (~10 ms). |
| Start instant | `AudioTrack.getTimestamp()` rewound to frame 0 — the true moment the tone hit the speaker. First-shot time is a difference of two HAL timestamps, not two thread wakeups. |
| Gain | `AudioSource.UNPROCESSED` where supported, else `VOICE_RECOGNITION`. With AGC live, gain ducks after round one and every subsequent split is measured through a moving target. |

`RecoilGate` is the part a dedicated timer cannot do: correlate the report
against a wrist impulse from the accelerometer within ±40 ms. Ships **off** —
see the caveats in `RecoilGate.kt` before trusting it.

## Modules

```
core/     models + the watch↔phone wire contract
wear/     the instrument: mic, tone, clock, state machine  (com.carlb.split)
mobile/   the replica: receiver, log, analysis             (com.carlb.split)
```

Both apps share one `applicationId`. That is deliberate — Play uses it to
deliver the watch APK to a paired watch when the phone app installs. Upload both
to the same Play Console app; the watch build goes in the Wear OS track. No
Gradle-side embedding is involved.

## The two transports

This is the core architectural decision, and it is driven by how a range
actually works: your phone is in a bag on the bench and Bluetooth drops
constantly.

- **`MessageClient` → `/split/live`** — low latency, best effort, silently
  dropped when the link is down. Used *only* to mirror a running string on the
  phone. Nothing the timer needs travels here.
- **`DataClient` → `/split/string/<id>`** — replicated and durable. A string
  written while the phone is out of range syncs the moment it returns. The phone
  receiver is a `WearableListenerService`, so strings land even if the phone app
  was never opened.

The watch writes to its own store **before** it attempts to reach the phone. The
watch is the system of record; the phone is a replica. Nothing in the timing path
ever waits on the phone.

## Build

```bash
./gradlew :wear:assembleDebug :mobile:assembleDebug
```

Install:

```bash
adb -s <watch> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

## Version pinning — read before upgrading

Versions in `gradle/libs.versions.toml` are **not** "latest". They are the newest
stable releases whose AAR metadata still permits `compileSdk 36` / AGP 8.13.2.
Anything newer gates on `compileSdk 37`, which is not published in the SDK
manager yet. These were verified by reading `aar-metadata.properties` out of each
artifact, not by guessing.

One deliberate exception: `material3` is pinned to **1.5.0-alpha18**.
`MaterialExpressiveTheme`, `MotionScheme` and friends exist in 1.4.0 stable but
are compiled `internal` there, so the Expressive API is unreachable. alpha18 is
the newest build that exposes it publicly *and* still allows compileSdk 36.
Revisit once platform 37 ships.

## Status

Both modules compile and package; manifests verified (`foregroundServiceType`
= microphone, standalone watch app, matching applicationIds). **Nothing has been
run on hardware, and no live fire has been recorded through it.** The detector
constants — clip-run length, blanking, the recoil window and threshold — are
reasoned starting points, not measured ones. Expect to characterise them against
your own gun and your own range.

`split-timer.html` in this directory is the browser prototype of the same
detector and UI; it is usable today and good for sanity-checking drill flow.
