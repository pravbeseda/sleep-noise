# Kover coverage on the Android-free packages

## Goal

Measure line coverage of the code that JVM tests can actually reach — `media/` minus the audio
engine, and `timer/SleepTimer` — and put a floor under it that the build enforces. Follow-up item 1
of "Testing strategy" in `REFACTORING_PLAN.md`; no production code changes.

## Decisions

- Which classes count → `media.*` and `timer.SleepTimer`, with `media.NoiseEngine`,
  `timer.TimerPreferences` and `timer.TimerView` excluded, because they import `android.*` and no
  JVM test can reach them. SpendControl puts its bound on `:domain` alone for the same reason: a
  denominator full of Android classes makes the figure answer no question however good the tests
  get. This project is single-module, so the same denominator has to be cut by a class filter.
- Which bound → decided in step 2 from the measured figure, with headroom below it. A bound set at
  today's exact number turns the next covered edge case into an argument with the build; a bound
  that is lowered to make a red run go green is not a floor at all.
- Kover 0.9.9 → the version SpendControl runs, on the same Kotlin and AGP generation as this
  project.
- Where the check runs → the existing `Unit tests` job, not a sixth one. A new job would need a new
  required status check, and branch protection matches contexts by job name.

## Steps

- [ ] 1. Add the Kover plugin to `:app` with the class filter and coverage logging, no bound yet —
      files: `gradle/libs.versions.toml`, `app/build.gradle.kts` — lenses: none — done when
      `./gradlew koverLogDebug` prints a coverage figure computed over exactly the six Android-free
      classes and no others.
- [ ] 2. Set the verification bound — files: `app/build.gradle.kts` — lenses: none — done when
      `./gradlew koverVerifyDebug` is green at the chosen bound, and red at a bound raised above the
      measured figure (checked locally, not committed).
- [ ] 3. Wire it into the Definition of done and CI — files: `CLAUDE.md`,
      `.github/workflows/ci.yml`, `docs/plans/REFACTORING_PLAN.md` — lenses: none — done when the
      documented command runs green end to end locally and the CI job carries the same task under
      the existing `decide-work` gate.

## Rulings

## Parked
