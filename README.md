# Sleep Noise

[![CI](https://github.com/pravbeseda/sleep-noise/actions/workflows/ci.yml/badge.svg)](https://github.com/pravbeseda/sleep-noise/actions/workflows/ci.yml)

Android app that **synthesizes** white and brown noise in real time to help you fall asleep,
with a countdown timer that stops playback on its own.

Nothing is streamed and nothing is bundled: the samples are generated on the device, so the app
has no audio assets, needs no network access, and never runs out of loop to repeat.

<a href="https://play.google.com/store/apps/details?id=ru.pravbeseda.sleepnoise">Get it on Google Play</a>

| Dark theme, idle | Light theme, playing |
|---|---|
| <img src="graph/Screenshot_20250106_182502.png" width="280" alt="Dark theme, playback stopped"> | <img src="graph/Screenshot_20250106_182551.png" width="280" alt="Light theme, playing with countdown"> |

## Features

- **Two independent noise channels** — white and brown, each with its own volume slider. Set one
  to 0 % to hear only the other.
- **Sleep timer** — up to several hours in 30-minute steps; playback stops when it runs out.
- **Three themes** — system, light, dark (dark by default).
- **Six languages** — English, Arabic, German, Spanish, Russian, Ukrainian, with full RTL support.
- **No ads, no accounts, no audio files.**

Volumes, theme, language, and the last timer value are remembered between launches.

## Known limitations

Being honest about the current state — all of these are tracked in
[`docs/plans/REFACTORING_PLAN.md`](docs/plans/REFACTORING_PLAN.md):

- Playback lives in the Activity, so it does not survive the app being killed, and changing the
  theme or language restarts it (phase 3 adds a foreground service).
- No audio focus handling: an incoming call plays on top of the noise, and unplugging headphones
  does not stop it (phase 3).
- Each channel drives its own `AudioTrack`, mixed by the platform rather than in software
  (phase 2).

## Building from source

Requires JDK 17 and the Android SDK (compileSdk 36, minSdk 24).

```bash
git clone git@github.com:pravbeseda/sleep-noise.git
cd sleep-noise
./gradlew assembleDebug
```

**`app/google-services.json` is required and is not in the repository.** The
`com.google.gms.google-services` and Crashlytics plugins are applied unconditionally, so the build
fails without it — including unit tests, which never touch Firebase at runtime. Download it from
the Firebase console (project settings → your app) into `app/`.

It stays out of git deliberately: this repository is public, and a committed key is picked up by
secret scanners within hours and stuck in the history for good.

### Common commands

```bash
./gradlew assembleDebug          # debug APK
./gradlew installDebug           # build + install on a connected device
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (needs a device or emulator)
./gradlew lint                   # Android lint — fails on new warnings
./gradlew assembleRelease        # unsigned release APK
```

## Project layout

```
app/src/main/java/ru/pravbeseda/sleepnoise/
├── MainActivity.kt          # UI wiring, theme and language selection, playback control
├── CreditsDialogFragment.kt
├── media/                   # BaseNoiseGenerator + White/Brown implementations
├── timer/                   # TimerView, TimerController, TimerPreferences
├── models/ · adapters/
```

The UI is XML layouts with AppCompat Views throughout. The Compose dependencies present in the
build are unused leftovers, scheduled for removal — do not treat them as the intended direction.

Architecture notes, including the parts that are non-obvious (how the active locale is detected,
why the theme is applied before `super.onCreate`, how versioning works), live in
[`CLAUDE.md`](CLAUDE.md).

## Contributing

`main` is protected — no direct commits or pushes. Branch from an up-to-date `main` and open a PR:

```bash
git fetch origin && git checkout -b <type>/<slug> origin/main
```

After a fresh clone, run these once — they live in `.git/config`, which is never cloned:

```bash
git config core.hooksPath .githooks    # activate the versioned hooks
git config remote.origin.prune true    # drop stale remote-tracking refs on fetch
```

The hooks refuse commits and pushes on `main` and `release`; GitHub enforces the same rule
server-side. Delete your branch once its PR is merged, and never reuse it — `versionCode` is
derived from the commit count, so `main` has to stay append-only.

### Adding a language

Translations are the most welcome contribution — the app even asks users for them.

1. Create `app/src/main/res/values-XX/strings.xml`, including
   `<string name="lang">XX</string>` (the code reads this back to detect the active locale).
2. Add a flag drawable.
3. Add a `Language(...)` entry to the array in `MainActivity.languageSelection()`.

## Versioning and releasing

`versionName` lives in `app/version.properties` and is the only value bumped by hand.
`versionCode` is derived from `git rev-list --count HEAD` and must never be edited — a release
build whose version cannot be derived from the repository is rejected outright by the
`verifyReleaseVersioning` Gradle task. Any CI job that builds a release must check out with
`fetch-depth: 0`.

Release commits follow the form `Release 1.0.3 (5)`.

## Credits

- Development — Alexander Ivanov
- Logo — Nina Ivanova

[Terms & Conditions](sleep-noise-policy.html) · contact: kalugaman@gmail.com

## License

No license file — all rights reserved. The source is published for transparency and for
translation contributions, not for redistribution or derivative apps.
