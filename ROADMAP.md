# Roadmap

This is a direction of travel, not a commitment. Items are ordered by how much they would
actually improve the project, which is not the same as how interesting they are to build.

## The blocker: nothing has been validated on hardware

Everything below is secondary to this. The project builds, is tested where it can be tested, and
is reasoned carefully — but **no gunshot has ever reached this code.** Until that changes, every
detector constant is a guess and no accuracy claim can be made.

### 1. Bench harness for recorded audio

Replay WAV files through `ShotDetector` on the JVM so the constants can be tuned without a range
trip, and so regressions in detection become testable in CI.

This requires decoupling the detector from `AudioRecord`, which is worth doing anyway.

### 2. Characterise the four constants

| Constant | Current | Needs |
| --- | --- | --- |
| Clip-run threshold | 3 samples | Measurement across calibres and suppressed/unsuppressed |
| Echo blanking | 60 ms | Indoor vs outdoor reverb tails |
| Recoil window | ±40 ms | Actual audio/IMU latency offset on a real device |
| Recoil threshold | 18 m/s² | Per-calibre impulse, per grip and wrist |

### 3. Measure end-to-end timing accuracy

The method resolves to one sample. What that means in practice depends on hardware latencies
nobody has measured. A known-interval acoustic source and a reference timer would settle it.
Until then the README should keep saying "unknown".

## Features worth building once detection is trusted

- **Suppressed-host mode.** A suppressed 9mm can sit below the microphone's clipping point,
  which defeats the clip gate entirely. Needs a different discriminator.
- **String comparison.** Overlay two strings to see where time actually went.
- **Longer-term trends.** Draw and split σ over weeks, not just the current session.
- **CSV/JSON export from the phone.** The browser prototype already does CSV; the phone does not.
- **Custom drills.** User-defined shot count, par, and goals.
- **Watch face complication** showing the last string's draw time.
- **Tile** for one-tap start without opening the app.

## Engineering

- **Instrumented tests** for the audio and sensor layers, running on an emulator in CI.
- **Baseline profiles** to cut startup time on the watch.
- **Release signing.** Currently every artifact is debug-signed. Play distribution needs a real
  key, held by the repository owner.
- **Unpin `compileSdk`.** Once platform 37 is published, drop the held-back dependency versions
  and move to AGP 9.

## Explicitly not planned

- **Network sync, accounts, or cloud storage.** Both apps are offline by design and the absence
  of `INTERNET` permission is a feature. It will not be added.
- **Match/official scoring use.** This is a practice tool. Use a certified timer for anything
  that counts.
- **Audio recording or retention.** Samples are analysed and discarded. That will not change.

## Contributing to this

The most valuable contribution by a wide margin is a
[detection accuracy report](https://github.com/GPTmadeit/split-shot-timer/issues/new?template=detection_accuracy.yml)
from a real range session. Everything in section 2 above is unblocked by data, not by code.
