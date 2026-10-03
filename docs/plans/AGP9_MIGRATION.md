# AGP 9 migration

The open item of phase 6 in `docs/plans/REFACTORING_PLAN.md`: "Update AGP and dependencies". Moving
to AGP 9 releases the pins that wait on it: Gradle Play Publisher 4.x, the Gradle wrapper's major and
`androidx.core` 1.19.

## Facts this plan rests on (checked 2026-10-03)

- AGP 9.4.1 is the latest stable. It needs Gradle ≥ 9.6.0 and JDK 17, and supports compileSdk up to
  37. Gradle 9.8.0 is the latest stable.
- AGP 9 builds Kotlin in (`android.builtInKotlin=true`), so `org.jetbrains.kotlin.android` must not
  be applied to `:app`. AGP itself pulls KGP 2.2.10; declaring the plugin `apply false` in the root
  build script lifts it to the catalog's 2.4.20. Kotlin's `jvmTarget` defaults to
  `compileOptions.targetCompatibility`.
- `android.newDsl=true` hides `applicationVariants`, and AGP 10 removes it. Nothing in the new API
  replaces `outputFileName`.
- Gradle Play Publisher 4.1.1 requires AGP ≥ 9. Its task names and CLI flags match 3.13.0, and
  `promote` still rewrites every release on the source track.
- Kover 0.9.11 supports AGP 9 with built-in Kotlin and fixes the 0 % report that 0.9.10 gives under
  the configuration cache.
- detekt 2.0 is still alpha (2.0.0-alpha.6) and moves to the plugin id `dev.detekt`. detekt 1.23.8
  runs on Gradle 9.8 / AGP 9.4.1 here, with deprecation warnings only.
- `androidx.core` 1.19.1 declares `minCompileSdk=37` and `minAndroidGradlePluginVersion=9.1.0`.
  API 37 is stable. Play requires targetSdk 36 and publishes no deadline for 37.
- A smoke build on a throwaway clone ran the whole Definition of done line green on AGP 9.4.1,
  Gradle 9.8.0, Kover 0.9.11 and GPP 4.1.1, at 96.5 % coverage. Release builds, the emulator and Play
  were not tried.

## Decisions

1. **Two PRs; detekt stays on 1.23.8.** PR A moves the toolchain, PR B moves the SDK. detekt moves
   when 2.0 is stable, in a PR of its own.
2. **The APK is no longer renamed by Gradle.** The `applicationVariants` block goes. The alpha job
   uploads `app/build/outputs/apk/release/app-release.apk` by its fixed path. Naming happens on CI, as
   it already does for the bundle in `release.yml`.
3. **Dependabot drops the major ignores of AGP, GPP and the wrapper** in PR A. A major that cannot go
   green still arrives on its own and holds back nothing. The detekt ignore stays, with its reason
   rewritten: 2.0 is alpha under a new plugin id. The `core-ktx` ignore goes in PR B.
4. **KGP comes onto the classpath through the root `apply false`**, the form the smoke build
   verified. The catalog alias stays and gets a comment saying why.
5. **The `kotlin { compilerOptions { jvmTarget } }` block goes.** It repeats `compileOptions`, which
   it now defaults to.

## PR A — `build/agp-9`: AGP 9.4.1 and the tools that move with it

1. Catalog: `agp` 9.4.1, `playPublisher` 4.1.1, `kover` 0.9.11. Drop the two "move with the AGP
   major" comments, and add one on the Kotlin alias → verify: `./gradlew help`.
2. Wrapper: `./gradlew wrapper --gradle-version 9.8.0`, run twice so the scripts update too →
   verify: `./gradlew --version`.
3. `app/build.gradle.kts`: remove the `kotlin.android` plugin, the `kotlin {}` block, the
   `applicationVariants` block and its imports. Remove `sarifReport = true` if lint still writes
   `lint-results-debug.sarif` without it, since AGP 9.4 deprecates the flag → verify: the file
   exists after `./gradlew lint`.
4. Review the AGP 9 default flips that reach this app: `r8.strictFullModeForKeepRules`,
   `r8.optimizedResourceShrinking` and `enableAppCompileTimeRClass` → verify: `assembleRelease`
   signs, and the release APK installs and plays on a device.
5. `ci.yml`: the "Locate the APK" step becomes the fixed path. Update the `release.yml:406` comment.
6. `dependabot.yml`: decision 3.
7. Docs: AGENTS.md (APK naming at line 78, the Dependabot paragraph, the detekt "revisit" sentence,
   the GPP version paragraph and its `3.13.0` caveat). Update the `3.13.0` comments in `promote.yml`
   and `rollout.yml` to 4.1.1, where the behaviour they describe still holds.
8. Definition of done line, then `connectedDebugAndroidTest` on an emulator. Also `./gradlew
   -PplayPublish publishReleaseBundle --track internal` without `--commit`, a dry run Play validates,
   if local Play credentials exist. Otherwise the PR says that path is unverified until the first
   release.

### Outcome

- Lint writes `lint-results-debug.sarif` without `sarifReport`, so the flag is gone.
- The release APK, signed locally with the debug key since the upload key's passwords are not on
  this machine, installs on an API 26 emulator and plays in the foreground service under R8's AGP 9
  defaults.
- The Definition of done line is green: 115 unit tests, 96.5 % coverage. `connectedDebugAndroidTest`
  passes 58 tests on API 26. `actionlint` is clean.
- The Play path is unverified, because there are no Play credentials locally. With `-PplayPublish`,
  GPP 4.1.1 registers the three tasks and every flag the workflows pass. Its
  `DefaultTrackManager.promote` matches 3.13.0 apart from the opt-in `retainExistingRollout`.

## PR B — `build/compile-sdk-37`: compileSdk 37 and `androidx.core` 1.19.1

1. `compileSdk = 37`, `coreKtx` 1.19.1, and drop its catalog comment. `targetSdk` stays 36.
2. Remove the `core-ktx` ignore from `dependabot.yml` and its sentence from AGENTS.md. Update the
   compileSdk in README.md line 54.
3. Fix whatever lint finds on API 37. Lint has no baseline to hide it in.
4. Make sure CI can resolve `platforms;android-37` (`setup-android` installs only
   `platform-tools`, and AGP downloads the platform when licences are accepted) → verify: the
   `Lint` and `Unit tests` jobs on the PR.
5. Definition of done line, then `connectedDebugAndroidTest`. Tick the phase 6 item in
   `REFACTORING_PLAN.md`.

## Not in scope

- detekt 2.0: when it is stable, a PR of its own.
- `targetSdk` 37: follows Play's deadline when one is published.
