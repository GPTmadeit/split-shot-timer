# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

While the version is `0.x`, the public surface — the Data Layer wire format in particular — may
change in any minor release.

## [Unreleased]

## [0.4.0] - 2026-08-10

Both apps now look like they belong on their platform instead of wearing a
custom skin.

### Changed

- **The watch is rebuilt on native Wear OS 6 structure.** `AppScaffold` supplies
  `TimeText` — the clock curved along the top bezel. The primary action is an
  `EdgeButton`, the control that hugs the bottom curve of a round display and is
  the single most recognisable Wear OS 6 element. Lists are
  `TransformingLazyColumn`, so rows scale and fade toward the curved edges the
  way every system list does. Settings uses the platform `Slider` and
  `SwitchButton` rather than rows of buttons imitating them, which also brings
  proper touch targets and rotary-crown behaviour.
- **The watch follows the active watch face.** `dynamicColorScheme` derives the
  chrome palette from whatever face is in use, with the previous gunmetal scheme
  as fallback where no dynamic source exists.
- **The phone is stock Android.** Material You dynamic colour on Android 12+, so
  the app takes its palette from the wallpaper; a real `TopAppBar` that responds
  to scroll; `Scaffold` owning the window insets edge to edge. The hand-built
  schemes remain the fallback for Android 10 and 11.
- The watch's menu button and drill label merged into one compact chip. Two
  stacked elements collided with the readout on a 227 dp round face, and the
  drill is one tap away inside the menu.

### Fixed

- The edge button no longer covers the readout, and its label no longer inherits
  a low-contrast `onPrimary` from the dynamic palette while sitting on the fixed
  instrument orange.

### Note on colour

Chrome follows the system; the **instrument does not**. Shot ticks, the running
clock, the standby arc and the start button keep fixed high-contrast colours,
because a wallpaper- or watch-face-derived pair can easily be low contrast and
these have to stay readable in sunlight with the display dimmed.

## [0.3.0] — 2026-08-10

Both apps can now update themselves from this repository's releases, and both gained a menu.

> [!IMPORTANT]
> **This release adds network access.** Until now neither app requested `INTERNET`, and
> `SECURITY.md` stated that as a guarantee. In-app updates required changing it. The scope is
> narrow — an unauthenticated `GET` to this repository's public release list, only when you press
> **Check**, with nothing uploaded — and [SECURITY.md](SECURITY.md) now describes exactly what
> changed rather than glossing over it.

### Added

- **In-app updates, on watch and phone.** Menu → Updates → Check. If a newer release exists you
  can download and install it from the device. Each app fetches its own APK. Android's package
  installer shows its own confirmation; nothing installs silently, and there is no background
  poller or check-on-launch.
- **A menu button on both apps** — three dots, top of the face on the watch and top-right on the
  phone — housing Drill, Settings and Updates, with room for more. It carries a dot when an
  update is waiting.
- **18 more unit tests** (60 total) covering version ordering and release selection: that `0.10.0`
  beats `0.9.0` rather than losing a string comparison, that a pre-release sorts below the same
  release, that each app is offered its own asset, that drafts are skipped, and that a failed
  lookup reports a failure rather than a reassuring "up to date".

### Fixed

- **Settings was unreachable.** The route existed in the navigation graph but nothing ever
  navigated to it, so every setting — sensitivity, the gates, start signal, auto-repeat — was
  inaccessible from a running app. The menu fixes this.

### Changed

- The Updates screen distinguishes **"Not checked yet"** from **"Up to date"**. Defaulting to
  "up to date" before any check has run states something the app has not verified.

### Security

- `INTERNET`, `ACCESS_NETWORK_STATE` and `REQUEST_INSTALL_PACKAGES` added, each documented in
  `SECURITY.md` with the reason. Endpoints are fixed at compile time; there is no
  user-supplied or configurable URL in the codebase.
- Downloaded APKs go to app-private cache and reach the installer through a `FileProvider`
  one-shot read grant, never world-readable storage.

## [0.2.1] — 2026-08-09

The watch app did not open. It does now, and releases can finally install over
each other. Both problems were verified on a Wear OS 6 emulator rather than
reasoned about.

> [!IMPORTANT]
> **Upgrading from v0.1.0 or v0.2.0 requires uninstalling first.** Those builds
> were signed with a throwaway key, so Android rejects this one with
> `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall, install v0.2.1, and every
> future update installs in place. This is a one-time break.

### Fixed

- **The watch app crashed instantly on launch.** `RecoilGate` registered the
  accelerometer at `SENSOR_DELAY_FASTEST` (0 µs). Since Android 12 any rate
  above 200 Hz requires `HIGH_SAMPLING_RATE_SENSORS`, which was not declared,
  so `registerListener` threw `SecurityException` out of the foreground
  service. `START_STICKY` then restarted the service straight back into the
  same crash, so the app appeared to open and vanish. The permission is now
  declared, the service is `START_NOT_STICKY`, and the sensor call cannot throw.
- **Releases could not be installed as updates.** Every CI run generated a fresh
  `~/.android/debug.keystore`, so each release was signed by a different key and
  Android refused the upgrade. Builds now use a committed, deliberately public
  sideload key, so all future artifacts share one signer.
- The recoil gate started the accelerometer even though it ships **off**. An
  optional, disabled feature could take down the whole timer. It is now started
  only when enabled, degrades to a slower sampling rate if the fast rate is
  refused, and reports itself unavailable rather than failing.
- `TimerService.startSession()` now enters the foreground before anything that
  can fail, so a later error cannot get the process killed for failing to post
  its notification in time.

### Added

- **Emulator smoke test in CI** — installs both APKs on a real emulator,
  launches them, and fails if the process dies or logs a fatal exception. Every
  static check passed while the app was crash-looping; only running it caught
  this.
- **Signing-key stability check in CI** — fails the build if the APK signer
  drifts from the committed key, which is what silently broke updates.
- `signing/split-debug.keystore`, with a narrow `.gitignore` exception. All
  other keystores stay ignored.

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

[Unreleased]: https://github.com/GPTmadeit/split-shot-timer/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/GPTmadeit/split-shot-timer/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/GPTmadeit/split-shot-timer/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/GPTmadeit/split-shot-timer/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/GPTmadeit/split-shot-timer/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/GPTmadeit/split-shot-timer/releases/tag/v0.1.0
