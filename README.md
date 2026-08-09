<div align="center">

# SPLIT

**A shot timer for Pixel Watch 4.**
The watch beeps, listens and times. The phone is a replica that shows you what happened.

[**▶ Try the browser prototype**](https://gptmadeit.github.io/split-shot-timer/) &nbsp;·&nbsp; [Why it works](#why-it-works-at-all) &nbsp;·&nbsp; [Architecture](#the-two-transports) &nbsp;·&nbsp; [Build](#build)

![Wear OS 6](https://img.shields.io/badge/Wear_OS-6-FF5A1F?style=flat-square)
![Android 16](https://img.shields.io/badge/Android-16_(API_36)-8B98A5?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-8B98A5?style=flat-square)
![Compose](https://img.shields.io/badge/Compose-M3_Expressive-C89A2E?style=flat-square)
![Build](https://img.shields.io/badge/gradlew_build-passing-3FD48B?style=flat-square)
![Hardware](https://img.shields.io/badge/hardware_tested-no-FF4D4D?style=flat-square)

</div>

---

## Why it works at all

A 9mm at the shooter's ear runs **160–165 dB SPL**. The MEMS capsule in the watch clips at
roughly **120–130 dB**. Every shot overdrives the microphone by 30–45 dB, so the amplitude
coming off the ADC is meaningless — it is simply *railed*.

That does not matter, because a shot timer does not need a level. It needs an arrival time.

And saturation turns out to be the best discriminator available: **your own muzzle rails the
converter for milliseconds, while the shooter two bays over arrives ~30 dB down and never
rails at all.** `clipGate` keys on exactly that, which is how this rejects neighbours when a
plain energy threshold cannot.

Three details carry the accuracy:

| Concern | How it is handled |
| --- | --- |
| **Timing** | `AudioRecord.getTimestamp()` gives a `(framePosition, nanoTime)` anchor from the audio HAL. Sample times are interpolated from it, so resolution is **one sample** (20.8 µs @ 48 kHz), not one buffer (~10 ms). |
| **Start instant** | `AudioTrack.getTimestamp()` rewound to frame 0 — the true moment the tone hit the speaker. First-shot time is a difference of two HAL timestamps, not two thread wakeups. |
| **Gain** | `AudioSource.UNPROCESSED` where supported, else `VOICE_RECOGNITION`. With AGC live, gain ducks after round one and every subsequent split is measured through a moving target. |

`RecoilGate` is the part a dedicated timer cannot do: correlate the report against a wrist
impulse from the accelerometer within ±40 ms. **Ships off** — see the caveats in
[`RecoilGate.kt`](wear/src/main/java/com/carlb/split/wear/sensor/RecoilGate.kt) before
trusting it.

---

## The two transports

![Architecture](docs/architecture.svg)

This is the core architectural decision, and it is driven by how a range actually works:
your phone is in a bag on the bench and Bluetooth drops constantly.

- **`MessageClient` → `/split/live`** — low latency, best effort, silently dropped when the
  link is down. Used *only* to mirror a running string. Nothing the timer needs travels here.
- **`DataClient` → `/split/string/<id>`** — replicated and durable. A string written while
  the phone is out of range syncs the moment it returns. The receiver is a
  `WearableListenerService`, so strings land even if the phone app was never opened.

The watch writes to its own store **before** it attempts to reach the phone. The watch is the
system of record; the phone is a replica.

---

## The face

The bezel is the instrument. The acoustic envelope draws inward from the band, and every
detected shot burns a tick outward at its angular position in the string. By the end of a run
**the ring _is_ the string** — you read cadence at a glance without parsing digits, which is
the point when the watch is on your wrist and your eyes are on the target.

Motion is physics, not duration curves. The start tone fires a shockwave on
`spring(dampingRatio = 0.34)`; each shot kicks the ring independently at `0.45` so a fast
split stacks on the previous kick rather than cancelling it. The whole face runs off a single
`withFrameNanos` loop rather than recomposing per tick.

The phone uses `MotionScheme.expressive()` throughout, a `graphics-shapes` `Morph`
(circle → rosette) on the personal-best badge that only animates when the string *is* a best,
staggered spring entry on split bars, and a self-drawing trend path.

---

## Modules

```
core/     models + the watch↔phone wire contract
wear/     the instrument: mic, tone, clock, state machine   (com.carlb.split)
mobile/   the replica: receiver, log, analysis              (com.carlb.split)
docs/     browser prototype of the same detector and UI
```

Both apps share one `applicationId`. That is deliberate — Play uses it to deliver the watch
APK to a paired watch when the phone app installs. Upload both to the same Play Console app;
the watch build goes in the Wear OS track. No Gradle-side embedding is involved.

## Drills

Bill Drill · Failure to Stop · F.A.S.T. · 1‑Reload‑1 · Blake · El Presidente · Casino ·
Dot Torture · Freestyle

Shot count and par are enforced: the string auto-stops on the last shot and grades against
the standard. Split σ is reported alongside the fastest and slowest split, because
consistency is the number that says whether you are shooting a cadence or got lucky once.

---

## Build

```bash
./gradlew :wear:assembleDebug :mobile:assembleDebug
```

```bash
adb -s <watch-serial> install -r wear/build/outputs/apk/debug/wear-debug.apk
```

### Version pinning — read before upgrading

Versions in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) are **not** "latest".
They are the newest stable releases whose AAR metadata still permits `compileSdk 36` /
AGP 8.13.2. Anything newer gates on `compileSdk 37`, which is not published in the SDK manager
yet. These were verified by reading `aar-metadata.properties` out of each artifact, not by
guessing.

One deliberate exception: `material3` is pinned to **1.5.0-alpha18**. `MaterialExpressiveTheme`,
`MotionScheme` and friends exist in 1.4.0 stable but are compiled `internal` there, so the
Expressive API is unreachable. alpha18 is the newest build that exposes it publicly *and*
still allows compileSdk 36. Revisit once platform 37 ships.

---

## Status

`./gradlew build` passes clean — debug and release, both modules, Android Lint reporting **no
issues** on `:wear` and `:mobile`. Manifests verified against the packaged APKs
(`foregroundServiceType` = microphone, standalone watch app, matching applicationIds,
fully-qualified component names).

The 10 remaining warnings on `:core` are all `GradleDependency` / `AndroidGradlePluginVersion`
notices about the version pins above. Taking any of them breaks the build until platform 37
ships.

> [!WARNING]
> **Nothing has been run on hardware, and no live fire has been recorded through it.**
> The detector constants — clip-run length, blanking, and the recoil window and threshold —
> are reasoned starting points, not measured ones. Expect to characterise them against your
> own gun and your own range before trusting a number this produces.

### Browser prototype

[**gptmadeit.github.io/split-shot-timer**](https://gptmadeit.github.io/split-shot-timer/) —
the same detection approach in Web Audio, useful for sanity-checking drill flow. Works on a
phone; add it to your home screen and it runs chromeless. Grant microphone access for live
detection, or tap the face (spacebar on desktop) to log shots by hand.

Note that browsers apply AGC and noise suppression by default, which crushes gunshot
transients; the page requests them off, but a native build is the only way to truly pin
`UNPROCESSED`.

---

<sub>No licence file is included, so default copyright applies — add one if you want others to
reuse this.</sub>
