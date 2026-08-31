# Phase 1 — Extract pure noise sources and cover them with tests

Run plan for phase 1 of `docs/plans/REFACTORING_PLAN.md`. That file states the phase and its
tasks; this one carries the per-step done-criteria, the decisions taken during the run and
the rulings on review findings. The phase's checkboxes there are ticked when this run ends.

## Goal

Move the sample math out of `BaseNoiseGenerator`, which is welded to `AudioTrack`, into pure
`NoiseSource` implementations that a JVM test can exercise. Phase 2 rewrites the playback
path and needs that safety net before it starts.

## Decisions

- Where does the run plan live? → its own file next to `REFACTORING_PLAN.md`, because rulings
  and parked findings of one run do not belong in the seven-phase project plan; the phase's
  checkboxes there are ticked from here.
- Keep `WhiteNoiseGenerator` / `BrownNoiseGenerator` as classes? → yes, as thin subclasses
  passing their source to `BaseNoiseGenerator`, because the phase says "no behaviour change
  yet" and `MainActivity` constructs both by name. Phase 2 deletes all three.
- Inject `Random` into `BrownNoise` as well as `WhiteNoise`? → yes: the phase's own saturation
  test needs a biased random, which is not reachable without the seam.
- Sample type at the seam → `FloatArray` in `[-1, 1]`, as the phase specifies; the short
  conversion stays in `BaseNoiseGenerator`, unchanged from today's expression.
- Does `startNoise()` call `source.reset()`? → no, removed on a blocking review finding. Today
  a stop/start cycle on the same instance resumes the brown integrator where it left off, and
  reinstating it from zero is a behaviour change; phase 1 is a refactor and this repository does
  not mix the two in one PR. `reset()` stays part of the interface, tested, and phase 2's
  `NoiseEngine` is where it gets wired into playback.
- New `media/` constants → named, not literals: detekt has no baseline entry for the new files,
  and a new `MagicNumber` finding fails the build.

No strategic question arose in planning: the phase names its own tasks, its own tests and its
own done-criterion, and nothing in it changes user-visible behaviour, a stored format or a
dependency.

## Steps

- [x] 1. `NoiseSource` interface and `WhiteNoise`, test-first — files: `app/src/main/java/ru/pravbeseda/sleepnoise/media/NoiseSource.kt`, `media/WhiteNoise.kt`, `app/src/test/java/ru/pravbeseda/sleepnoise/media/WhiteNoiseTest.kt` — lenses: none — done when: `./gradlew testDebugUnitTest --tests "*WhiteNoiseTest"` is green on assertions that every sample lies in `[-1, 1]` and that a seeded instance is reproducible, and neither main file imports `android.*`.
- [x] 2. `BrownNoise`, test-first — files: `media/BrownNoise.kt`, `app/src/test/java/ru/pravbeseda/sleepnoise/media/BrownNoiseTest.kt` — lenses: none — done when: `./gradlew testDebugUnitTest --tests "*BrownNoiseTest"` is green on four assertions — samples in `[-1, 1]`, consecutive step never above `0.02`, saturation at the clamp under a biased random, `reset()` returning the integrator to zero — and the file imports no `android.*`.
- [x] 3. `BaseNoiseGenerator` delegates to a `NoiseSource` — files: `media/BaseNoiseGenerator.kt`, `media/WhiteNoiseGenerator.kt`, `media/BrownNoiseGenerator.kt`, `config/detekt/baseline.xml` — lenses: none — done when: `./gradlew assembleDebug detekt` is green, no sample math is left in the three generator files, `MainActivity` is untouched, and the two now-dead `media/` `MagicNumber` baseline entries are gone (16 entries → 14).
- [x] 4. Drop `ExampleUnitTest` and close the phase — files: `app/src/test/java/ru/pravbeseda/sleepnoise/ExampleUnitTest.kt` (deleted), `docs/plans/REFACTORING_PLAN.md` — lenses: none — done when: `./gradlew spotlessCheck detekt testDebugUnitTest lint` is green and the phase's task list carries the state this run left behind.

## Rulings

- Step 2, spec reviewer, `BrownNoiseTest.kt:28`: the `[-1, 1]` assertion stays green if the
  `coerceIn` is deleted, because an unbiased 0.02 walk over 1024 samples essentially never
  reaches the clamp — so the first criterion is carried by the saturation test. Dropped: the
  fix (a second biased random, or a walk long enough to drift out) only adds code, the clamp
  is already load-bearing in `biasedRandomSaturatesAtTheClamp`, and the finding is a
  suggestion. Cost if wrong: the lower clamp has no test of its own, so a one-sided clamp
  regression would be caught only by the upper-bound assertions.

## Parked
