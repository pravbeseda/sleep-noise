# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (`ru.pravbeseda.sleepnoise`) that synthesizes white and brown noise in real time for sleep, with a countdown timer. Single-module Gradle build (`:app`), Kotlin, minSdk 26 / target+compile SDK 36, JVM target 11.

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
./gradlew koverVerifyDebug               # unit tests + the coverage floor
./gradlew koverLogDebug                  # print the coverage figure without enforcing it
./gradlew connectedAndroidTest           # instrumented tests (needs device/emulator)
./gradlew lint                           # Android lint (fails on new warnings)
./gradlew detekt                         # Kotlin static analysis (baselined)
./gradlew spotlessCheck                  # ktlint formatting, changed files only
./gradlew spotlessApply                  # rewrite those files in place
./gradlew assembleRelease                # signed release APK (needs the SN_* credentials)
```

Single unit test:

```bash
./gradlew testDebugUnitTest --tests "ru.pravbeseda.sleepnoise.media.BrownNoiseTest.resetReturnsTheIntegratorToZero"
```

`app/google-services.json` is gitignored but **required** — the `com.google.gms.google-services` and Crashlytics plugins are applied unconditionally, so the build fails without it. A fresh clone has to download it from the Firebase console (project settings → your app). It stays out of git deliberately: this repository is public, and a committed key is picked up by secret scanners and stuck in the history for good.

Release APKs are renamed by an `applicationVariants` block in `app/build.gradle.kts` to `SleepNoise-<versionName>-<versionCode>-<buildType>.apk`.

The `release` build type is signed by a `signingConfig` reading four project properties — `SN_KEY_ALIAS`, `SN_KEY_PASSWORD`, `SN_STORE_PASSWORD` and `SN_STORE_FILE` — so `assembleRelease` **fails without them** rather than producing an unsigned APK, which is the point: a release that quietly comes out unsigned is worse than one that stops. `SN_STORE_FILE` defaults to `../.key/Drevo.Keystore`, the maintainer's gitignored copy; the default exists because `file(null)` throws at configuration time and would take down every Gradle task in the project, tests included. The other three have no default. CI passes all four as `ORG_GRADLE_PROJECT_SN_*` environment variables, which Gradle maps onto properties of the same name.

`Drevo.Keystore` is the *upload* key — `.key/create_sign.sh` wraps `pepk.jar`, which exists only to hand a key to Play App Signing, and Google re-signs what it distributes.

## Tests are mandatory

The project reached this point with no test covering its own code, which is exactly why the rule is
written down rather than assumed. It is deliberately not "always TDD": test-first pays for itself on
logic and fights you on Android plumbing, so the boundary is explicit.

**Pure logic is written test-first.** Pure logic is anything that does not import `android.*`: noise
sample generation, time formatting, state computation, settings migration. Order: a failing test,
the smallest implementation that passes it, then refactoring. New pure logic without a test in the
same commit is not finished work — do not describe it as done.

The two roots the coverage floor names are checked rather than trusted: `AndroidFreeSourcesTest`
walks `media/` minus `NoiseEngine.kt`, plus `timer/SleepTimer.kt`, and fails naming the file and the
import line when one of them imports `android.*` or `androidx.*`. The rest of the rule above is still
discipline — pure logic outside those two roots is scanned by nothing, `models/Language` included. A
separate `java-library` module would grant the whole rule at compile time; single-module, this much
of it is asserted instead (issue #32).

**Android plumbing** (Activity, View, Service, `SharedPreferences`) is not written test-first. If the
behaviour can be expressed as an instrumented test on an emulator, the test lands after the
implementation in the same PR. If it cannot, the PR description says which behaviour is uncovered and
why. "Untested" is an acceptable answer; "untested and unmentioned" is not.

**Robolectric and MockK are ruled out** — plumbing is covered on a real emulator instead, and pure
logic needs neither. The decision, with what replaces them, is in `docs/plans/REFACTORING_PLAN.md`
under "Testing strategy"; adding either dependency means changing that section first.

**A bug fix starts with a test** that reproduces the defect and fails before the fix.

**Never weaken a test to get a green build.** Not by deleting it, not with `@Ignore`, not by
loosening an assertion. A test that seems wrong is a discussion in the PR, not a silent edit.

**Coverage has a floor: 80 % of lines**, set in `app/build.gradle.kts` and measured on the debug
variant over one named set of classes — `media/` minus `NoiseEngine`, plus `timer/SleepTimer`. The
denominator is cut down on purpose: an Activity or a Service is a line no JVM test can execute, so
counting them makes the figure report how much Android plumbing the app has rather than how well its
logic is tested. It is the logic that is measured, not everything a JVM test could technically
reach — `models/Language` imports nothing from `android.*` either, and is a data holder with no
behaviour to cover. The bound rises once the figure has settled above it, and is never lowered to
turn a red run green — a floor that moves down is not a floor.

That set is written twice and not identically: Kover names classes by glob, the test names files.
Where they differ the test is the stricter one — it reads every `.kt` under `media/` except
`NoiseEngine.kt`, while the filter excludes the whole `NoiseEngine*` glob — except at the edges a
glob reaches and a file name does not: a `SleepTimerFormatter` class, or a second class declared
inside `NoiseEngine.kt`, counts towards the floor while going unscanned. Change one and look at the
other; a package added to the filter alone keeps the floor honest while quietly dropping the
Android-free premise that justifies it.

### Definition of done

```bash
./gradlew spotlessCheck detekt testDebugUnitTest koverVerifyDebug lint
```

Green is the bar for calling work finished. Red means it is not finished, whatever else is true. If
a step could not be run at all, say which one and why rather than reporting around it.

`koverVerifyDebug` runs `testDebugUnitTest` itself, so the tests execute once however you reach
them; both are named so that a reader can see the tests run at all.

Five tasks, four of the seven required checks: coverage has no job of its own and rides in `Unit
tests`. The other three are deliberately not on that line. Guardrails compares the PR against its
base commit, so there is nothing local to run at all. `Instrumented tests (API 26)` and
`(API 36)` are the remaining two, and unlike Guardrails they *can* be run here —
`connectedAndroidTest`, in the Commands section — but they need a device or an emulator, and a
pre-push line that does not run without one is a line that gets skipped. So a green local run
means the work is done as far as a machine with no device can tell; it does not mean the PR is
mergeable. See the CI section.

New tooling joins this line as it lands; Kover was the most recent.

## CI

`.github/workflows/ci.yml` runs seven jobs. Six of them report the **seven required status checks**: a red run blocks the merge button, and the branch has to be up to date with `main` first. Six jobs and seven checks because **instrumented tests** is a matrix over API 26 and API 36 and reports one context per leg. None can be bypassed from the UI; `enforce_admins` is on. The seventh job, **alpha**, delivers and is deliberately not required — see the delivery section below.

Five of the six — unit tests, instrumented tests, lint, detekt and format — are triggered on every PR and push to `main`, but each one first asks `.github/actions/decide-work` whether it has anything to do. The sixth, **Guardrails**, runs on pull requests only, because it compares the PR against `github.event.pull_request.base.sha` and a push to `main` has nothing to compare against. That is why it became required by hand and only after it had been seen passing on a PR: a required check that has never reported blocks every merge in the repository, so making it required before the first green run would have locked the repo. The two instrumented contexts were added the same way and for the same reason: by hand, on PR #33, once both had been seen green on it.

It enforces two rules this file states in prose, and only the half of each that a diff makes visible: that neither baseline grows (entry counts compared against the base commit), and that no `@Ignore` or `@Disabled` line is *added* under `app/src/test/` or `app/src/androidTest/`. Removing one passes — that direction is a test coming back. Deleting a test outright is not caught by either rule and stays a matter for review — a bare `@Test` count would have failed the `ExampleUnitTest` removal the quality plan scheduled, so that half needs its own design (issue #16).

The context names in the branch protection (`Unit tests`, `Lint`, `Detekt`, `Format`, `Guardrails`, `Instrumented tests (API 26)`, `Instrumented tests (API 36)`) are the job names, hardcoded on both sides — the last two with the matrix value the job's `name:` interpolates. Renaming a job, or changing an API level in the matrix, without renaming the context turns the check into a missing one and blocks every merge — change them together.

The five Gradle jobs put `app/google-services.json` in place before anything else, because the Firebase plugins are applied unconditionally and every Gradle task needs the file. Guardrails does not: it reads the diff and counts lines, so it needs no JDK, no Android SDK and no Gradle at all. The step lives in one place, `.github/actions/google-services`, since two copies of a fallback rule drift into two different rules.

### Which jobs have work: `.github/actions/decide-work`

A pull request that only edits prose does not need five Gradle jobs, one of which boots an emulator on each of its two matrix legs, so the composite action answers `run=true` / `run=false` and every subsequent step in the five carries `if: steps.decide.outputs.run == 'true'`. **The condition never moves to job level**: all seven required contexts are matched by job name, and a job skipped at job level reports nothing at all, which blocks the merge button permanently instead of freeing it. A skipped job here still reports green in seconds.

The rule lives in exactly one place, the `ignored_globs` array at the top of `decide.sh`, and it is one glob: `*.md`. `docs/**` is not beside it because every file under `docs/` is Markdown — a second glob no test could tell from the first. `.github/**` is not there either, unlike SpendControl: a workflow is build configuration, not prose, and a PR that rewrites `ci.yml` has to run `ci.yml` or a broken step lands behind seven green checks that executed none of it.

A push to `main` answers `false` on its own, whatever changed: branch protection is `strict: true`, so the pull request already ran against the very tree being merged. `workflow_dispatch` answers `true` and is how a full run is forced on demand.

The decision needs the merge base, so **every calling job checks out with `fetch-depth: 0`** — that, and not the Spotless ratchet, is now why four of the five do. `decide.sh` has its own tests in `test.sh`, and the action runs them before it decides: nothing else on CI exercises them, and a wrong decision is the one failure that reports green.

### Alpha delivery to Firebase App Distribution

The `alpha` job builds a signed release APK on every push to `main` and uploads it to the `qa` tester group. It is **not** a required check — it runs after the merge, so there is nothing left for it to block — and it is gated `needs: [unit-tests]`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`. It carries no concurrency group of its own, and one would do nothing if it had: the workflow-level key already serialises runs on `main`, so two alpha jobs never overlap. That key cancels superseded runs on pull requests only — on `main` it would drop a build the merge is entitled to, and `workflow_dispatch` shares the group while doing full work. Two merges a minute apart therefore deliver twice, in order.

It checks out with `fetch-depth: 0` because `versionCode` is the commit count and `verifyReleaseVersioning` rejects a shallow clone outright. It asserts `GOOGLE_SERVICES_JSON_B64` is present **before** calling the shared action: that action's stub fallback is right for a fork pull request and wrong here, since a stub ships an app whose Crashlytics reports to nobody.

Six secrets beyond `GOOGLE_SERVICES_JSON_B64`: `ANDROID_KEYSTORE_B64` (base64 of `.key/Drevo.Keystore`, decoded into `$RUNNER_TEMP`), `SN_KEY_ALIAS`, `SN_KEY_PASSWORD`, `SN_STORE_PASSWORD`, `FIREBASE_APP_ID` and `FIREBASE_SERVICE_ACCOUNT_JSON` (a service account with App Distribution Admin). An upload naming a tester group that does not exist succeeds and reaches nobody, so the `qa` group has to exist in the Firebase console.

Lint runs with `warningsAsErrors`, so **a new warning fails the build**. The 25 pre-existing findings are parked in `app/lint-baseline.xml`; clearing them is phase 6 of the plan. After fixing one, regenerate with `./gradlew updateLintBaseline` — and strip the informational entries it adds back in, or later runs complain about baseline entries that no longer match.

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

`config/detekt/baseline.xml` holds the debt this landed on: **9 entries covering 19 findings** —
`MagicNumber` 12, `EmptyFunctionBlock` 6, `TooManyFunctions` 1.
The two counts differ because a baseline entry is a signature, not a location,
so one entry absorbs every identical finding. That cuts both ways: a *new* magic number written into
an already-baselined expression is suppressed silently. Detekt is a floor, not a proof.

`ImplicitDefaultLocale` restates one of the Kotlin conventions below in executable form, and is no
longer baselined — its three call sites in `timer/` name their `Locale`, so a new implicit one fails
the build. `PrintStackTrace` went the same way when its two call sites were fixed. `media/` is clear of `MagicNumber` too: phase 1 of the refactoring plan
moved the sample math into named constants and both of its entries went with it. The 12 that remain
sit in `MainActivity` (6), `timer/TimerView` (5) and `adapters/LanguagesArrayAdapter` (1).

The version is deliberate: detekt 2.0.0 is still alpha and is built against Kotlin 2.4 / AGP 9,
two minors and a major ahead of this project. Revisit when the project moves, not before.

## Kotlin conventions

Six rules, each of them a mistake this codebase has already made or is one edit away from making.

- **`String.format` always names its `Locale`, and for anything a user reads that `Locale` is
  `Locale.getDefault()`.** Leaving it out uses the default anyway, so "explicit" on its own changes
  no output — naming it makes the choice deliberate and reviewable instead of accidental.
  Locale-native digits are the intended behaviour, not the bug: on an Arabic device the timer reads
  `١٢:٣٤`, the same as the system clock, because someone who picked Arabic picked all of it.
  `Locale.ROOT` is for strings a machine parses, never for strings a person reads. The three call
  sites in `timer/` name it, and their four `DefaultLocale` baseline entries are gone with them, so
  a new implicit locale fails the build.
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

### Audio: one engine, one track, mixed in software

`media/NoiseEngine` owns one `AudioTrack` (44.1 kHz, mono, PCM 16-bit, `MODE_STREAM`) and one writer thread that serves every session of the engine's life, and **that thread is the sole owner of the track**: it builds it, plays it and releases it in its own `finally`, so no other thread can ever see a released track. The thread raises its own priority with `Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)`, because Java thread priorities map poorly onto Linux nice values. The track buffer is `getMinBufferSize * 4` **bytes** and one write covers half of it; `BYTES_PER_SAMPLE` is the one place a byte count turns into a sample count, since the code this replaced confused the two (issue #24).

`media/NoiseMixer` holds the mixing law and nothing else — no audio platform, no threading — so it is tested on the JVM: sum each `NoiseSource` scaled by its channel volume, clamp to `[-1, 1]`, convert to PCM 16-bit. The clamp is not decoration: without it the sum of two loud channels wraps the `Short` conversion into an audible crack. The sample math behind `NoiseSource` is plain Kotlin importing nothing from `android.*` and is tested on the JVM too: `media/WhiteNoise` (uniform random) and `media/BrownNoise` (integrates white noise via `lastOut + 0.02 * white`, clamped).

`NoiseSource.reset()` still has no production caller. The engine never resets its sources, so a stop/start cycle resumes the brown integrator where it left off — the behaviour the app has always had. Zeroing it is a behaviour change and needs to be asked for, not slipped into a refactoring.

`playback/PlaybackService` holds two `NoiseChannel`s (white and brown) and one `NoiseEngine` over them, started and stopped as a whole. A volume slider writes `NoiseChannel.volume` — a `@Volatile` field clamped to `[0, 1]` that the writer thread reads once per cycle — and nothing outside the writer thread touches the track. A channel at volume 0 is not generated at all, so "white noise only" costs nothing — the design this replaced kept the muted track running at full rate, which is why the note here used to warn that muting is not stopping. It is now, for the channel; stopping playback is still `stop()` on the engine, which stops both.

With the noise lab switched on the service holds more than two: one further `NoiseChannel` per entry of
`NOISE_LAB_CANDIDATES` in `media/NoiseLab.kt`, built from the same registry the Activity builds its sliders from.
The whole lab hangs off one compile-time constant there, `NOISE_LAB_ENABLED` — editing it to `false` puts the
experiment away without deleting a source, a key or a test, and the service is back to the two channels it ships
with. A lab volume defaults to 0, so an install nobody has touched sounds exactly as it did before the lab existed.

`start()`, `stop()` and `release()` are expected on the main thread, the first two are each a no-op when the engine is already in the state they ask for, and **none of the three waits for the writer thread**. The writer is created by the first `start()`, parks between sessions and ends on `release()`, which `PlaybackService.onDestroy()` calls; every one of the three takes a lock the writer holds only to read the intent out of it. A stop the writer has not noticed yet leaves it draining one last `write()`, and a start arriving meanwhile is served by that same thread once the old session is torn down, so two tracks never overlap and nothing blocks on a `join()` to arrange it. That replaced a `stop()` that did join — 176-208 ms on the main thread per stop, and one thread and stack per flap of audio focus had the join simply been dropped (issue #26).

`app/src/androidTest/.../NoiseEngineHammerTest` hammers 100 start/stop cycles against a real `AudioTrack`; CI runs it on an emulator at API 26 and API 36 on every pull request, and `connectedAndroidTest` runs it against whatever device is attached. It asserts that one writer thread serves all 100 cycles and survives every `stop()`, that `stop()` and `release()` return inside 50 ms, and that the thread is gone within 2 s of the `release()`. That last bound is what still ties the test to a real audio sink, which is why the emulator is deliberately not started with `-noaudio`: without one the guest accepts the writes far more slowly, and the writer's exit waits out the write in flight.

### Playback: a foreground service, not the Activity

`playback/PlaybackService` owns the engine, the sleep timer and the ongoing notification, so a session outlives the Activity — backgrounding the app, or the `recreate()` a theme or language change triggers, no longer stops the noise. It is a plain `Service` with `foregroundServiceType="mediaPlayback"`, deliberately not a media3 `MediaSessionService`: media3 wants a `Player` implementation and this app plays a generated track, not a media item. There are no lock-screen or headset-button controls, and adding them is its own decision.

It is driven two ways at once. `ACTION_START` (carrying `EXTRA_TIMER_MINUTES`) and `ACTION_STOP` drive playback; the `LocalBinder` lets a visible Activity read `isPlaying` and `remainingMillis`, push volume changes, and receive `onTick` / `onPlaybackStopped`. `MainActivity` binds in `onStart`, unbinds in `onStop`, and reflects the service's state rather than holding its own — the listener is cleared on both sides so a destroyed Activity cannot be reached from a service that outlives it.

Two rules are easy to break here. **Every `startForegroundService()` has to be answered by a `startForeground()`**, including one that arrives while playback is already running — an unanswered start crashes the app five seconds later, which is why the notification is posted before the "already playing" guard. And the **volumes are read from preferences at start**, not pushed by the Activity: the sliders persist on every move, so preferences are the single source and the binder setters carry only live changes.

`playback/AudioFocus` holds the focus request and the mapping of the raw focus constants onto what the service does: stop for good, silence the engine while keeping the session (a call must not extend the sleep timer), or resume. Ducking is **not** implemented on purpose — from API 26 the framework ducks the app's own track and never delivers `LOSS_TRANSIENT_CAN_DUCK` to a `CONTENT_TYPE_MUSIC` listener. A code-registered receiver (never a manifest one) stops playback on `ACTION_AUDIO_BECOMING_NOISY`.

None of the service is covered by tests yet. It is meant to be covered by instrumented tests on an emulator — Robolectric was weighed and ruled out, see "Testing strategy" in `docs/plans/REFACTORING_PLAN.md` — and CI now runs those on one, so what is still missing is the tests and no longer somewhere to run them. Until they are written, its behaviour is verified by hand on a device.

### Timer

Three pieces in `timer/`:
- `TimerView` — custom `LinearLayout` inflating `timer_view.xml`; owns the seekbar and the time label, and formats both the idle value and the countdown. Seekbar progress is in 30-minute units (`progress * 30` minutes), and the view hides the seekbar while playing.
- `TimerPreferences` — its own `SharedPreferences` file (`timer_prefs`), separate from the app-wide one.
- `SleepTimer` — the arithmetic only: a deadline on a clock the caller supplies, the milliseconds left on it, and the `mm:ss` / `hh:mm:ss` formatting. It imports nothing from `android.*` and is tested on the JVM. The service passes `SystemClock.elapsedRealtime()`; a `CountDownTimer` would have died with the Activity, which is what the deadline replaced.

The countdown itself runs in `playback/PlaybackService`, once a second, into the notification and into whatever Activity is bound.

### Preferences

Two distinct stores. `APP_PREFS` ("AppPreferences", constants at the top of `MainActivity.kt`) holds `whiteNoiseVolume`, `brownNoiseVolume`, `selectedTheme`, `selectedLanguage`. `timer_prefs` holds only the timer value. Don't consolidate one into the other without checking both readers.

Two more `APP_PREFS` keys belong to the noise lab — `labPinkNoiseVolume` and `labLeakyBrownNoiseVolume` — and they are the one set that is *not* declared at the top of `MainActivity.kt`: each lives on its candidate in `media/NoiseLab.kt`, so a new experiment stays one entry in one file. Both default to 0, which is why an untouched install is unchanged by the lab, and with `NOISE_LAB_ENABLED` set to `false` neither is read at all.

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
