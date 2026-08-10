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

> **Changed in v0.3.0.** Before v0.3.0 neither app requested `INTERNET` and this document said
> so as a guarantee. In-app updates required that to change. The scope of the change is described
> precisely below rather than glossed over.

- **Network access exists, and is used for exactly one thing.** Checking GitHub for a newer
  release, and downloading its APK. The endpoints are fixed at compile time
  (`api.github.com` and the GitHub release CDN, for this repository only) — there is no
  configurable or user-supplied URL anywhere in the code.
- **Nothing is uploaded, ever.** A check is an unauthenticated `GET`. No account, no device
  identifier, no shot data, no audio, no settings. The only thing GitHub learns is that some
  IP asked for a public release list, which is true of anyone visiting the releases page.
- **It only runs when you ask.** There is no background poller, no check on launch, and no
  scheduled job. The request happens when you press **Check** on the Updates screen.
- **No telemetry, no analytics, no crash reporting.**
- **No audio retention.** Microphone samples are analysed in memory and discarded. Nothing is
  written to disk, and no recording is ever produced.
- **No accounts, no credentials, no secrets** of any kind in the codebase.

### Updates, and what they can and cannot do

- **Nothing installs silently.** The app downloads an APK and hands it to the platform package
  installer, which shows the system's own confirmation prompt. `REQUEST_INSTALL_PACKAGES` allows
  an app to *ask*; it never allows it to install unattended.
- **Android enforces the signature.** An update only installs if it is signed with the same key
  as the copy you already have, so a downloaded file cannot replace this app with something else.
- **But the signing key is public** — see Known limitations. It is committed to this repository
  so that updates work at all. Treat the key as an update-continuity mechanism, not as proof of
  origin, and verify downloads against `SHA256SUMS.txt`.
- **Downloads are HTTPS** to GitHub. The APK is written to app-private cache and exposed to the
  installer through a `FileProvider` one-shot read grant, not to world-readable storage.

### Permissions and why they exist

| Permission | Why |
| --- | --- |
| `RECORD_AUDIO` | Detecting gunshots is the entire function of the app. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Keeps the timer running with the screen dimmed during a range session. Android only permits starting it while the app is in the foreground. |
| `VIBRATE` | Haptic start signal, which carries through hearing protection. |
| `WAKE_LOCK` | Prevents the watch sleeping mid-string. |
| `POST_NOTIFICATIONS` | The ongoing notification required by a foreground service. |
| `INTERNET` | Checking GitHub for a newer release, on request only. |
| `ACCESS_NETWORK_STATE` | Reporting "no internet connection" instead of a timeout. |
| `REQUEST_INSTALL_PACKAGES` | Handing a downloaded APK to the system installer, which always asks you to confirm. |

There is **no background listening**. The microphone service can only be started from the
foreground and stops when the session ends.

### Data that leaves the device

Two paths, both narrow:

1. **Watch to phone.** Completed strings sync over the Wear OS Data Layer — local
   Bluetooth/Wi-Fi transport between your own two devices. The payload is shot timings, drill
   name and optional scoring. No audio, no location, no identifiers.
2. **Outbound to GitHub.** Only when you press Check: an unauthenticated `GET` for the public
   release list, and if you choose to update, a download of the APK. Nothing is sent.

There is no third path. No shot data, no audio and no settings ever leave your devices.

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
