# Architecture test: `media/` and `timer/SleepTimer` stay Android-free

Closes issue #32.

## Goal

Two rules — the test-first boundary in `CLAUDE.md` and the Kover coverage denominator in
`app/build.gradle.kts` — assume that `media/` minus `NoiseEngine`, plus `timer/SleepTimer`, import
nothing from `android.*`. Nothing checks that today, so the assumption fails silently. A JVM unit
test walks those sources and fails naming the file and the offending line.

## Decisions

- Konsist and ArchUnit → neither, walk the file tree with `java.nio.file.Files`, because the project
  needs one rule and both dependencies cost more than one rule is worth. Decided in issue #32 and in
  `REFACTORING_PLAN.md` "Testing strategy"; not reopened here.
- A separate `java-library` module → no, single-module stays. Same source, same reason.
- Where the exclusion is named → once in the test file, as a constant carrying a comment that points
  at the Kover filter. Reading `app/build.gradle.kts` from a test to derive it would parse a build
  script at runtime to avoid writing one name twice.
- Test package → `ru.pravbeseda.sleepnoise.architecture`, outside the packages it inspects, so it is
  not mistaken for a test of the classes themselves. Test classes are not in the Kover denominator,
  so the location changes no coverage figure.

## Steps

- [ ] 1. The architecture test — files: `app/src/test/java/ru/pravbeseda/sleepnoise/architecture/AndroidFreeSourcesTest.kt`
      — lenses: none — done when: the test passes on the tree as it stands, and fails naming the file
      and the import line when the issue's reproduction (`import android.util.Log` in `WhiteNoise.kt`)
      is planted; the planted violation is reverted before the step is committed.
- [ ] 2. Documentation — files: `docs/plans/REFACTORING_PLAN.md`, `CLAUDE.md` — lenses: none —
      done when: the "Testing strategy" follow-up checkbox is ticked and names this test, and the
      `CLAUDE.md` rule that states the Android-free boundary says what enforces it.

## Rulings

_None yet._

## Parked

_None yet._
