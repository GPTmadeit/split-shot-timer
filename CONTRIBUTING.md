# Contributing

Thanks for considering it. This is a small project with an unusual constraint: **the maintainer
has never run it against live fire.** That makes some contributions far more valuable than
others.

## The most useful thing you can contribute

**Real detection data.** Every detector constant in this project is a reasoned estimate. If you
take SPLIT to a range and it misses shots, double-counts reverb, or picks up the bay next to
you, please open a
[detection accuracy report](https://github.com/GPTmadeit/split-shot-timer/issues/new?template=detection_accuracy.yml).

Include your firearm and calibre, whether it was suppressed, indoor or outdoor, and your timer
settings. A report saying "6 fired, 4 detected, Glock 34 9mm unsuppressed, indoor, sensitivity
−22, clip gate on" is worth more than a hundred lines of speculative code.

Please do not record audio at a range where other people have not agreed to being recorded.

## Getting set up

You need **JDK 17** and the **Android SDK with platform 36**. Do not install Gradle — use the
wrapper.

```bash
git clone https://github.com/GPTmadeit/split-shot-timer.git
```

```bash
cd split-shot-timer && ./gradlew build
```

That compiles both apps for debug and release, runs the unit tests, and runs Android Lint. It
should pass on a clean checkout. If it does not, that is a bug — please report it.

## Before you open a pull request

Run the same things CI runs:

```bash
./gradlew build
```

### Formatting

Kotlin formatting is checked with [ktlint](https://pinterest.github.io/ktlint/) 1.8.0, using
the **`intellij_idea`** ruleset configured in `.editorconfig`. That ruleset is chosen so CI
agrees with what Android Studio produces on save — `ktlint_official` does not, and would fight
your IDE.

Download it once:

```bash
curl -sSfL -o ktlint.jar https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/1.8.0/ktlint-cli-1.8.0-all.jar
```

Check:

```bash
java -jar ktlint.jar "**/src/**/*.kt" "**/*.gradle.kts"
```

Fix automatically:

```bash
java -jar ktlint.jar --format "**/src/**/*.kt" "**/*.gradle.kts"
```

Do not pass `--code-style` on the command line — it was removed in ktlint 1.8 and silently
causes the tool to lint nothing while still exiting non-zero. The style comes from
`.editorconfig`.

### Tests

`:core` is pure Kotlin and unit tested. If you change anything in it, add or update tests:

```bash
./gradlew :core:testDebugUnitTest
```

The audio, sensor and UI layers have no automated tests. If you change those, say so honestly in
the PR — an unticked verification box is more useful than a guess.

## Dependency versions — please read

Versions in `gradle/libs.versions.toml` are pinned deliberately. They are the newest stable
releases whose AAR metadata still permits `compileSdk 36` / AGP 8.13.2; anything newer requires
`compileSdk 37`, which is not published in the SDK manager yet.

`material3` is pinned to `1.5.0-alpha18` on purpose: the Material 3 Expressive API is compiled
`internal` in 1.4.0 stable and unreachable there.

If you need to bump something, verify it by reading `aar-metadata.properties` out of the
artifact rather than assuming. A PR that bumps a version and breaks the build on a machine
without platform 37 will not be merged.

## Style and scope

- **Comments explain why, not what.** The codebase is dense with reasoning about acoustics and
  timing because those decisions are not obvious from the code. Match that.
- **Do not claim things you have not verified.** This applies to code comments, docs, and PR
  descriptions. "Untested on hardware" is a perfectly good statement.
- **Keep the timing path free of blocking calls.** Nothing in `TimerEngine` or `ShotDetector`
  may await I/O, and nothing may wait on the phone.
- Small, focused pull requests. If you are planning something large, open an issue first.

## Commit messages

Explain the problem, not just the change. A good message here reads like:

```
Fix two launch-crashing manifest references

The wear manifest used relative component names. Those resolve against the
namespace com.carlb.split, which is shared with the phone app, but the watch
code lives under com.carlb.split.wear -- so both resolved to classes that do
not exist. The app packaged fine and would have thrown ClassNotFoundException
on launch.
```

## Reporting bugs

Use the [issue templates](https://github.com/GPTmadeit/split-shot-timer/issues/new/choose).
Detection problems have their own template because they need different information.

## Security

Do not open a public issue. See [SECURITY.md](SECURITY.md).

## Code of Conduct

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence

Contributions are licensed under the [MIT Licence](LICENSE).
