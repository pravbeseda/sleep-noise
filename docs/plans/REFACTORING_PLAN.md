# Refactoring Plan

Staged plan for addressing the architectural issues found in the current implementation
(as of version 1.0.3, versionCode 5).

Each phase is meant to be a separate branch and PR. Phases are ordered so that every step
leaves the app in a shippable state, and so that the risky audio work happens only after
tests exist to protect it.

Phase 0 sets up CI/CD first, so that every later phase is verified automatically and lands
on a real device without a cable — modelled on the pipeline already running in the
SpendControl repository.

## Priority overview

| # | Phase | Problem it fixes | Risk | Estimate |
|---|-------|------------------|------|----------|
| 0 | CI/CD: tests, signed builds, delivery | Everything is manual; no automated verification | Medium | 1 day |
| 1 | Extract pure noise sources + tests | Zero test coverage of the only real logic | Low | 2-3 h |
| 2 | Single-track mixing engine | Data race on released `AudioTrack`, double battery drain | Medium | 3-4 h |
| 3 | Foreground playback service | Playback and timer die with the Activity; no audio focus | High | 1 day |
| 4 | ViewModel and state hoisting | 384-line god object, state lost on recreate | Medium | 1 day |
| 5 | Settings consolidation | Two prefs stores, magic strings, two sources of truth for locale | Low | 3-4 h |
| 6 | Cleanup: lint, insets, R8, dead deps | 40 lint warnings, unused Compose stack, no minification | Low | 3-4 h |

Total: roughly six to seven working days.

Phases 1-3 are the ones that change user-visible behaviour for the better. Phases 4-6 are
maintainability work and can be deferred if time is short — but phase 4 becomes much
cheaper right after phase 3, because the service already owns the state by then.

## Testing strategy: no Robolectric, no MockK

Decided 2 September 2026, by comparing with SpendControl — the repository this pipeline is
modelled on, which covers its logic without either dependency. Recorded here because "add
Robolectric for the service" is the suggestion that comes back every time the service is
called untested.

Three moves replace them:

1. **Pure logic stays Android-free and is tested on the JVM** — `media/` minus `NoiseEngine`,
   plus `timer/SleepTimer`, already is. `NoiseEngine` owns the `AudioTrack` and is Android
   plumbing by design; it is the one carve-out, and it is the same one everywhere below.
   SpendControl gets this as a compile-time guarantee: its logic lives in a separate
   `java-library` module that could not resolve an `android.*` import if it tried. This
   project is single-module, so the same rule has to be checked rather than granted.
2. **Android plumbing is tested on a real emulator**, in `androidTest` — the way SpendControl
   tests Room, its migrations and its file storage. `NoiseEngineHammerTest` is already written
   this way, and CI runs it on an emulator at API 26 and API 36 on every pull request.
3. **Coverage is measured on the Android-free packages only.** SpendControl puts its 80 % Kover
   bound on `:domain` alone, and its build script says why: a denominator full of Activities
   makes the figure answer no question however good the tests get. Single-module, this project
   names the same set with a Kover class filter instead of a module boundary.

Why not Robolectric: it is a second, approximate Android, and a foreground service, an ongoing
notification and audio focus are precisely where the approximation is thinnest — an emulator
answers the same questions truthfully for the price of a CI job. Why not MockK: nothing in this
codebase needs a mock that a hand-written fake or a constructor parameter does not cover.

**Do not reopen this by adding either to `gradle/libs.versions.toml` "just for one test".**
Reopening it means changing this section first, with the case for it.

### Follow-up work

- [x] Kover with a class filter, so the bound covers `media.*` minus `NoiseEngine`, plus the
      Android-free part of `timer/`, rather than the whole module. Landed in PR #30: the
      filter and the 80 % bound are in `app/build.gradle.kts`, and `koverVerifyDebug` joined
      the Definition of done in `CLAUDE.md`.
- [x] A CI job running `connectedAndroidTest` on an emulator — what turns point 2 from an
      intention into coverage, and the first thing to run the existing `NoiseEngineHammerTest`.
      Landed in PR #33: the `instrumented-tests` job in `.github/workflows/ci.yml` runs
      `connectedDebugAndroidTest` on `reactivecircus/android-emulator-runner` over API 26 and
      API 36, on every pull request and guarded by `decide-work` like the Gradle jobs. Both of its
      contexts are required status checks, added once they had been seen green on that pull
      request. The design is in [`CI_INSTRUMENTED_TESTS.md`](CI_INSTRUMENTED_TESTS.md).
- [x] An architecture test that reads the sources and fails if anything in `media/` other than
      `NoiseEngine`, or `timer/SleepTimer`, imports `android.*` — the single-module stand-in for
      SpendControl's module boundary. The exclusion is the Kover filter's, named once in both.
      SpendControl weighed Konsist and ArchUnit for this and wrote neither: Konsist is
      effectively unmaintained, and its rule is to reach for ArchUnit only past ~15 rules —
      below that, walking the file tree is cheaper than either. It holds six rules that way;
      this project needs one. Landed as
      `app/src/test/java/ru/pravbeseda/sleepnoise/architecture/AndroidFreeSourcesTest.kt`, which
      walks the two roots with `java.nio.file` and adds no dependency; it covers `androidx.*` on the
      same terms, and a fully-qualified `android.os.X` reference with no import line is the hole it
      deliberately leaves rather than parse Kotlin. Closes issue #32.

---

## Phase 0 — CI/CD

**Goal:** unit tests run on every PR, a merge into `main` puts a signed build on the phone
automatically, and releases go to Play without manual assembly.

Reference implementation: `SpendControl/.github/workflows/ci.yml` and
`SpendControl/doc/release-pipeline-2026-06-29.md`. The structure carries over, but three
things must **not** be copied verbatim — see "Differences from SpendControl" below.

Doing this first means the risky audio work in phase 2 is guarded by CI from day one. The
test suite is still empty at this point (phase 1 fills it), which is fine: the pipeline is
built now so that phase 1 merges straight into a green build.

### Deliverables

Each deliverable is one branch, one PR, and leaves the repository in a working state.
Branch names below are the ones actually used.

| ID | Deliverable | Branch | Depends on | Estimate |
|----|-------------|--------|------------|----------|
| D0 | Branch protection and repo hygiene | `chore/branch-protection` | — | 1 h |
| D1 | Make the build CI-ready | `chore/ci-ready-build` | D0 | 2-3 h |
| D2 | CI workflow: unit tests + lint | `ci/unit-tests` | D1 | 2 h |
| D3 | Alpha: signed APK to Firebase | `ci/alpha-firebase` | D2 | 2-3 h |
| D4 | Beta: AAB to Play internal | `ci/beta-play` | D3 | 2-3 h |

D0-D2 (about half a day) already give automated tests on every PR. D3 is where the
day-to-day benefit lands — a build on the phone after every merge. D4 can wait until there
is something worth releasing, realistically after phase 3.

---

### D0 — Branch protection and repo hygiene

Locks the workflow down before any automation exists, so nothing lands on `main` unreviewed.

- [x] Versioned `.githooks/pre-push` blocking direct pushes to `main` and `release` on
      `origin`, activated with `git config core.hooksPath .githooks`. SpendControl keeps its
      hook in `.git/hooks/pre-push`, where it is invisible to the repo and lost on a fresh
      clone — versioning it is the one improvement over that setup.
- [x] Versioned `.githooks/pre-commit` refusing commits made on a protected branch, so the
      mistake surfaces immediately instead of after a pile of commits has to be rewritten.
      An in-progress merge is exempt.
- [x] GitHub branch protection on `main`: pull request required, force-push and deletion
      blocked. This repo is **public**, so classic branch protection is available at no cost —
      SpendControl is private and could only use the local hook.
- [x] Zero required approvals: a solo maintainer cannot approve their own PR, and any non-zero
      count would deadlock every merge.
- [x] Enable `delete_branch_on_merge` on the repository, so merged branches do not pile up,
      and set `remote.origin.prune` locally so stale tracking refs disappear on fetch.
- [x] `.gitignore` for signed build outputs (`/app/release/`, `*.apk`, `*.aab`) and stray
      root-level screenshots, all of which are currently untracked clutter.
- [x] Commit the existing untracked docs: `CLAUDE.md` and this plan.

### D1 — Make the build CI-ready

Everything that has to be true before a runner can build this project at all.

- [x] **`google-services.json` is not in git.** `.gitignore` excludes `app/google-services.json`,
      while `app/build.gradle.kts` applies `com.google.gms.google-services` and the Crashlytics
      plugin unconditionally. Any CI job — even unit tests — fails at
      `processDebugGoogleServices`. Two options:
      1. Store it base64-encoded in a `GOOGLE_SERVICES_JSON_B64` secret and decode it into
         `app/` as the first build step. **Recommended.**
      2. Commit the file, as SpendControl does (`SpendConrol/google-services.json` is tracked).

      SpendControl's precedent does **not** transfer: that repository is private, this one is
      public. The usual defence — "it carries no secret beyond what already ships in the APK" —
      is technically true (Firebase keys identify a project, they do not authorise access; the
      real controls are Security Rules and the SHA-1 signing certificate), but it ignores cost
      of discovery. A key sitting in a public repository is grepped by automated scanners
      within hours, and once committed it is in the history for good. Only Analytics and
      Crashlytics are wired up here — no Firestore, RTDB, or Storage — so the blast radius is
      junk events, not data loss. Still not worth paying to undo later.

      Price of option 1, to be paid knowingly: a clean clone no longer builds until the file is
      fetched from the Firebase console. Document that where the build commands live.
- [ ] Independently of the above, restrict the Android API key by package name and SHA-1
      signing certificate in the Google Cloud console. Google recommends this regardless of
      where the file is stored, and it is what actually makes the key useless to anyone else.
- [x] Move `versionName` into `version.properties`, bumped manually on release.
- [x] Derive `versionCode` from `git rev-list --count HEAD`. The commit count is already an
      order of magnitude above the last manual `versionCode` of **5**, so the switch only ever
      raises the code and stays monotonic from there. The current value is whatever
      `git rev-list --count HEAD` prints — deliberately not restated here, since a number
      written into a document that lives in the repository it counts is stale on the next
      commit.
- [x] Reject a versionCode that cannot be trusted, via two separate guards rather than one
      threshold doing both jobs. `git rev-parse --is-shallow-repository` catches a truncated
      history at any depth — a `--depth 20` clone clears any numeric floor while still
      producing a stale count. The floor (5, the last hand-assigned value) then catches what
      depth cannot: a history that is not the one this app ships from. Both are enforced by
      `verifyReleaseVersioning`, hung off the tasks that package a release rather than off the
      requested task name, so `build` and `bundle` are covered and `lintRelease` is not.
- [x] Drop the parentheses from the APK filename (see the warning under D3).

### D2 — CI workflow: unit tests and lint

- [x] `.github/workflows/ci.yml`, triggered on `pull_request` and `push` to `main`, plus
      `workflow_dispatch`.
- [x] JDK 17 via `actions/setup-java@v4` (AGP 8.12 requires 17+; the module's own `jvmTarget`
      stays at 11), `android-actions/setup-android@v3`, `gradle/actions/setup-gradle@v4`.
- [x] `./gradlew testDebugUnitTest --stacktrace`. No product flavors here, so the task name is
      plain — unlike SpendControl's `testFreeOpenDebugUnitTest`.
- [x] Upload `app/build/reports/tests/testDebugUnitTest` as an artifact with `if: always()`.
- [x] Add a lint job with `warningsAsErrors` plus a baseline, so CI fails on **new** warnings
      only. This is what makes phase 6 measurable instead of open-ended: the debt is exactly
      the 29 findings in `app/lint-baseline.xml`.
- [x] Mark version-currency checks (`GradleDependency`, `NewerVersionAvailable`,
      `AndroidGradlePluginVersion`, `OldTargetApi`) informational. Their messages embed the
      versions being compared, so the baseline stops matching as soon as either side moves —
      they failed the first CI run against code that had not changed.
- [x] Once the check has run at least once, add it to the required status checks configured
      in D0. Done 8 August 2026: `strict: true`, contexts `Unit tests` and `Lint`. A context
      name is the job's `name:` value, and the two are stored in separate places — renaming a
      job in `ci.yml` without updating branch protection leaves a required check that can never
      report, which blocks every merge until someone edits the protection by hand.

### D3 — Alpha: signed APK to Firebase App Distribution

- [x] **Add a `signingConfig`** to `app/build.gradle.kts` reading `SN_*` project properties
      (`SN_KEY_ALIAS`, `SN_KEY_PASSWORD`, `SN_STORE_PASSWORD`, `SN_STORE_FILE`). None exists
      today. Give `storeFile` a non-null default path: `file(null)` throws at configuration
      time and breaks the whole project, including CI jobs that never sign anything.
      SpendControl hit exactly this and documents it in its `signingConfigs` block.
- [x] **Keystore into secrets.** `.key/Drevo.Keystore` is gitignored; base64-encode it into
      `ANDROID_KEYSTORE_B64` and decode into `$RUNNER_TEMP` at build time.
- [x] Job gated on `needs: unit-tests`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`.
- [ ] ~~`concurrency: { group: alpha, cancel-in-progress: true }` so rapid merges do not queue
      up.~~ **Rejected, not pending** — the third departure below says why. Left unticked so the
      plan does not claim a de-duplication the workflow does not do, and struck through so nobody
      picks it up as unfinished work.
- [x] Check out with `fetch-depth: 0`. Any shallow clone truncates the commit-count versionCode
      from D1, and the release gate rejects the build outright rather than letting a stale code
      through — so this is not optional for a job that packages a release.
- [x] Decode the keystore, then `./gradlew assembleRelease`.
- [x] Deliver with `wzieba/Firebase-Distribution-Github-Action` to the `qa` group. Firebase is
      already wired into the app (analytics + Crashlytics), so the Firebase project exists.
- [x] APK, not AAB: App Distribution only accepts AAB when the app is linked to Play and the
      bundle has been processed, whereas an APK installs on the phone immediately.

The `applicationVariants` block renames outputs to
`SleepNoise-<versionName>-<versionCode>-release.apk` — dash-separated, no parentheses, so the
upload path globs without quoting. The parentheses this section used to warn about are already
gone (see the checked item under D1); keep the separator as it is when wiring the upload.

That block is the one place still on the deprecated `applicationVariants` API, and it stays
there on purpose: `androidComponents.onVariants` has no equivalent. `VariantOutput` exposes
`versionCode`, `versionName` and `enabled` and nothing else — the same in `gradle-api` 8.12.2
and 9.0.1 — so AGP 9 removes the API without replacing what it is used for. Renaming through
the modern API means a `Copy` task wired to `SingleArtifact.APK`, which also changes where the
artifact lands. Settle that here, when the upload path that consumes the name is being written,
rather than guessing at it beforehand.

Landed on branch `ci/alpha-firebase`; the step-by-step record is in
[`D3_ALPHA_FIREBASE.md`](D3_ALPHA_FIREBASE.md). Three things came out differently from the sketch
above:

- `SN_STORE_FILE` defaults to `../.key/Drevo.Keystore` rather than SpendControl's path outside the
  repository, since this project keeps its keystore in `.key/`.
- Path filtering arrived with this deliverable although D3 never asked for it: a composite action,
  `.github/actions/decide-work`, lets the four Gradle jobs skip their steps on a Markdown-only pull
  request. It is SpendControl's mechanism minus the `.github/**` glob — a workflow is build
  configuration, and the diff that changes what CI does is the one CI must run in full.
- The alpha job has **no concurrency group**, where D3 asked for one. A group at job level cannot
  fire here: the workflow-level key already serialises runs on `main`, so two alpha jobs never
  overlap and there is nothing to cancel. Making that key unconditional instead would have a
  `workflow_dispatch` — same group, and unlike a push it does full work — cancel a delivery
  mid-upload, and a merge cancel a manual run. So two merges a minute apart deliver two builds, in
  order, and the tester installs the later one.

### D4 — Beta: AAB to Google Play internal

- [ ] Create a long-lived `release` branch (the repo currently has only `main` and a stale
      `sdk36`), following SpendControl's promotion model: features land in `main`, releases are
      promoted `main → release`, hotfixes branch from `release` and are merged back into `main`.
- [ ] Add `refs/heads/release` to the branch protection configured in D0 — the local hook
      already covers it.
- [ ] Trigger on `push` to `release` plus `workflow_dispatch`, gated on `needs: unit-tests`.
- [ ] Upload with `r0adkll/upload-google-play`, `packageName: ru.pravbeseda.sleepnoise`,
      `track: internal`.
- [ ] Keep `release` strictly append-only — no force-push, no rebase — or the commit-count
      versionCode can go backwards and Play will reject the upload.

⚠️ **Build a bundle here, not an APK.** SpendControl uploads APKs because its Play listing
predates August 2021 and is grandfathered in. Sleep Noise was published far later, so Play
requires an AAB: use `./gradlew bundleRelease` and upload `app/build/outputs/bundle/release/*.aab`.
Play App Signing is already in use for this app — `.key/create_sign.sh` wraps `pepk.jar`, which
exists only for uploading a key to Play — so `Drevo.Keystore` is the *upload* key and Google
re-signs the artifact.

### Secrets to create in the repository

| Secret | Used by | Source |
|---|---|---|
| `GOOGLE_SERVICES_JSON_B64` | every job that builds | `base64 -i app/google-services.json` |
| `ANDROID_KEYSTORE_B64` | alpha, beta | `base64 -i .key/Drevo.Keystore` |
| `SN_KEY_ALIAS`, `SN_KEY_PASSWORD`, `SN_STORE_PASSWORD` | alpha, beta | existing keystore credentials |
| `FIREBASE_APP_ID` | alpha | Firebase console → project settings |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | alpha | service account with App Distribution Admin |
| `PLAY_SERVICE_ACCOUNT_JSON` | beta | Play Console → API access |

### Differences from SpendControl worth restating

1. **AAB for Play**, not APK — the listing is not grandfathered (see D4).
2. **`google-services.json` is injected from a secret, not committed** — SpendControl can
   afford to commit it because it is private; this repository is public (see D1).
3. **No product flavors** — every Gradle task name loses the flavor segment, and the beta job
   needs no `strategy.matrix`.
4. **Real branch protection is available** — this repo is public, SpendControl is private
   (see D0).

### Done when

A pull request shows a green test run; merging into `main` sends a push notification with a
fresh build to the phone; pushing to `release` puts a bundle on the Play internal track.

---

## Phase 1 — Extract pure noise sources and cover them with tests

**Goal:** separate sample math from `AudioTrack` so it can be tested on the JVM, and so
phase 2 has a safety net.

Today `generateNoiseData()` lives inside `BaseNoiseGenerator`, which is welded to
`AudioTrack`. The brown-noise integrator is the only non-trivial logic in the project and
nothing verifies it.

### Tasks

- [x] Add `media/NoiseSource.kt`:
      `interface NoiseSource { fun fill(buffer: FloatArray); fun reset() }`.
      Use `FloatArray` in `[-1, 1]` — phase 2 needs float samples to mix before clipping.
- [x] Add `media/WhiteNoise.kt` and `media/BrownNoise.kt` implementing it. Move the math
      out of `WhiteNoiseGenerator` / `BrownNoiseGenerator` unchanged.
- [x] Inject randomness: `class WhiteNoise(private val random: Random = Random.Default)`,
      so tests can seed it and assert exact output.
- [x] Make `BaseNoiseGenerator` delegate to a `NoiseSource` (keep the existing public API
      of `startNoise` / `stopNoise` / `setVolume` for now — no behaviour change yet).
- [x] Write unit tests in `app/src/test/`:
  - every sample stays within `[-1, 1]` for both sources;
  - `BrownNoise` step size never exceeds `0.02` between consecutive samples;
  - `BrownNoise` saturates at the clamp instead of running away, when fed a biased random;
  - `reset()` returns `BrownNoise.lastOut` to zero;
  - seeded `WhiteNoise` is reproducible.
- [x] Delete `ExampleUnitTest`.

### Done when

`./gradlew testDebugUnitTest` runs real assertions, and no Android class is imported by
`NoiseSource` or its implementations.

---

## Phase 2 — Replace two AudioTracks with one mixing engine

**Goal:** fix the concurrency bug and halve the runtime cost.

Two problems are fixed together because they live in the same code.

**Data race.** In the writer thread:

```kotlin
audioTrack?.let {
    if (it.state == AudioTrack.STATE_INITIALIZED && !isStopped.get()) {
        it.write(noiseData, 0, noiseData.size)
    }
}
```

The reference is captured locally and the block is not synchronized, while `stopNoiseInternal()`
calls `release()` from the UI thread. Between the state check and `write()` the track can be
released. `audioTrack` is also not `@Volatile`, so the writer may never observe the `null`.
On top of that, `stopNoiseInternal()` is invoked twice — from `stopNoise()` and from the
thread's `finally` — and `isStopped` merely duplicates `!isPlaying`.

**Waste.** Two threads, two tracks, two buffers, both running at full rate even when a
channel's volume is 0 — all night.

### Tasks

- [x] Add `media/NoiseEngine.kt`: one `AudioTrack`, one writer thread, a list of
      `(NoiseSource, volume)` channels mixed in software.
- [x] Make the writer thread the sole owner of the `AudioTrack` lifecycle: it creates the
      track, and it releases it in its own `finally`. No other thread ever touches the track.
- [x] `stop()` becomes: set a `@Volatile` flag, then `join()` the thread. Remove `isStopped`;
      one flag is enough.
- [x] Mix as `sum = white * wVol + brown * bVol`, then clamp to `[-1, 1]` before converting
      to `Short`, so raising both sliders cannot clip.
- [x] Skip generation entirely for channels at volume 0.
- [x] Publish volume changes through `@Volatile` fields (or `AtomicInteger` bits) read by
      the writer thread — do not call into the track from the UI thread.
- [x] Replace `Thread.MAX_PRIORITY` with
      `Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)`; Java thread
      priorities map poorly onto Linux nice values.
- [x] Raise the buffer to about `minBufferSize * 4` to reduce wakeups over a long session.
- [x] Delete `BaseNoiseGenerator`, `WhiteNoiseGenerator`, `BrownNoiseGenerator`.

### Done when

Start/stop hammered 100 times in a row produces no crash and no `IllegalStateException` in
logcat; only one `AudioTrack` is alive during playback.

`app/src/androidTest/.../NoiseEngineHammerTest` executes as much of that as a test can: 100 cycles,
no crash, no `IllegalStateException` — neither escaping the writer thread nor logged by it — and
exactly one writer thread alive on the cycles that dwell long enough to look, none after the last
`stop()`. The track count is not asserted, because no API lets a process count its own live tracks;
it follows from the thread count instead, the track being a local of the writer thread and released
in that thread's own `finally`. It passed on the `Medium_Phone_API_36.0` emulator, which is the only
hardware this run had; CI now runs it on emulators at API 26 and API 36 on every pull request.
A real device is still unverified — see the risk below.

### Risk

This is the piece most likely to regress audibly (clicks on start/stop, buffer underruns).
Test on a real device, not only the emulator.

---

## Phase 3 — Foreground playback service

**Goal:** the app actually plays through the night. This is the phase that fixes the
product, not just the code.

The state this phase started from: `MainActivity` owned the engine and the timer, `onDestroy()`
stopped the noise, and the manifest declared no permissions at all. The consequences it fixed:

- backgrounding the app kept playing only until the system reclaimed the process;
- changing theme or language calls `recreate()`, which stopped playback outright;
- an incoming call played on top of the noise (no audio focus);
- unplugging headphones blasted the speaker (no `ACTION_AUDIO_BECOMING_NOISY`);
- the countdown timer lived in the Activity and died with it.

### Tasks

- [x] Add `playback/PlaybackService.kt` — a plain `Service`; media3 wants a `Player` implementation
      and this app plays a generated track. It owns `NoiseEngine`.
- [x] Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (API 34+),
      `POST_NOTIFICATIONS` (API 33+), and
      `android:foregroundServiceType="mediaPlayback"` on the service.
- [x] Request the notification permission at runtime on API 33+; playback must still work
      if the user denies it.
- [x] Ongoing notification with a stop action and the remaining timer.
- [x] Audio focus via `AudioFocusRequest`, which minSdk 26 makes available without the
      `androidx.media:media` compatibility wrapper.
      Handle `LOSS` (stop) and `LOSS_TRANSIENT` (pause). Ducking is the framework's from API 26 on:
      it attenuates the app's own track and sends no `LOSS_TRANSIENT_CAN_DUCK` at all.
- [x] Register a `BroadcastReceiver` for `ACTION_AUDIO_BECOMING_NOISY` and stop on it.
- [x] Move the timer into the service. `CountDownTimer` gave way to a deadline computed from
      `SystemClock.elapsedRealtime()`, ticked by a handler. No `AlarmManager`: an exact alarm needs
      `SCHEDULE_EXACT_ALARM`, which Android 14 does not grant on install, and the case it would
      rescue cannot happen — if the process dies, so does the noise the timer exists to stop.
- [x] `MainActivity` binds to the service and only reflects its state.

### Done when

Playback continues after the Activity is destroyed; the timer still fires after the app is
swiped away; a phone call interrupts the noise; unplugging headphones stops it.

---

## Phase 4 — ViewModel and state hoisting

**Goal:** break up the 422-line `MainActivity` and stop losing state on recreate.

`isPlaying` is a plain field. Phase 3 made it survivable — the Activity re-reads it from the
service on every bind — but everything else in the Activity still does not survive;
`android:configChanges="orientation|screenSize"` only covers rotation, not system language,
font size, or theme changes.

### Tasks

- [ ] Add `PlaybackViewModel` exposing a single `StateFlow<PlaybackState>`
      (`isPlaying`, `whiteVolume`, `brownVolume`, `timerMinutes`, `remaining`).
- [ ] Extract `ThemeController` and `LocaleController` out of `MainActivity`.
- [ ] Move the mailto intent and `getDebugInfo()` into a `support/` helper. Read the version
      from `BuildConfig` only — `getDebugInfo()` currently re-reads it via `PackageManager`.
- [ ] Collapse the duplicated white/brown code paths (two identical `SeekBar` listeners, two
      identical `setXxxNoiseVolume` methods) into one loop over a channel list.
- [ ] Give `TimerView` a listener callback instead of letting it write to `SharedPreferences`
      itself; persistence moves up to the ViewModel. Also drop the hidden side effect where
      `setPlayingState(false)` silently reloads the stored value.
- [ ] `MainActivity` keeps only view binding and menu wiring — target under 150 lines.

### Done when

Rotating the device or changing the theme mid-playback keeps the UI state consistent, and
`MainActivity` no longer touches `SharedPreferences` directly.

---

## Phase 5 — Settings consolidation

**Goal:** one source of truth per setting.

Two stores exist without a real reason: `AppPreferences` (volumes, theme, language) and
`timer_prefs` (timer value). Theme is passed around as the magic strings `"dark"` /
`"light"` / `"system"` in five places. The active language has two sources that can
disagree — `preferences.getString(CURRENT_LANGUAGE)` and the per-locale
`getString(R.string.lang)` — which drift apart when the user changes the system language.

### Tasks

- [ ] `enum class AppTheme(val key: String)` with `SYSTEM("system")`, `LIGHT("light")`,
      `DARK("dark")`. Keep the string keys: installed users already have them stored.
- [ ] `SettingsRepository` as the single facade over preferences.
- [ ] Migrate `timer_prefs` into the main store on first launch, reading the old value if
      present so existing users keep their timer.
- [ ] Pick one source of truth for the locale. Recommended: keep
      `AppCompatDelegate.getApplicationLocales()` as the authority and drop the stored
      language key, using `R.string.lang` only as the fallback for a fresh install.
- [ ] Consider `AppCompatDelegate.setApplicationLocales` without `recreate()` — it already
      restarts the Activity itself, so the current code recreates twice.

### Done when

No string literal `"dark"` remains outside the enum, and upgrading from 1.0.3 preserves
volumes, theme, language, and timer.

---

## Phase 6 — Cleanup

Lint reports 36 findings (`./gradlew lint`, report at
`app/build/reports/lint-results-debug.html`). 25 of them are real and parked in
`app/lint-baseline.xml`; the other 11 are version-currency checks marked informational in
`app/build.gradle.kts`, so this phase is measured by emptying the baseline. It started at 29
real findings; the four `DefaultLocale` entries were the first to go.

### Deadline: targetSdk

Tracked here rather than in lint. `OldTargetApi` fires on CI (the runner's SDK components are
ahead of a local install) but it is informational, and a baseline entry would not survive the
regenerations this phase requires — so the reminder lives where regeneration cannot drop it.

Google requires each app to target the previous year's API level by 31 August annually, and
misses mean Play stops accepting updates. `targetSdk` is currently 36, which satisfies the
present requirement; the lint finding is about the cycle after it.

- [ ] Confirm the current Play deadline and target level in the Play Console before each
      August, and raise `targetSdk` with a pass over behaviour changes for that release.

### Tasks

- [x] `DefaultLocale` (4 hits, then in `TimerController.kt` and `TimerView.kt`; phase 3 moved the
      formatting into `SleepTimer.kt` and `TimerView.kt`): pass
      `Locale.getDefault()` explicitly. The decision was taken on 8 August 2026 and is written
      into CLAUDE.md — localized digits stay, so an Arabic device keeps reading `١٢:٣٤` like its
      system clock. That makes this a no-op on output: it silences the check and records the
      choice, nothing more. Do not "fix" it to `Locale.ROOT` while clearing the baseline.
- [ ] Check the timer under RTL on a device, which nothing has done. `"%02d:%02d"` reaches the
      bidi algorithm as digits around a neutral colon, and the failure mode is a countdown that
      reads `34:12`. `BidiFormatter` is already applied to language names in `MainActivity` but
      not here. Independent of the digit question above — it goes wrong the same way with
      Western digits in an RTL layout.
- [ ] `UseKtx` (8 hits): replace `preferences.edit().apply()` with `edit { }` and friends.
- [ ] `Untranslatable` (5), `IconDuplicates` (5), `MonochromeLauncherIcon` (2),
      `AlwaysShowAction`, `Overdraw`, `UnusedResources`.
- [ ] `ContentDescription` on the cats `ImageView` in `activity_main.xml`.
- [ ] Replace `android:fitsSystemWindows="true"` with
      `ViewCompat.setOnApplyWindowInsetsListener`, which is the supported approach under the
      mandatory edge-to-edge of targetSdk 35+.
- [ ] Drop the unused Compose stack (Compose BOM, material3, activity-compose, ui-tooling,
      `buildFeatures.compose`) — the UI is entirely XML and Views. Alternatively, commit to a
      Compose migration, but do not leave it half-declared.
- [ ] Enable `isMinifyEnabled = true` for release and verify the Crashlytics mapping upload.
- [ ] Update AGP and dependencies (`AndroidGradlePluginVersion` 2, `GradleDependency` 7,
      `NewerVersionAvailable` 2).
- [ ] Replace the odd `android:tint="@color/cardview_dark_background"` on the play button
      with a project colour.

---

## Out of scope

Deliberately not planned here:

- Compose migration — the Views UI is small and working; rewriting it buys nothing right now.
- Dependency injection framework — at this size, constructor injection is enough.
- New noise types (pink, fan, rain) — worth doing, but only after phase 2, since
  `NoiseEngine` makes adding a channel trivial.

## Release checklist

After each phase: green CI, plus a manual smoke test on a real device — CI cannot hear audio
glitches, which is exactly what phase 2 risks.

Before phase 0 is done: `./gradlew testDebugUnitTest lint` locally, and bump `versionCode` and
`versionName` by hand in `app/build.gradle.kts`, following the existing `Release 1.0.4 (6)`
commit message format.

After phase 0: bump `versionName` in `version.properties` only — `versionCode` comes from the
commit count, and promoting `main → release` publishes the build.
