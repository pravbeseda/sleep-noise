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

- [x] 1. The architecture test — files: `app/src/test/java/ru/pravbeseda/sleepnoise/architecture/AndroidFreeSourcesTest.kt`
      — lenses: none — done when: the test passes on the tree as it stands, and fails naming the file
      and the import line when the issue's reproduction (`import android.util.Log` in `WhiteNoise.kt`)
      is planted; the planted violation is reverted before the step is committed.
- [ ] 2. Documentation — files: `docs/plans/REFACTORING_PLAN.md`, `CLAUDE.md` — lenses: none —
      done when: the "Testing strategy" follow-up checkbox is ticked and names this test, and the
      `CLAUDE.md` rule that states the Android-free boundary says what enforces it.

## Rulings

Step 1, gate round 1 — two reviewers, no `blocking` finding, six suggestions:

- Spec: the comment claimed to mirror the Kover `NoiseEngine*` glob while the code compares with the
  file name `NoiseEngine.kt` → **fixed**, the comment now states the stricter rule it actually
  implements.
- Spec: an `androidx.*` import escapes the rule, and is as unrunnable on a JVM test as an `android.*`
  one → **fixed**, the regex is `^import androidx?\.`. Nothing in the scanned set imports `androidx`
  today, so the widened rule fires on nothing that exists; verified by planting one and watching it
  fail.
- Spec: a fully-qualified reference (`android.os.SystemClock.elapsedRealtime()`) with no import line
  escapes → **dropped**. Issue #32 specifies the import rule, and catching a qualified reference
  needs source parsing rather than a line match: `SleepTimer.kt:9` already mentions `android.*` in a
  KDoc line, so a looser pattern fails a legal tree. The narrower rule that never lies is worth more
  than the wider one that cries wolf.
- Quality: drop the `root()` helper, since `Files.walk` and `Files.readAllLines` throw on a missing
  path anyway → **dropped**. What the helper adds is the sentence naming the working-directory
  assumption, which is the one thing a `NoSuchFileException: media` does not say — and that
  assumption is what the implementation actually tripped over.
- Quality: replace the regex with `startsWith("import android.")` → **dropped**, superseded by the
  `androidx` fix above: one regex carrying both prefixes is shorter than two `startsWith` calls.
- Quality: the class set is named in `app/build.gradle.kts` and `CLAUDE.md` with no pointer back to
  the test, so a new pure package added to the Kover filter would silently go unchecked → **fixed in
  step 2**, where the documentation is updated.

## Parked

_None yet._
