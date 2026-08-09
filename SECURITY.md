# Security Policy

## Supported versions

This project is pre-1.0 and has a single active line. Only the latest release receives fixes.

| Version | Supported |
| --- | --- |
| Latest release | Yes |
| Anything older | No |

## Reporting a vulnerability

**Please do not open a public issue.**

Report privately through GitHub Security Advisories:

**<https://github.com/GPTmadeit/split-shot-timer/security/advisories/new>**

Include what you found, how to reproduce it, and what an attacker could achieve. If you have a
proof of concept, attach it — but please redact anything personal.

### What to expect

This is a small hobby project maintained by one person, so I will not pretend to an enterprise
SLA. Realistically: an acknowledgement within about a week, and a fix or a clear explanation of
why something is not a vulnerability once I have looked at it. If a fix ships, you will be
credited in the release notes unless you would rather not be.

## Security posture

Both apps are **offline by design**. This is worth stating precisely, because it rules out most
of the categories people worry about:

- **No network permission.** Neither `AndroidManifest.xml` requests `android.permission.INTERNET`.
  The apps cannot make network calls.
- **No telemetry, no analytics, no crash reporting.**
- **No audio retention.** Microphone samples are analysed in memory and discarded. Nothing is
  written to disk, and no recording is ever produced.
- **No accounts, no credentials, no secrets** of any kind in the codebase.

### Permissions and why they exist

| Permission | Why |
| --- | --- |
| `RECORD_AUDIO` | Detecting gunshots is the entire function of the app. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Keeps the timer running with the screen dimmed during a range session. Android only permits starting it while the app is in the foreground. |
| `VIBRATE` | Haptic start signal, which carries through hearing protection. |
| `WAKE_LOCK` | Prevents the watch sleeping mid-string. |
| `POST_NOTIFICATIONS` | The ongoing notification required by a foreground service. |

There is **no background listening**. The microphone service can only be started from the
foreground and stops when the session ends.

### Data that leaves the watch

Completed strings sync to the paired phone over the Wear OS Data Layer, which is local
Bluetooth/Wi-Fi transport between your own two devices. The payload is shot timings, drill name,
and optional scoring — no audio, no location, no identifiers.

### Automated scanning

Every push and pull request runs CodeQL (`security-extended`), gitleaks across full history, and
dependency review. A weekly scheduled run catches newly disclosed CVEs in pinned dependencies.

## Known limitations

- **Release APKs are debug-signed.** They are published for sideloading convenience. Debug
  signing keys are not secret and provide no authenticity guarantee — anyone can produce an APK
  signed the same way. Do not treat a debug-signed APK as proof of origin. Verify downloads
  against the `SHA256SUMS.txt` published with each release.
- The project has never been run on hardware, so no runtime security behaviour has been observed
  in practice.

## Out of scope

- The accuracy or reliability of shot detection. That is a correctness issue — use the
  [detection accuracy template](https://github.com/GPTmadeit/split-shot-timer/issues/new?template=detection_accuracy.yml).
- Vulnerabilities in Android, Wear OS, or Google Play Services themselves. Report those to Google.
