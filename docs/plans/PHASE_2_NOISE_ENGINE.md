# Phase 2 — One mixing engine instead of two AudioTracks

Run plan for phase 2 of `docs/plans/REFACTORING_PLAN.md`. That file states the phase, the race it
fixes and its task list; this one carries the per-step done-criteria, the decisions taken during the
run and the rulings on review findings. The phase's checkboxes there are ticked when this run ends.

## Goal

Replace the two `AudioTrack`s — each with its own writer thread, each running at full rate even at
volume 0 — with one track, one writer thread and a software mixer. That closes the data race on a
released `AudioTrack` and halves the runtime cost of a night's playback.

## Decisions

- How is the phase's done-criterion verified? → an instrumented hammer test in `androidTest/`, run
  by this session on the `Medium_Phone_API_36.0` emulator (100 start/stop cycles, no crash, no
  `IllegalStateException`, one live `AudioTrack`), plus JVM tests for the mixing math and a manual
  checklist in the PR for a real device. The emulator does not settle clicks or underruns, but it
  does settle the race on `release()`, which is the defect the phase exists for. Answered by the
  user before the run started.
- Mixing law → `sum = white * wVol + brown * bVol`, then clamp to `[-1, 1]`, as the phase
  prescribes. The clamp is what stops the `Short` conversion wrapping around; loudness of a single
  channel is unchanged from today, and both sliders at maximum distort exactly as the platform
  mixer distorts them today.
- Where the pure/Android seam goes → `NoiseMixer` (pure, JVM-tested) holds the mixing law,
  `NoiseEngine` holds the `AudioTrack` and the thread. Phase 1 put the sample math behind
  `NoiseSource` for exactly this reason; the mixer is the same trick one level up.
- Buffer sizing → `AudioTrack.getMinBufferSize` returns **bytes**, and the old code used that
  number as a sample count (issue #24). The engine keeps bytes and samples apart by name and
  closes that issue.

## Steps

- [x] 1. `NoiseMixer`, test-first — files: `app/src/main/java/ru/pravbeseda/sleepnoise/media/NoiseMixer.kt`, `app/src/test/java/ru/pravbeseda/sleepnoise/media/NoiseMixerTest.kt` — lenses: none — done when: `./gradlew testDebugUnitTest --tests "*NoiseMixerTest"` is green on assertions that two sources are summed by their volumes, that the result clamps at `[-1, 1]` instead of wrapping the `Short` conversion, and that a channel at volume 0 is not generated at all, and the file imports no `android.*`.
- [x] 2. `NoiseEngine` — files: `media/NoiseEngine.kt` — lenses: none — done when: `./gradlew assembleDebug detekt` is green, the writer thread is the only code that creates or releases the `AudioTrack`, `stop()` is a `@Volatile` flag plus `join()` with no second flag, volume reaches the thread without the UI thread touching the track, the thread priority comes from `Process.setThreadPriority(THREAD_PRIORITY_URGENT_AUDIO)`, and byte counts and sample counts are named apart (issue #24).
- [ ] 3. Rewire `MainActivity` and delete the old hierarchy — files: `MainActivity.kt`, `media/BaseNoiseGenerator.kt`, `media/WhiteNoiseGenerator.kt`, `media/BrownNoiseGenerator.kt` (all three deleted) — lenses: none — done when: `./gradlew assembleDebug detekt testDebugUnitTest lint` is green, no `*NoiseGenerator` class is left in the repository, and the two volume sliders and the play button drive the engine through the same five call sites they drive the generators through today.
- [ ] 4. The hammer test — files: `app/src/androidTest/java/ru/pravbeseda/sleepnoise/media/NoiseEngineHammerTest.kt` — lenses: none — done when: the test runs 100 start/stop cycles against a real `AudioTrack` and passes on the `Medium_Phone_API_36.0` emulator, with the run's output read in this session.
- [ ] 5. Documentation — files: `CLAUDE.md`, `docs/plans/REFACTORING_PLAN.md` — lenses: none — done when: the Architecture section describes one engine rather than two generators mixed by the OS, no sentence in `CLAUDE.md` names a class this phase deleted, and phase 2's task list carries the state this run left behind.

## Rulings

- Step 2, round two, `NoiseEngine.kt:57`: `buildTrack` sits outside the `try`, so an
  `UnsupportedOperationException` from `AudioTrack.Builder.build()` kills the process instead of
  being logged like the two sibling error exits. Dropped: the old `BaseNoiseGenerator` built its
  track outside any catch as well, on the UI thread, so this is today's behaviour rather than a
  regression; the fix only adds a catch clause for a device failure nobody here can reproduce, and
  the finding is a suggestion raised in the round that was only meant to check the fixes. Cost if
  wrong: on a device that cannot open a 44.1 kHz mono PCM track the app crashes rather than
  starting silently — which is arguably the better of the two anyway.

## Parked
