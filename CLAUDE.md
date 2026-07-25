# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (`ru.pravbeseda.sleepnoise`) that synthesizes white and brown noise in real time for sleep, with a countdown timer. Single-module Gradle build (`:app`), Kotlin, minSdk 24 / target+compile SDK 36, JVM target 11.

An ongoing refactoring plan lives in `docs/plans/REFACTORING_PLAN.md` — check it before starting architectural work.

## Contributing workflow

`main` is protected: **no direct commits or pushes**, enforced on GitHub and by local hooks. Always branch from an up-to-date `main`, then open a PR.

```bash
git fetch origin && git checkout -b <type>/<slug> origin/main
```

### After a fresh clone

Both settings below live in `.git/config`, which is never cloned. Run them once per clone — git applies neither on its own:

```bash
git config core.hooksPath .githooks    # activate the versioned hooks
git config remote.origin.prune true    # drop stale remote-tracking refs on fetch
```

Two hooks back this up: `.githooks/pre-commit` rejects a commit made while `main` or `release` is checked out, and `.githooks/pre-push` rejects a push to either — on `origin` only, so forks and scratch remotes are unaffected. Both accept `--no-verify` as a deliberate bypass, and GitHub enforces the same rule server-side.

An in-progress merge is exempt from the commit hook, so resolving a conflict on `main` still works.

**Delete the branch once its PR is merged.** GitHub removes the remote branch automatically (`delete_branch_on_merge`), so only the local copy is left behind:

```bash
git checkout main && git pull && git branch -d <branch>
```

With `remote.origin.prune` set as above, `git fetch` clears the stale remote-tracking ref too. Never reuse a merged branch for new work — branch again from an up-to-date `main`.

## Commands

```bash
./gradlew assembleDebug                  # build debug APK
./gradlew installDebug                   # build + install on connected device
./gradlew testDebugUnitTest              # JVM unit tests
./gradlew connectedAndroidTest           # instrumented tests (needs device/emulator)
./gradlew lint                           # Android lint
./gradlew assembleRelease                # unsigned release APK
```

Single unit test:

```bash
./gradlew testDebugUnitTest --tests "ru.pravbeseda.sleepnoise.ExampleUnitTest.addition_isCorrect"
```

`app/google-services.json` is gitignored but **required** — the `com.google.gms.google-services` and Crashlytics plugins are applied unconditionally, so the build fails without it. A fresh clone has to download it from the Firebase console (project settings → your app). It stays out of git deliberately: this repository is public, and a committed key is picked up by secret scanners and stuck in the history for good.

Release APKs are renamed by an `applicationVariants` block in `app/build.gradle.kts` to `SleepNoise-<versionName>(<versionCode>)-<buildType>.apk`. There is no `signingConfig` in the build script; release signing happens through Android Studio, and `.key/create_sign.sh` wraps `pepk.jar` to export the upload key for Play App Signing.

## Architecture

### Audio: two independent generators, mixed by the OS

`media/BaseNoiseGenerator` owns one `AudioTrack` (44.1 kHz, mono, PCM 16-bit, `MODE_STREAM`) plus a dedicated `Thread.MAX_PRIORITY` thread that loops calling the subclass's `generateNoiseData(bufferSize)` and writing into the track. Subclasses supply only the sample math: `WhiteNoiseGenerator` (uniform random) and `BrownNoiseGenerator` (integrates white noise via `lastOut + 0.02 * white`, clamped).

`MainActivity` holds one instance of each and **always starts and stops both together**. There is no software mixer — the two `AudioTrack`s play simultaneously and the platform mixes them. The volume sliders call `setVolume` on each track independently, so "white noise only" is really "brown noise at volume 0". Keep this in mind when changing playback: muting is not stopping.

Lifecycle guards are `AtomicBoolean` (`isPlaying`, `isStopped`) with `stopNoiseInternal()` synchronized, because the writer thread and the UI thread both touch the track. Playback runs entirely in the Activity — no foreground service, no media session, and `onDestroy` stops the noise, so audio does not survive the app being killed or (currently) backgrounded for long.

### Timer

Three pieces in `timer/`:
- `TimerView` — custom `LinearLayout` inflating `timer_view.xml`; owns the seekbar and the time label. Seekbar progress is in 30-minute units (`progress * 30` minutes), and the view hides the seekbar while playing.
- `TimerPreferences` — its own `SharedPreferences` file (`timer_prefs`), separate from the app-wide one.
- `TimerController` — wraps `CountDownTimer` with `onTick(formattedTime)` / `onTime()` callbacks. `MainActivity` wires `onTick` to `timerView.showCountdown` and `onTime` to `stopPlayback()`.

### Preferences

Two distinct stores. `APP_PREFS` ("AppPreferences", constants at the top of `MainActivity.kt`) holds `whiteNoiseVolume`, `brownNoiseVolume`, `selectedTheme`, `selectedLanguage`. `timer_prefs` holds only the timer value. Don't consolidate one into the other without checking both readers.

### Theme

Three explicit themes (`system` / `light` / `dark`, default **dark**) rather than relying on system night mode alone. `applyTheme` runs **before** `super.onCreate` and sets both `AppCompatDelegate.setDefaultNightMode` and an explicit `setTheme(...)`; changing the theme calls `recreate()`. Edge-to-edge and the status-bar appearance flag are set in `onCreate` from the same stored value.

### Localization

Supported: en (default), ar, de, es, ru, uk. The mechanism is non-obvious:

- Each `values-XX/strings.xml` defines `<string name="lang">XX</string>`. `getString(R.string.lang)` is how the code asks "which locale is actually active" — used to preselect the language dialog and to decide whether to append "(Language)" to the menu title.
- The chosen code is stored in `APP_PREFS`/`selectedLanguage` and applied with `AppCompatDelegate.setApplicationLocales`.

To add a language: create `values-XX/strings.xml` including the `lang` key, add a flag drawable, and add a `Language(...)` entry to the array in `MainActivity.languageSelection()`. The array also carries an `engName` used by `LanguagesArrayAdapter`; RTL is handled via `BidiFormatter` and `android:supportsRtl`/`layoutDirection="locale"` in the manifest.

## UI is Views, not Compose

The build enables Compose (`buildFeatures.compose`, Compose BOM, material3, activity-compose), but **no Compose is used anywhere**. The entire UI is XML layouts with AppCompat: `activity_main.xml`, `timer_view.xml`, `dialog_credits.xml`, `item_lang.xml`, plus `menu/` for the action bar and theme popup. Follow the existing View-based approach unless deliberately migrating; don't assume Compose because the dependencies are present.

## Releasing

Bump `versionCode` and `versionName` in `app/build.gradle.kts`. Release commits follow the message form `Release 1.0.3 (5)`.
