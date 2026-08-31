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
- [x] 3. Rewire `MainActivity` and delete the old hierarchy — files: `MainActivity.kt`, `media/BaseNoiseGenerator.kt`, `media/WhiteNoiseGenerator.kt`, `media/BrownNoiseGenerator.kt` (all three deleted) — lenses: none — done when: `./gradlew assembleDebug detekt testDebugUnitTest lint` is green, no `*NoiseGenerator` class is left in the repository, and the two volume sliders and the play button drive the engine through the same five call sites they drive the generators through today.
- [x] 4. The hammer test — files: `app/src/androidTest/java/ru/pravbeseda/sleepnoise/media/NoiseEngineHammerTest.kt` — lenses: none — done when: the test runs 100 start/stop cycles against a real `AudioTrack` and passes on the `Medium_Phone_API_36.0` emulator, with the run's output read in this session.
- [x] 5. Documentation — files: `CLAUDE.md`, `README.md`, `docs/code-quality-suggestions.md`, `docs/plans/REFACTORING_PLAN.md` — lenses: none — done when: the Architecture section describes one engine rather than two generators mixed by the OS, none of those four documents describes the deleted hierarchy as present, and phase 2's task list carries the state this run left behind. `README.md` and `docs/code-quality-suggestions.md` joined this step in step 3, where the implementer found both still describing it. The plan files under `docs/plans/` that name `BaseNoiseGenerator` in a problem statement are records of what was true when they were written and are left alone.

## Rulings

- Step 2, round two, `NoiseEngine.kt:57`: `buildTrack` sits outside the `try`, so an
  `UnsupportedOperationException` from `AudioTrack.Builder.build()` kills the process instead of
  being logged like the two sibling error exits. Dropped: the old `BaseNoiseGenerator` built its
  track outside any catch as well, on the UI thread, so this is today's behaviour rather than a
  regression; the fix only adds a catch clause for a device failure nobody here can reproduce, and
  the finding is a suggestion raised in the round that was only meant to check the fixes. Cost if
  wrong: on a device that cannot open a 44.1 kHz mono PCM track the app crashes rather than
  starting silently — which is arguably the better of the two anyway.

- Step 3, spec reviewer, `MainActivity.kt:190` and `:240`: `NoiseEngine.stop()` joins the writer
  thread, so the play button, the timer's `onTime` and `onDestroy` now block the main thread for up
  to one write chunk — on the order of 100 ms — where the old `stopNoise()` returned at once.
  Dropped as a code change: the flag-plus-`join()` shape is what phase 2 prescribes and what makes
  the track's lifetime provable, and shortening the chunk trades stop latency for wakeups all
  night. Instead the hammer test in step 4 measures the stop latency, and the PR names it. Cost if
  wrong: a perceptible lag between the tap and the silence on a device with a large minimum buffer.
- Step 3, spec reviewer, `MainActivity.kt:181,190`: the plan said the two private helpers "become
  one engine call each"; the implementer deleted them and inlined the calls. Dropped: the
  done-criterion is about the five call sites, which are intact, and a one-line private wrapper
  around `noiseEngine.start()` is the kind of indirection the repository's own rules cut.

- Step 4, own measurement, then both reviewers: `WRITES_PER_BUFFER` was raised from 2 to 8 when the
  hammer test showed `stop()` blocking its caller for ~190 ms, and has been put back to 2. The
  constant did not move that number, and probing the engine said why: `AudioTrack.stop()` takes 2 ms
  and `release()` 1 ms, while one `write()` takes up to 195 ms for a chunk holding 46 ms of audio —
  that is the emulator's audio sink, not the chunk size. So the change rested on a device-side
  prediction nothing here could test, it reversed the step 3 ruling that had already declined it,
  and it put a production tuning change in the commit that adds a test. Cost if wrong: a device
  where one write really does hold the caller for a whole chunk keeps the longer stop, which the
  parked issue below is about.
- Step 4, spec reviewer, `NoiseEngineHammerTest.kt`: the "settled stop" split was dropped rather
  than kept. With one `write()` running longer than the dwell that was supposed to separate the two
  paths, the split measured the same path twice under two bounds. One measurement, one bound, and
  the number in the log line.

- Step 5, own finding: `docs/code-quality-suggestions.md` is gitignored (`.gitignore:23`), so the
  edits it needed — the line-count table and the fixed `AudioTrack` race item — exist on this
  machine only and reach no reviewer. It is a dated working note rather than repository
  documentation, so nothing is lost; the step's file list named it before that was noticed.

## Parked

- `NoiseEngine.stop()` blocks its caller while the writer finishes the `write()` in flight, and
  `MainActivity` calls it from the main thread on the play button, on the timer's `onTime` and in
  `onDestroy` (`app/src/main/java/ru/pravbeseda/sleepnoise/MainActivity.kt:190`, `:240`). Measured on
  the API 36 emulator over 100 cycles: 176-208 ms. Today's code blocks the main thread there too — it
  calls `AudioTrack.stop()` and `release()` on it — so this is not a regression, but the phase that
  moves playback into a service is where the stop should stop being synchronous.
