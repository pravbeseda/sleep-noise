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

- [x] 1. Add the Kover plugin to `:app` with the class filter, no bound yet — files:
      `gradle/libs.versions.toml`, `app/build.gradle.kts` — lenses: none — done when
      `./gradlew koverLogDebug` prints a coverage figure computed over the Android-free classes and
      no others. Measured: **97.561 %**, 40 of 41 lines. The report holds six entries —
      `BrownNoise`, `NoiseChannel`, `NoiseMixer`, `WhiteNoise`, `SleepTimer` and
      `SleepTimer$Companion`; `NoiseSource` is an interface and contributes no line to measure.
- [x] 2. Set the verification bound — files: `app/build.gradle.kts` — lenses: none — done when
      `./gradlew koverVerifyDebug` is green at the chosen bound, and red at a bound raised above the
      measured figure (checked locally, not committed). Bound: **80 %** on the debug variant. Both
      halves were run: green at 80, and red at 99 with `Rule 'Line coverage of the Android-free
      classes' violated: lines covered percentage is 97.561000`.
- [x] 3. Wire it into the Definition of done and CI — files: `CLAUDE.md`, `README.md`,
      `.github/workflows/ci.yml` — lenses: none — done when the documented command runs green end to
      end locally and the CI job carries the same task under the existing `decide-work` gate.
      `README.md` carried a verbatim copy of the same command and had to move with it;
      `docs/plans/REFACTORING_PLAN.md` did not — see the ruling below.

## Rulings

- Reviewer: the comment claimed the trailing `*` on the **exclude** is what keeps `NoiseEngine`'s
  companion out of the report. Fixed — the claim was false. A companion holding only constants
  compiles to nothing executable and is dropped from the report whatever the pattern says, which
  `BrownNoise$Companion` demonstrates: it is inside the include and absent from the report. The
  comment now claims only what the include's `*` really does, which is to catch
  `SleepTimer$Companion` and its eight lines.
- Reviewer: "javac names `Owner$Companion`". Fixed — kotlinc emits those classes and javac never
  sees these sources.
- Reviewer: `log { onCheck = true }` on the `total` report attached coverage to `check` through the
  merged debug+release report — a different variant from the `koverLogDebug` this step measures and
  the bound step 2 sets, and it pulled `testReleaseUnitTest` into `check` along the way. Fixed by
  dropping it: step 2 puts the bound on the debug variant, and step 3 names the task in the
  Definition of done, which is what actually runs locally and on CI. Nothing runs `check` here. No
  replacement `log` block was needed either — Kover registers `koverLogDebug` on its own, and that
  is the task that prints the figure.
- Reviewer: the step's done-criterion said "exactly the six Android-free classes", and the report's
  six entries are not that set — `NoiseSource` is an interface with no executable line and never
  appears, while `SleepTimer$Companion` does. Ruled met in substance: the filter includes and
  excludes exactly the intended sources, and a class with no measurable line changes no figure. The
  step's wording is corrected above rather than the filter.

- Reviewer, both passes: the ledger promised a `log` block on the debug variant that step 2 did not
  add. Fixed in the ruling above rather than in the build script: `koverLogDebug` exists without any
  configuration, so a `log { }` block would be configuration that buys nothing.

- The step named `docs/plans/REFACTORING_PLAN.md`, whose "Testing strategy" section has a follow-up
  item this branch closes. That section is not on `main`: it lives on `docs/testing-strategy`
  (PR #29), still open. Ruled out of this branch — writing the section here would duplicate an
  unmerged branch's content and conflict when it lands. The item gets ticked once #29 is merged,
  which this branch's PR description says out loud so it is not lost with the session.

- Reviewer: neither `## Commands` block listed a kover task, so a developer working from that block
  would never learn the coverage tasks exist. Fixed in both `CLAUDE.md` and `README.md`:
  `koverVerifyDebug` and `koverLogDebug` are named there with one line each.
- Reviewer: the prose said the floor covers "the Android-free classes — the only ones a JVM test can
  reach", and `models/Language` disproves the equivalence: it imports nothing from `android.*` and
  is outside the filter. Fixed by stating the set as the definition and saying why that data holder
  is left out, rather than by widening the filter to a class with no behaviour to cover.
- Reviewer: the number 80 had come to live in three places — the build script, `CLAUDE.md` and
  `README.md` — with nothing marking which is authoritative, unlike the line-length rule that
  documents its three copies. Fixed by dropping the figure from `README.md`, which now points at
  `CLAUDE.md`, and by naming `app/build.gradle.kts` in `CLAUDE.md` as where the bound is set.
- Reviewer: the CI step runs `koverLogDebug` as well as the `koverVerifyDebug` the step named.
  Dropped — the extra task only prints, cannot change the job's outcome, and putting the figure in
  the log of every run is the reason it is there. The step comment already says so.

- Final reviewer: `minBound(80, CoverageUnit.LINE)` passed Kover's own default and cost an import to
  say it. Fixed — the argument and the import are gone; the rule is named "Line coverage of the
  Android-free classes", which says the same thing where a failure is actually read.

## Parked
