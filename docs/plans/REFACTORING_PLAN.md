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

- [ ] Versioned `.githooks/pre-push` blocking direct pushes to `main` and `release`, activated
      with `git config core.hooksPath .githooks`. SpendControl keeps its hook in
      `.git/hooks/pre-push`, where it is invisible to the repo and lost on a fresh clone —
      versioning it is the one improvement over that setup.
- [ ] GitHub branch protection on `main`: pull request required, force-push and deletion
      blocked. This repo is **public**, so classic branch protection is available at no cost —
      SpendControl is private and could only use the local hook.
- [ ] Zero required approvals: a solo maintainer cannot approve their own PR, and any non-zero
      count would deadlock every merge.
- [ ] `.gitignore` for signed build outputs (`/app/release/`, `*.apk`, `*.aab`) and stray
      root-level screenshots, all of which are currently untracked clutter.
- [ ] Commit the existing untracked docs: `CLAUDE.md` and this plan.

### D1 — Make the build CI-ready

Everything that has to be true before a runner can build this project at all.

- [ ] **`google-services.json` is not in git.** `.gitignore` excludes `app/google-services.json`,
      while `app/build.gradle.kts` applies `com.google.gms.google-services` and the Crashlytics
      plugin unconditionally. Any CI job — even unit tests — fails at
      `processDebugGoogleServices`. Two options:
      1. Commit the file, as SpendControl does (`SpendConrol/google-services.json` is tracked).
         It carries no secret beyond what already ships inside the APK. **Recommended.**
      2. Store it base64-encoded as a secret and decode it in a step.
- [ ] Move `versionName` into `version.properties`, bumped manually on release.
- [ ] Derive `versionCode` from `git rev-list --count HEAD`. Current commit count is **34**
      against a manual `versionCode` of **5**, so the switch is safe and monotonic — the next
      build jumps to 35 and keeps climbing.
- [ ] Set the floor to the current value (5) and fail loudly on release builds when the count
      falls below it, which is the signature of a shallow CI clone.
- [ ] Drop the parentheses from the APK filename (see the warning under D3).

### D2 — CI workflow: unit tests and lint

- [ ] `.github/workflows/ci.yml`, triggered on `pull_request` and `push` to `main`, plus
      `workflow_dispatch`.
- [ ] JDK 17 via `actions/setup-java@v4` (AGP 8.12 requires 17+; the module's own `jvmTarget`
      stays at 11), `android-actions/setup-android@v3`, `gradle/actions/setup-gradle@v4`.
- [ ] `./gradlew testDebugUnitTest --stacktrace`. No product flavors here, so the task name is
      plain — unlike SpendControl's `testFreeOpenDebugUnitTest`.
- [ ] Upload `app/build/reports/tests/testDebugUnitTest` as an artifact with `if: always()`.
- [ ] Add a lint job. There are 40 existing warnings, so generate a baseline first
      (`./gradlew updateLintBaseline`) and let CI fail only on **new** warnings. This is what
      makes phase 6 measurable instead of open-ended.
- [ ] Once the check has run at least once, add it to the required status checks configured
      in D0.

### D3 — Alpha: signed APK to Firebase App Distribution

- [ ] **Add a `signingConfig`** to `app/build.gradle.kts` reading `SN_*` project properties
      (`SN_KEY_ALIAS`, `SN_KEY_PASSWORD`, `SN_STORE_PASSWORD`, `SN_STORE_FILE`). None exists
      today. Give `storeFile` a non-null default path: `file(null)` throws at configuration
      time and breaks the whole project, including CI jobs that never sign anything.
      SpendControl hit exactly this and documents it in its `signingConfigs` block.
- [ ] **Keystore into secrets.** `.key/Drevo.Keystore` is gitignored; base64-encode it into
      `ANDROID_KEYSTORE_B64` and decode into `$RUNNER_TEMP` at build time.
- [ ] Job gated on `needs: unit-tests`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`.
- [ ] `concurrency: { group: alpha, cancel-in-progress: true }` so rapid merges do not queue up.
- [ ] Check out with `fetch-depth: 0` — the commit-count versionCode from D1 collapses to 1 on
      a shallow clone.
- [ ] Decode the keystore, then `./gradlew assembleRelease`.
- [ ] Deliver with `wzieba/Firebase-Distribution-Github-Action` to the `qa` group. Firebase is
      already wired into the app (analytics + Crashlytics), so the Firebase project exists.
- [ ] APK, not AAB: App Distribution only accepts AAB when the app is linked to Play and the
      bundle has been processed, whereas an APK installs on the phone immediately.

⚠️ The `applicationVariants` block renames outputs to `SleepNoise-1.0.3(5)-release.apk`.
**Parentheses in a filename** are fragile in shell globs and artifact upload paths. Either
quote every path carefully, or — simpler — change the separator to `SleepNoise-1.0.3-5-release.apk`
while setting this up.

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
| `ANDROID_KEYSTORE_B64` | alpha, beta | `base64 -i .key/Drevo.Keystore` |
| `SN_KEY_ALIAS`, `SN_KEY_PASSWORD`, `SN_STORE_PASSWORD` | alpha, beta | existing keystore credentials |
| `FIREBASE_APP_ID` | alpha | Firebase console → project settings |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | alpha | service account with App Distribution Admin |
| `PLAY_SERVICE_ACCOUNT_JSON` | beta | Play Console → API access |

### Differences from SpendControl worth restating

1. **AAB for Play**, not APK — the listing is not grandfathered (see D4).
2. **`google-services.json` must be un-ignored or injected** — SpendControl has it committed,
   this repo does not (see D1).
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

- [ ] Add `media/NoiseSource.kt`:
      `interface NoiseSource { fun fill(buffer: FloatArray); fun reset() }`.
      Use `FloatArray` in `[-1, 1]` — phase 2 needs float samples to mix before clipping.
- [ ] Add `media/WhiteNoise.kt` and `media/BrownNoise.kt` implementing it. Move the math
      out of `WhiteNoiseGenerator` / `BrownNoiseGenerator` unchanged.
- [ ] Inject randomness: `class WhiteNoise(private val random: Random = Random.Default)`,
      so tests can seed it and assert exact output.
- [ ] Make `BaseNoiseGenerator` delegate to a `NoiseSource` (keep the existing public API
      of `startNoise` / `stopNoise` / `setVolume` for now — no behaviour change yet).
- [ ] Write unit tests in `app/src/test/`:
  - every sample stays within `[-1, 1]` for both sources;
  - `BrownNoise` step size never exceeds `0.02` between consecutive samples;
  - `BrownNoise` saturates at the clamp instead of running away, when fed a biased random;
  - `reset()` returns `BrownNoise.lastOut` to zero;
  - seeded `WhiteNoise` is reproducible.
- [ ] Delete `ExampleUnitTest`.

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

- [ ] Add `media/NoiseEngine.kt`: one `AudioTrack`, one writer thread, a list of
      `(NoiseSource, volume)` channels mixed in software.
- [ ] Make the writer thread the sole owner of the `AudioTrack` lifecycle: it creates the
      track, and it releases it in its own `finally`. No other thread ever touches the track.
- [ ] `stop()` becomes: set a `@Volatile` flag, then `join()` the thread. Remove `isStopped`;
      one flag is enough.
- [ ] Mix as `sum = white * wVol + brown * bVol`, then clamp to `[-1, 1]` before converting
      to `Short`, so raising both sliders cannot clip.
- [ ] Skip generation entirely for channels at volume 0.
- [ ] Publish volume changes through `@Volatile` fields (or `AtomicInteger` bits) read by
      the writer thread — do not call into the track from the UI thread.
- [ ] Replace `Thread.MAX_PRIORITY` with
      `Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)`; Java thread
      priorities map poorly onto Linux nice values.
- [ ] Raise the buffer to about `minBufferSize * 4` to reduce wakeups over a long session.
- [ ] Delete `BaseNoiseGenerator`, `WhiteNoiseGenerator`, `BrownNoiseGenerator`.

### Done when

Start/stop hammered 100 times in a row produces no crash and no `IllegalStateException` in
logcat; only one `AudioTrack` is alive during playback.

### Risk

This is the piece most likely to regress audibly (clicks on start/stop, buffer underruns).
Test on a real device, not only the emulator.

---

## Phase 3 — Foreground playback service

**Goal:** the app actually plays through the night. This is the phase that fixes the
product, not just the code.

Current state: `MainActivity` owns the generators and the timer, `onDestroy()` stops the
noise, and the manifest declares no permissions at all. Consequences:

- backgrounding the app keeps playing only until the system reclaims the process;
- changing theme or language calls `recreate()`, which stops playback outright;
- an incoming call plays on top of the noise (no audio focus);
- unplugging headphones blasts the speaker (no `ACTION_AUDIO_BECOMING_NOISY`);
- the countdown timer lives in the Activity and dies with it.

### Tasks

- [ ] Add `playback/PlaybackService.kt` (`MediaSessionService`, or a plain `Service` plus a
      `MediaSessionCompat` if the media3 dependency is unwanted). It owns `NoiseEngine`.
- [ ] Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (API 34+),
      `POST_NOTIFICATIONS` (API 33+), and
      `android:foregroundServiceType="mediaPlayback"` on the service.
- [ ] Request the notification permission at runtime on API 33+; playback must still work
      if the user denies it.
- [ ] Ongoing notification with a stop action and the remaining timer.
- [ ] Audio focus via `AudioManagerCompat` / `AudioFocusRequestCompat` from
      `androidx.media:media` — `AudioFocusRequest` alone requires API 26 and minSdk here is 24.
      Handle `LOSS` (stop), `LOSS_TRANSIENT` (pause), `LOSS_TRANSIENT_CAN_DUCK` (lower volume).
- [ ] Register a `BroadcastReceiver` for `ACTION_AUDIO_BECOMING_NOISY` and stop on it.
- [ ] Move the timer into the service. Replace `CountDownTimer` with a deadline computed
      from `SystemClock.elapsedRealtime()`, and schedule the stop with `AlarmManager`
      (`setExactAndAllowWhileIdle`) so an 8-hour timer survives Doze instead of drifting.
- [ ] `MainActivity` binds to the service and only reflects its state.

### Done when

Playback continues after the Activity is destroyed; the timer still fires after the app is
swiped away; a phone call interrupts the noise; unplugging headphones stops it.

---

## Phase 4 — ViewModel and state hoisting

**Goal:** break up the 384-line `MainActivity` and stop losing state on recreate.

`isPlaying` is a plain field, so it does not survive configuration changes;
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

Lint currently reports 40 warnings (`./gradlew lint`, report at
`app/build/reports/lint-results-debug.html`).

### Tasks

- [ ] `DefaultLocale` (4 hits, `TimerController.kt:18,20` and `TimerView.kt`): pass an
      explicit `Locale`. Arabic is a shipped language, so the timer currently renders
      Arabic-Indic digits there. Decide deliberately: `Locale.ROOT` for stable digits, or
      keep the localized ones.
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
