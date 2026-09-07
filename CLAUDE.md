# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android app (`ru.pravbeseda.sleepnoise`) that synthesizes pink and brown noise in real time for sleep, with a countdown timer. Single-module Gradle build (`:app`), Kotlin, minSdk 26 / target+compile SDK 36, JVM target 11.

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
base commit, so nothing about it belongs on a pre-push line — its scripts do run here, one of them
against `BASE_SHA=origin/main`, but only against a `main` a fetch has just moved.
`Instrumented tests (API 26)` and
`(API 36)` are the remaining two, and unlike Guardrails they *can* be run here —
`connectedAndroidTest`, in the Commands section — but they need a device or an emulator, and a
pre-push line that does not run without one is a line that gets skipped. So a green local run
means the work is done as far as a machine with no device can tell; it does not mean the PR is
mergeable. See the CI section.

New tooling joins this line as it lands; Kover was the most recent.

## CI

`.github/workflows/ci.yml` runs seven jobs. Six of them report the **seven required status checks**: a red run blocks the merge button, and the branch has to be up to date with `main` first. Six jobs and seven checks because **instrumented tests** is a matrix over API 26 and API 36 and reports one context per leg. None can be bypassed from the UI; `enforce_admins` is on. The seventh job, **alpha**, delivers and is deliberately not required — see the delivery section below.

Five of the six — unit tests, instrumented tests, lint, detekt and format — are triggered on every PR and push to `main`, but each one first asks `.github/actions/decide-work` whether it has anything to do. The sixth, **Guardrails**, runs on pull requests only, because it compares the PR against `github.event.pull_request.base.sha` and a push to `main` has nothing to compare against. That is why it became required by hand and only after it had been seen passing on a PR: a required check that has never reported blocks every merge in the repository, so making it required before the first green run would have locked the repo. The two instrumented contexts were added the same way and for the same reason: by hand, on PR #33, once both had been seen green on it.

It enforces three rules this file states in prose. Two of them read what a diff makes visible: that neither baseline grows (entry counts compared against the base commit), and that no `@Ignore` or `@Disabled` line is *added* under `app/src/test/` or `app/src/androidTest/` — removing one passes, since that direction is a test coming back. The third catches what a diff does not show at all: deleting a test method, or the whole file, adds no line for either of the others to match. `.github/scripts/no-deleted-tests.sh` counts `@Test` annotations across both test source sets at the merge base and at the branch head, and fails when the total drops. **The merge base, not `base.sha`** — which costs nothing on CI and is what makes the script usable off it. The job checks out with no `ref:`, so on a `pull_request` event `HEAD` is `refs/pull/N/merge` and its first parent is `base.sha`, which makes the merge base `base.sha` itself. Run by hand on the branch with `BASE_SHA=origin/main` the two part company: `main` has moved on, and every test it gained since would otherwise read as one the branch deleted. It counts the whole tree rather than each file, so a rename, a move between files or source sets and a split class all remove and add the same annotations and pass. Comments are cut out before anything is counted, in both the forms an editor writes them — `//` per line and a `/* */` block, whose lines carry no leading star when the IDE produces it. Switching a test off that way adds no `@Ignore` for the step above to see, and would be invisible here too if the count took the line at its word. A block comment counts as opening only where one starts a line, which is where an editor puts it: anywhere else a `/*` is far likelier to sit inside a string, and `val marker = "/*"` once opened a comment that never closed, so every annotation after it went uncounted on both sides at once and a real deletion read as no change. What the scanner still cannot see is a `@Test` inside a string literal, and chasing that means lexing Kotlin — raw strings and escapes included — which is not what a floor is for: it is against the ways a test is actually switched off, not a proof against a forgery. The price of that is stated rather than hidden: a pull request that deletes one test and adds another passes, because the net is what is measured, and that much stays a matter for review. There is no escape hatch, on the same terms as the baselines — a deletion that is genuinely right is a conversation, not a flag (issue #14). It costs nothing so far: run against all 38 merged pull requests in this repository, the rule blocks none. Three commits do drop a test, `ExampleUnitTest` among them, and each sits in a pull request that added more elsewhere — so the `@Test` count the quality plan feared would have failed that removal does not, at the granularity the check actually runs. `.github/scripts/no-deleted-tests.test.sh` holds the cases, and the job runs it before the check for the reason the `decide-work` action runs its own: a wrong count is the failure that reports green.

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

Lint runs with `warningsAsErrors`, so **a new warning fails the build**. The 22 pre-existing findings are parked in `app/lint-baseline.xml`; clearing them is phase 6 of the plan. After fixing one, regenerate with `./gradlew updateLintBaseline` — and strip the informational entries it adds back in, or later runs complain about baseline entries that no longer match.

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

`config/detekt/baseline.xml` holds the debt this landed on: **6 entries covering 9 findings** —
`MagicNumber` 6, `EmptyFunctionBlock` 2, `TooManyFunctions` 1.
The two counts differ because a baseline entry is a signature, not a location,
so one entry absorbs every identical finding. That cuts both ways: a *new* magic number written into
an already-baselined expression is suppressed silently. Detekt is a floor, not a proof.

`ImplicitDefaultLocale` restates one of the Kotlin conventions below in executable form, and is no
longer baselined — its three call sites in `timer/` name their `Locale`, so a new implicit one fails
the build. `PrintStackTrace` went the same way when its two call sites were fixed. `media/` is clear of `MagicNumber` too: phase 1 of the refactoring plan
moved the sample math into named constants and both of its entries went with it. `MainActivity` is
clear of both rules since its noise sliders moved into `ui/NoiseControlView`, which took their
percentage literals and their empty seekbar callbacks with them. The 6 `MagicNumber` findings that
remain sit in `timer/TimerView` (5) and `adapters/LanguagesArrayAdapter` (1), and the 2
`EmptyFunctionBlock` ones in `timer/TimerView`.

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
  theme is `models/AppTheme` and its `key` is the only place `"dark"` is spelled out; the language is
  still a literal in several places, and phase 5 of the plan is where that ends. Do not add the
  twentieth occurrence in the meantime.
- **New dependencies go through `gradle/libs.versions.toml`,** with a line in the PR description
  saying why. The Compose stack is the cautionary tale: seven artifacts on the classpath, none used.
- **`versionCode`, `app/version.properties` and the versioning block of `app/build.gradle.kts` are
  release-PR territory.** Every other PR leaves them alone. See the versioning section for why the
  code is derived rather than written.

## Architecture

### Audio: one engine, one track, mixed in software

`media/NoiseEngine` owns one `AudioTrack` (44.1 kHz, mono, PCM 16-bit, `MODE_STREAM`) and one writer thread that serves every session of the engine's life, and **that thread is the sole owner of the track**: it builds it, plays it and releases it in its own `finally`, so no other thread can ever see a released track. The thread raises its own priority with `Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)`, because Java thread priorities map poorly onto Linux nice values. The track buffer is `getMinBufferSize * 4` **bytes** and one write covers half of it; `BYTES_PER_SAMPLE` is the one place a byte count turns into a sample count, since the code this replaced confused the two (issue #24).

`media/NoiseMixer` holds the mixing law and nothing else — no audio platform, no threading — so it is tested on the JVM: sum each `NoiseSource` scaled by its channel volume, clamp to `[-1, 1]`, convert to PCM 16-bit. The clamp is not decoration: without it the sum of two loud channels wraps the `Short` conversion into an audible crack. The sample math behind `NoiseSource` is plain Kotlin importing nothing from `android.*` and is tested on the JVM too: `media/PinkNoise` (Kellett's filter bank, normalised to a fixed RMS) and `media/LeakyBrownNoise` (a one-pole low-pass on white, normalised to the same RMS from its own pole). Which source each shipping slider drives is `media/ShippingNoises.kt`, and it is named there rather than in the service so a JVM test can mix exactly what a user hears — `ShippingNoiseMixTest` does, and asserts two things about it: that the pair at full volume barely reaches the mixer's clamp, and that the shipping corner still reaches the audible band. The second is not redundant — the source normalises to `NORMALISED_SOURCE_RMS` from its own pole, so the clipped share barely moves with the cutoff and the first assertion passes with the corner put back to the walk's own ~3 Hz.

Two sources no longer sound in the app and stay as the references their tests measure against. `media/WhiteNoise` (uniform random) is what `PinkNoiseTest` measures pink's spectral tilt against, and the source `NoiseEngineHammerTest` drives. `media/BrownNoise` (a random walk, `lastOut + 0.02 * white`, clamped) is what `LeakyBrownNoiseTest` measures the audible-band difference against: it corners at ~3 Hz, so nearly all of its level is a subsonic wander no speaker returns, and the mixer's clamp charged the other channels for it — at full volume beside pink it clipped ~10 % of the samples where the shipping pair clips ~0.5 %. `BROWN_NOISE_CUTOFF_HZ` is 60 Hz, the darkest of the three the lab put on trial and the one that was indistinguishable from the walk by ear.

Three of the lab's sources are textures, each built rather than sampled — the app ships no audio assets, and a
generated one is a few constants instead of a few megabytes. `media/SurfNoise` is two bands under one wave
envelope: a rumble that only breathes and a spray that belongs to the break, with each wave's length drawn as
it starts, since surf on a fixed period is heard as a machine. `media/RainNoise` is a bright sheet with the
weight of the downpour under it and drops struck out of the same bright band — a burst, not a pitched ping.
`media/WheelClatterNoise` is the rumble of a carriage with thumps in pairs: two axles of a bogie a third of a
second apart, the next joint seconds away, both intervals jittered. All three share `media/OnePole`, which
carries the gains that put either half of a split back at the level of the white it came from, so the weights
that mix two bands mean what they say.

The three are normalised on different terms from the steady sources, and the tests say so. Surf is held to
`NORMALISED_SOURCE_RMS` through its loudest second rather than its average, because a wave source that
averages to the shared level puts its break far past full scale. Rain and the clatter keep the shared average
and pay for their peaks in the clamp: 0.32 % and 0.47 % of their own samples on their tests' own seed, and
0.25-0.32 % and 0.46-0.57 % across the seeds tried, where pink measures 0.002 %, brown 0.004 % and surf
0.0001 % alone. Each test bounds the spread rather than the measurement — a bound set at what one seed
measures asserts the seed. They are the first sources here to spend any of their own samples that
way, and the shipping pair's ~0.5 % is not the precedent for it — that figure is the pair *mixed*, which
`ShippingNoiseMixTest` measures through the mixer and bounds at 2 %. What the mix says about these two is
smaller than it looks: a third source at full volume takes it to ~2.2 % whether that source is rain, the
clatter or a steady leaky brown at 250 Hz, measured while that one was still on trial. Promoting either of
them out of the lab means
revisiting the level — a shipping source that clips on its own is a different thing from a lab candidate
that does.

The fourth candidate is a colour rather than a texture. `media/VioletNoise` is the first difference of uniform
white — `f^2`, the mirror of what `BrownNoise` does by integrating the same input, and the brightest of the named
colours. It is also the one source here that cannot clip: differencing two independent draws doubles their
variance and at most doubles their bound, so normalising to `NORMALISED_SOURCE_RMS` lands the peak at 0.61 of full
scale, a crest factor of 2.4 where every other source here runs near 5. There is no clamp in it — not a clamp that
never fires, but a bound the arithmetic already carries, and `VioletNoiseTest` asserts the headroom rather than
trusting it. Its gain is derived from the distribution the way `OnePole`'s are derived from the pole, so no
measured constant stands in for one. It is on trial because bright is the one direction this app has never gone,
and violet is reached for to mask tinnitus far more often than to sleep: whether it is bearable at all is an ear
question, which is what the lab is for.

`NoiseSource.reset()` still has no production caller. The engine never resets its sources, so a stop/start cycle resumes the brown integrator where it left off — the behaviour the app has always had. Zeroing it is a behaviour change and needs to be asked for, not slipped into a refactoring.

`playback/PlaybackService` holds two `NoiseChannel`s (pink and brown) and one `NoiseEngine` over them, started and stopped as a whole. A volume slider writes `NoiseChannel.volume` — a `@Volatile` field clamped to `[0, 1]` that the writer thread reads once per cycle — and nothing outside the writer thread touches the track. A channel at volume 0 is not generated at all, so "pink noise only" costs nothing — the design this replaced kept the muted track running at full rate, which is why the note here used to warn that muting is not stopping. It is now, for the channel; stopping playback is still `stop()` on the engine, which stops both.

With the noise lab switched on the service holds more than two: one further `NoiseChannel` per entry of
`NOISE_LAB_CANDIDATES` in `media/NoiseLab.kt`, built from the same registry the Activity builds its sliders from.
The whole lab hangs off one compile-time constant there, `NOISE_LAB_ENABLED` — editing it to `false` puts the
experiment away without deleting a source, a key or a test, and the service is back to the two channels it ships
with. A lab volume defaults to 0, so an install nobody has touched sounds exactly as it did before the lab existed.
Nothing enforces the flag's value per build type, so **a release PR sets it to `false`**: left on, a Play release
ships four developer-facing sliders whose English labels are not translated into any of the six locales. It is
`true` as the project stands, with violet on trial and the three parked candidates showing beside it — putting the
lab away again is that one edit, and it deletes no source, key or test either way.

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

Two distinct stores. `APP_PREFS` ("AppPreferences", constants at the top of `MainActivity.kt`) holds `pinkNoiseVolume`, `brownNoiseVolume`, `pinkNoiseEnabled`, `brownNoiseEnabled`, `selectedTheme`, `selectedLanguage`. `timer_prefs` holds only the timer value. Don't consolidate one into the other without checking both readers.

Every noise has a `*Enabled` key beside its volume — the two shipping ones here, each lab candidate on its own
descriptor — and they default to `true`, so an install made before the toggles existed sounds exactly as it did.
A switched-off noise **keeps its stored level**: the gate is applied where the volume is handed to the engine,
never by writing 0 over the level. That gate is written twice on purpose — `ui/NoiseControlView` applies it to the
live changes it pushes over the binder, and `PlaybackService` applies it again when it reads the preferences at
start, because a session begun with no Activity in sight reads nothing else.

Eight more `APP_PREFS` keys belong to the noise lab, a `lab<name>NoiseVolume` / `lab<name>NoiseEnabled` pair for each of the four candidates on trial — `Violet`, `Surf`, `Rain` and `WheelClatter` — and they are the one set that is *not* declared at the top of `MainActivity.kt`: both keys are derived from the candidate's name in `media/NoiseLab.kt`, so a new experiment stays one entry in one file. The volumes default to 0, which is why an untouched install is unchanged by the lab, and with `NOISE_LAB_ENABLED` set to `false` none of the eight is read at all. A retired candidate leaves its pair behind in the store — the three leaky-brown ones did — and nothing reads a key the registry no longer names.

### Theme

Two themes, `purple` and `dark`, named by `models/AppTheme` and cycled by the action-bar button — one
press, no popup, in the enum's own order. **`purple` is the default**, so a fresh install opens in the
colour the splash screen ends on. `AppTheme.fromKey` maps anything else to that default, which is how
an install that stored the retired `light` or `system` is carried across.

Both themes are dark ones, so both are built on plain `Theme.AppCompat` — the dark one, with no day
variant for a `uiMode` to select — and **night mode is not touched at all**: a `DayNight` parent held
in the dark by a forced `MODE_NIGHT_YES` is the same appearance reached the long way round. For the
same reason **there is no `values-night/`**: that qualifier would answer for both themes at once, so
every colour that separates them is named in the style instead. `applyTheme` still runs **before**
`super.onCreate`, and changing the theme still calls `recreate()`. The status bar is told to use light
icons unconditionally — neither theme has a light background left for dark ones to sit on.

`Theme.SleepNoise.Purple` puts the splash colour on the window as a gradient
(`drawable/window_background_purple`, `#25064F` down to the splash's own `#430985`) and darkens the
action bar under it. Its accent is light enough that the play triangle has to be dark on it, which is
what `colorOnAccent` is for: `colorOnPrimary` is the cats and the text, and those want the opposite.

### Localization

Supported: en (default), ar, de, es, ru, uk. The mechanism is non-obvious:

- Each `values-XX/strings.xml` defines `<string name="lang">XX</string>`. `getString(R.string.lang)` is how the code asks "which locale is actually active" — used to preselect the language dialog and to decide whether to append "(Language)" to the menu title.
- The chosen code is stored in `APP_PREFS`/`selectedLanguage` and applied with `AppCompatDelegate.setApplicationLocales`.

To add a language: create `values-XX/strings.xml` including the `lang` key, add a flag drawable, and add a `Language(...)` entry to the array in `MainActivity.languageSelection()`. The array also carries an `engName` used by `LanguagesArrayAdapter`; RTL is handled via `BidiFormatter` and `android:supportsRtl`/`layoutDirection="locale"` in the manifest.

## UI is Views, not Compose

The build enables Compose (`buildFeatures.compose`, Compose BOM, material3, activity-compose), but **no Compose is used anywhere**. The entire UI is XML layouts with AppCompat: `activity_main.xml`, `noise_control_view.xml`, `timer_view.xml`, `dialog_credits.xml`, `item_lang.xml`, plus `menu/` for the action bar. Follow the existing View-based approach unless deliberately migrating; don't assume Compose because the dependencies are present.

`activity_main.xml` is a `ConstraintLayout` stacked from the bottom up: the version line, the picture
on it, then the play button with the timer, each keeping its own height. **The noise rows take the
whole remainder above them and scroll only once there is more than that.** They are the only block
here whose height depends on how many noises the app has, so they are the only one the remainder can
go to — and nothing else can take it, which is what separates this from the two layouts below that
also gave the remainder away: the picture is capped and the play block wraps its content.

A `Guideline` at 0.45 held that share until the noise lab put a third row on the screen. Two rows fit
in 45 % and a third did not, so the `ScrollView` cut it in half while the band under the line stood
mostly empty — and a clipped row with no scrollbar reads as a rendering bug rather than as an
invitation to scroll. `requiresFadingEdge` is the other half of that fix: a row that really is cut
off now fades out instead of ending mid-glyph, which is what says there is more below.

**No size on this screen comes from a resource a rotation would change: what is left is percentages,
one aspect ratio and one dp cap, all resolved at measure time.** `MainActivity` declares
`configChanges="orientation|screenSize"` and is therefore never recreated on a rotation, so a `-land`
or `-h500dp` value, or a `resources.getBoolean` read in `onCreate`, is the portrait one for the rest
of the session — a layout that leans on either is correct only until the user turns the phone. This
is not a style preference; it is the bug two drafts of this screen hit before either was merged.
`values-sw320dp` is the one qualifier here that is safe, and the reason is the whole of the rule: `smallestScreenWidth` is the
same number in both orientations, so the play button's size cannot go stale where a `-land` or `-h`
one would.

The picture's box is the drawing itself: 70 % of the width and a height from
`layout_constraintDimensionRatio` carrying the vector's own 585.62 x 170.1, so no letterbox opens
between it and the version line. `cats_max_height` (96dp) is what keeps that width-driven height
honest on a wide window, where 70 % of the width would otherwise be most of the height — the picture
is decoration and the first thing to give way.

Four layouts got this screen wrong before the current one, each in its own way. The weighted
`LinearLayout` handed 4/5 of the free height to two noise rows and left a hole above the play button.
A `ConstraintLayout` chain fixed the hole and let the picture take whatever height its width dictated
— at 2400x1080 that was the whole screen, sliders gone and the play button a sliver under the action
bar. Config-qualified shares then fixed *that* and survived only until a rotation. The 0.45 guideline
that replaced them survived a rotation and not a third noise: a fixed share cannot answer a question
whose answer is the number of rows. Giving the rows the remainder is the first of the four that can,
and it is safe here only because the two blocks that once took the remainder for themselves are now
both bounded.
`androidx.constraintlayout` is a direct dependency for it rather than the transitive one material
pulls in.

The play button is an `ImageButton` sized by `play_button_size`, 80dp in `values-sw320dp` and 56dp in
the default bucket every narrower screen falls back to — a display or font scale that leaves the
screen under 320dp wide gets a circle that fits it. It is not a `Button` with a compound drawable,
because a compound drawable is painted at the icon's own 48dp whatever the button measures, and the
smaller circle would clip it; `scaleType="fitCenter"` scales the icon with the circle instead.

`ui/NoiseControlView` is the one row every noise gets: a speaker toggle, a label and a slider, bound to that
noise's own preference keys by `bind(NoiseControl, SharedPreferences) { volume -> ... }` and reporting only the
volume the mix should hear. The two shipping noises declare it in `activity_main.xml`, the lab builds one per
candidate in code, and neither knows how the toggle is persisted or how a switched-off row is dimmed. A new noise
that wires its own slider by hand is the mistake this replaced.

Every slider in the app — the two noise rows, each lab candidate and the timer — wears
`Widget.SleepNoise.Slider`: a 4dp groove with a 14dp round thumb, drawn white and coloured by the
style's tints, so one drawable serves both themes and the unfilled half reads as a groove rather than
as `colorControlNormal`. It is applied per widget rather than as the theme's `seekBarStyle` for the
same reason the toggle below is, and there is no slider in this app that should look like anything
else.

The toggle is still an `AppCompatCheckBox`, wearing `Widget.SleepNoise.NoiseToggle`: the button drawable is a
speaker, struck through while the noise is off. The style sits on the widget rather than on the theme's
`checkboxStyle`, so a checkbox added anywhere else still looks like a checkbox. The slider drives it both ways —
a level the **user** sets switches the noise on, a level dragged to zero switches it off — and only for
`fromUser` changes, since restoring a stored level at bind or `recreate()` time must switch nothing on by
itself. `bind` closes the same circle from the other side: a stored level of zero reads as off however the
stored flag was left, because pink and every lab candidate default to 0 % with their `*Enabled` key set, and a
sounding speaker over a silent slider says something untrue. The toggle keeps its own end of that bargain —
switching a silent noise on raises it to `MIN_AUDIBLE_PROGRESS`, 1 %, since a noise switched on at zero would
read as off again the next time the row is bound.

## Versioning and releasing

`versionName` lives in `app/version.properties` and is the only value bumped by hand.

`versionCode` is **derived** from `git rev-list --count HEAD` — never edit it. It is monotonic only while `main` (and later `release`) stay append-only, so no force-push or rebase on those branches.

A shallow clone undercounts, which would publish a code below what is already on Play, so any shallow checkout is rejected — not just `--depth 1`, since a depth of 20 would clear a numeric threshold while still producing a stale code. A count below the floor (`5`, the last hand-assigned value) is rejected too, as a history that is not the one the app ships from.

A missing or keyless `version.properties` is rejected on the same terms: the `versionName` falls back to `0.0.0`, and a release carrying that placeholder is one nobody can identify afterwards.

The rejection is a task, `verifyReleaseVersioning`, wired into `packageRelease` and `packageReleaseBundle` — the two tasks that turn a version into a publishable artifact. So `./gradlew build` and `./gradlew bundle` are covered even though neither names a release, while `lintRelease`, `testReleaseUnitTest` and any debug build still work on a shallow clone, falling back to the floor. **Any CI job that builds a release must check out with `fetch-depth: 0`.**

Release commits follow the message form `Release 1.0.3 (5)`.
