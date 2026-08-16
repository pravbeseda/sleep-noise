# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (`ru.pravbeseda.sleepnoise`) that synthesizes white and brown noise in real time for sleep, with a countdown timer. Single-module Gradle build (`:app`), Kotlin, minSdk 24 / target+compile SDK 36, JVM target 11.

An ongoing refactoring plan lives in `docs/plans/REFACTORING_PLAN.md` — check it before starting architectural work.

`README.md` is the outward-facing description of the same project. Build commands, requirements and
process live in both files: change one and the other goes stale silently, since nothing checks them
against each other. Keep them in step, and the plan too when a change closes or moves one of its
phases.

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

### Scope of one PR

One PR does one thing. Where the work maps onto `docs/plans/REFACTORING_PLAN.md`, that means one
deliverable of one phase. Refactoring and behaviour changes do not share a PR: a diff that moves
code *and* changes what it does cannot be reviewed, only trusted.

Files outside the stated scope stay untouched, however tempting. Something worth fixing that turns
up along the way goes into the PR description or an issue, not into the diff.

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
./gradlew lint                           # Android lint (fails on new warnings)
./gradlew detekt                         # Kotlin static analysis (baselined)
./gradlew spotlessCheck                  # ktlint formatting, changed files only
./gradlew spotlessApply                  # rewrite those files in place
./gradlew assembleRelease                # unsigned release APK
```

Single unit test:

```bash
./gradlew testDebugUnitTest --tests "ru.pravbeseda.sleepnoise.ExampleUnitTest.addition_isCorrect"
```

`app/google-services.json` is gitignored but **required** — the `com.google.gms.google-services` and Crashlytics plugins are applied unconditionally, so the build fails without it. A fresh clone has to download it from the Firebase console (project settings → your app). It stays out of git deliberately: this repository is public, and a committed key is picked up by secret scanners and stuck in the history for good.

Release APKs are renamed by an `applicationVariants` block in `app/build.gradle.kts` to `SleepNoise-<versionName>-<versionCode>-<buildType>.apk`. There is no `signingConfig` in the build script yet; release signing happens through Android Studio, and `.key/create_sign.sh` wraps `pepk.jar` to export the upload key for Play App Signing.

## Tests are mandatory

The project reached this point with no test covering its own code, which is exactly why the rule is
written down rather than assumed. It is deliberately not "always TDD": test-first pays for itself on
logic and fights you on Android plumbing, so the boundary is explicit.

**Pure logic is written test-first.** Pure logic is anything that does not import `android.*`: noise
sample generation, time formatting, state computation, settings migration. Order: a failing test,
the smallest implementation that passes it, then refactoring. New pure logic without a test in the
same commit is not finished work — do not describe it as done.

**Android plumbing** (Activity, View, Service, `SharedPreferences`) is not written test-first. If the
behaviour can be expressed through Robolectric, the test lands after the implementation in the same
PR. If it cannot, the PR description says which behaviour is uncovered and why. "Untested" is an
acceptable answer; "untested and unmentioned" is not.

**A bug fix starts with a test** that reproduces the defect and fails before the fix.

**Never weaken a test to get a green build.** Not by deleting it, not with `@Ignore`, not by
loosening an assertion. A test that seems wrong is a discussion in the PR, not a silent edit.

### Definition of done

```bash
./gradlew spotlessCheck detekt testDebugUnitTest lint
```

Green is the bar for calling work finished. Red means it is not finished, whatever else is true. If
a step could not be run at all, say which one and why rather than reporting around it.

The command grows as tooling lands (Kover next); when it does, update it here.

## CI

`.github/workflows/ci.yml` runs five independent jobs, and all five are **required status checks**: a red run blocks the merge button, and the branch has to be up to date with `main` first. None can be bypassed from the UI; `enforce_admins` is on.

Four of them — unit tests, lint, detekt and format — run on every PR and push to `main`. The fifth, **Guardrails**, runs on pull requests only, because it compares the PR against `github.event.pull_request.base.sha` and a push to `main` has nothing to compare against. That is why it became required by hand and only after it had been seen passing on a PR: a required check that has never reported blocks every merge in the repository, so making it required before the first green run would have locked the repo.

It enforces two rules this file states in prose, and only the half of each that a diff makes visible: that neither baseline grows (entry counts compared against the base commit), and that no `@Ignore` or `@Disabled` line is *added* under `app/src/test/` or `app/src/androidTest/`. Removing one passes — that direction is a test coming back. Deleting a test outright is not caught by either rule and stays a matter for review — a bare `@Test` count would fail the `ExampleUnitTest` removal the quality plan schedules, so that half needs its own design (issue #16).

The context names in the branch protection (`Unit tests`, `Lint`, `Detekt`, `Format`, `Guardrails`) are the job names, hardcoded on both sides. Renaming a job without renaming the context turns the check into a missing one and blocks every merge — change them together.

The four Gradle jobs put `app/google-services.json` in place before anything else, because the Firebase plugins are applied unconditionally and every Gradle task needs the file. Guardrails does not: it reads the diff and counts lines, so it needs no JDK, no Android SDK and no Gradle at all. The step lives in one place, `.github/actions/google-services`, since two copies of a fallback rule drift into two different rules.

Lint runs with `warningsAsErrors`, so **a new warning fails the build**. The 29 pre-existing findings are parked in `app/lint-baseline.xml`; clearing them is phase 6 of the plan. After fixing one, regenerate with `./gradlew updateLintBaseline` — and strip the informational entries it adds back in, or later runs complain about baseline entries that no longer match.

**Both baselines only ever shrink** — `app/lint-baseline.xml` and `config/detekt/baseline.xml` alike. Regenerating one to make a new warning disappear converts a
five-minute fix into permanent debt, and does it invisibly — the build goes green and the count goes
up. A new finding gets fixed. The baseline changes only in a PR whose subject is reducing it, and
that PR states the entry count before and after. Same rule for suppression: a new `@Suppress` or
`tools:ignore` carries a comment on the same line saying why.

Version-currency checks (`GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`, `OldTargetApi`) are informational on purpose: their messages contain the versions being compared, so they stop matching the baseline whenever a new release appears and would fail untouched code.

## Formatting

Spotless with ktlint 1.8.0 owns whitespace, import order and brace placement — `./gradlew
spotlessApply` settles any question about them, and a formatting argument in review means the
config is wrong, not the code.

The style is `intellij_idea`, set in `.editorconfig`, not ktlint's own `ktlint_official`. The two
differ mainly in wrapping, and `ktlint_official` moves every assigned expression onto its own
indented line and splits chained calls one call per line: a one-line edit to `app/build.gradle.kts`
came out as 67 added and 55 removed lines of pure wrapping, since the ratchet takes whole files.
`intellij_idea` is also what Android Studio produces, so the IDE and the check agree with no IDE
setup.

**Line length is 140, written in three places, and they have to stay equal:** `.editorconfig` (for
the IDE), `editorConfigOverride` in the root `build.gradle.kts` (Spotless reads the code style out
of `.editorconfig` but *not* the line length — without the override ktlint joined an already
wrapped class declaration into a 156-character line), and detekt's `MaxLineLength`.

**Detekt is what enforces it, not ktlint.** Spotless runs ktlint in format mode, and a line that is
too long is not something ktlint can fix, so it passes silently — a 483-character line went through
`spotlessCheck` untouched. Both facts were measured on this project, not assumed.

It runs with `ratchetFrom("origin/main")`: only files a branch changed are formatted or checked.
The whole tree was deliberately **not** reformatted — that commit would rewrite every blame line in
the project and teach nothing. The price is a hard dependency on the `origin/main` ref, so a
shallow or single-branch clone fails every spotless task outright instead of quietly checking
nothing, and the CI job checks out with `fetch-depth: 0`.

Do not widen the ratchet to `spotlessApply` the whole codebase in a PR about something else. A
formatting sweep is its own PR, if it ever happens at all.

## Static analysis: detekt

detekt 1.23.8, configured on the **root** project next to Spotless — not inside `:app`. Applying it
there would mean editing `app/build.gradle.kts`, and the Spotless ratchet then drags that whole
300-line file into ktlint's scope, so an unrelated wholesale reformat rides along in whatever PR
touches it. Detekt runs without type resolution and needs nothing from AGP but the paths.

Source paths are listed explicitly: `app/src/main/java`, `app/src/test/java`,
`app/src/androidTest/java`. The last one is not among detekt's defaults, and it is the source set
that already shipped a test asserting the wrong package name. Reports land in
`build/reports/detekt/` (root), not under `app/`.

Config is `config/detekt/detekt.yml` on top of `buildUponDefaultConfig`. It switches off two rules —
`WildcardImport` and `NewLineAtEndOfFile` — because ktlint owns them **and can fix them**, while two
tools with two opinions about one line is how a project ends up unable to satisfy either.
`MaxLineLength` is the opposite case and stays on at 140: ktlint cannot fix a long line, so it says
nothing about one. Anything else that is silenced belongs in that file with its reason, not in an
inline `@Suppress`.

`config/detekt/baseline.xml` holds the debt this landed on: **19 entries covering 36 findings** —
`MagicNumber` 26, `EmptyFunctionBlock` 6, `ImplicitDefaultLocale` 3, `TooManyFunctions` 1.
The two counts differ because a baseline entry is a signature, not a location,
so one entry absorbs every identical finding. That cuts both ways: a *new* magic number written into
an already-baselined expression is suppressed silently. Detekt is a floor, not a proof.

`ImplicitDefaultLocale` restates one of the Kotlin conventions below in executable form;
`PrintStackTrace` did the same until its two call sites were fixed and the entry dropped. The
remaining `MagicNumber` findings are concentrated in `media/` and are what phase 1 of the
refactoring plan turns into named constants.

The version is deliberate: detekt 2.0.0 is still alpha and is built against Kotlin 2.4 / AGP 9,
two minors and a major ahead of this project. Revisit when the project moves, not before.

## Kotlin conventions

Six rules, each of them a mistake this codebase has already made or is one edit away from making.

- **`String.format` always names its `Locale`, and for anything a user reads that `Locale` is
  `Locale.getDefault()`.** Leaving it out uses the default anyway, so "explicit" on its own changes
  no output — naming it makes the choice deliberate and reviewable instead of accidental.
  Locale-native digits are the intended behaviour, not the bug: on an Arabic device the timer reads
  `١٢:٣٤`, the same as the system clock, because someone who picked Arabic picked all of it.
  `Locale.ROOT` is for strings a machine parses, never for strings a person reads. The four
  `DefaultLocale` entries in the baseline come from three call sites in `timer/`; phase 6 makes
  them explicit without changing what they render.
- **No `e.printStackTrace()`.** Crashlytics is wired up; a stack trace printed to logcat in a release
  build goes nowhere at all. Use `Log` for the expected case, Crashlytics for the unexpected one.
  There is not one left in the project, and the detekt rule is no longer baselined, so a new one
  fails the build.
- **No `!!`.** There is currently not one in the project, which is worth keeping. `?.let`,
  `requireNotNull(x) { "why" }`, or an early return say the same thing without the crash.
- **Preference keys and theme/language values are constants, not literals at the call site.** The
  string `"dark"` appears throughout `MainActivity` as key, default and comparison at once; phase 5
  turns those into an enum. Do not add the twentieth occurrence in the meantime.
- **New dependencies go through `gradle/libs.versions.toml`,** with a line in the PR description
  saying why. The Compose stack is the cautionary tale: seven artifacts on the classpath, none used.
- **`versionCode`, `app/version.properties` and the versioning block of `app/build.gradle.kts` are
  release-PR territory.** Every other PR leaves them alone. See the versioning section for why the
  code is derived rather than written.

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

## Versioning and releasing

`versionName` lives in `app/version.properties` and is the only value bumped by hand.

`versionCode` is **derived** from `git rev-list --count HEAD` — never edit it. It is monotonic only while `main` (and later `release`) stay append-only, so no force-push or rebase on those branches.

A shallow clone undercounts, which would publish a code below what is already on Play, so any shallow checkout is rejected — not just `--depth 1`, since a depth of 20 would clear a numeric threshold while still producing a stale code. A count below the floor (`5`, the last hand-assigned value) is rejected too, as a history that is not the one the app ships from.

A missing or keyless `version.properties` is rejected on the same terms: the `versionName` falls back to `0.0.0`, and a release carrying that placeholder is one nobody can identify afterwards.

The rejection is a task, `verifyReleaseVersioning`, wired into `packageRelease` and `packageReleaseBundle` — the two tasks that turn a version into a publishable artifact. So `./gradlew build` and `./gradlew bundle` are covered even though neither names a release, while `lintRelease`, `testReleaseUnitTest` and any debug build still work on a shallow clone, falling back to the floor. **Any CI job that builds a release must check out with `fetch-depth: 0`.**

Release commits follow the message form `Release 1.0.3 (5)`.
