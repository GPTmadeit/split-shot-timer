# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

While the version is `0.x`, the public surface — the Data Layer wire format in particular — may
change in any minor release.

## [Unreleased]

## [0.2.0] — 2026-08-09

Repository and quality release. No functional change to detection, timing, or the apps'
behaviour; the reformat in this release is whitespace-only and was verified against the test
suite.

### Added

- **42 unit tests** covering `:core`: shot string statistics (splits, σ, standards grading),
  USPSA scoring and hit factor, the watch↔phone wire contract round-trip, timer delay modes, and
  drill library invariants.
- **Continuous integration** — build, unit tests, Android Lint, Kotlin formatting, and a
  structural check on the browser prototype, on every push and pull request.
- **Security automation** — CodeQL (`security-extended`), gitleaks across full history,
  dependency review on pull requests, and a weekly scheduled scan.
- **Release automation** — tagging `v*` verifies, builds, and attaches APKs plus `SHA256SUMS.txt`.
- **Dependabot** for Gradle and GitHub Actions, with grouped updates.
- **Documentation**: `ARCHITECTURE.md`, `CONTRIBUTING.md`, `SECURITY.md`, `SUPPORT.md`,
  `CODE_OF_CONDUCT.md`, and this changelog. The README was rewritten with requirements, quick
  start, usage, configuration, troubleshooting, and an FAQ.
- **Issue templates** for bugs, feature requests, and a dedicated detection-accuracy report;
  plus a pull request template.
- **MIT licence.**
- **Branding**: banner and social preview assets.
- `.gitattributes` normalising line endings to LF, and `.editorconfig`.
- `SHA256SUMS.txt` published alongside release artifacts.

### Changed

- Kotlin sources formatted with ktlint 1.8.0 (`intellij_idea` ruleset). Whitespace only —
  `./gradlew build` and all 42 tests pass unchanged.
- `.gitignore` hardened to cover signing material (`*.jks`, `*.keystore`, `keystore.properties`),
  `.env` files, service-account JSON, and additional build output.

### Fixed

- Two unit test expectations that were wrong when first written: a hand-computed σ used an
  incorrect variance, and a drill invariant asserted that goal times must sum inside par when
  `goalFirst` and `goalSplit` are independent ceilings. Both were test bugs; the implementation
  was correct.

### Security

- Full git history scanned for credentials, tokens, keys, and personal data across every commit
  and every blob. Clean — nothing found, nothing needed rewriting.
- Documented the offline-by-design posture: neither app requests `INTERNET`, and no audio is
  retained.

## [0.1.0] — 2026-08-08

First pre-release.

### Added

- **Wear OS timer** — the instrument. Owns the microphone, the start tone, and the clock.
  - Onset detection timed from `AudioRecord.getTimestamp()`, giving one-sample resolution
    (20.8 µs at 48 kHz) rather than one-buffer resolution.
  - Start instant taken from `AudioTrack.getTimestamp()` rewound to frame 0, so first-shot time
    is a difference of two HAL timestamps.
  - `AudioSource.UNPROCESSED` where supported, falling back to `VOICE_RECOGNITION`, to stop
    automatic gain control from corrupting splits after the first round.
  - Clip-run gating for neighbour rejection, and an optional accelerometer recoil gate (off by
    default).
  - Nine drills with enforced shot counts and par times.
  - Foreground service holding the microphone through ambient mode.
  - Wear Compose Material 3 UI; the bezel renders the shot string as a time tape.
- **Phone companion** — a durable replica.
  - `WearableListenerService` receiving both transports, so strings land even if the app was
    never opened.
  - Live mirror, session log, draw-time trend, split bars, USPSA hit factor scoring.
  - Material 3 Expressive with physics-based motion.
- **Shared `:core` module** — models and the watch↔phone wire contract.
- **Browser prototype** at <https://gptmadeit.github.io/split-shot-timer/>.

### Known limitations

- Never run on hardware; no live fire recorded through it.
- Detector constants (clip run, blanking, recoil window and threshold) are estimates.
- Release APKs are debug-signed.
- `compileSdk` pinned at 36 with dependencies held back to match, because platform 37 is not yet
  published.

[Unreleased]: https://github.com/GPTmadeit/split-shot-timer/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/GPTmadeit/split-shot-timer/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/GPTmadeit/split-shot-timer/releases/tag/v0.1.0
