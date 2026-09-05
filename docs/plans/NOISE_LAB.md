# Noise lab: two experimental noise sources behind a flag

## Goal
Even a low white-noise setting drowns the brown channel, because `media/BrownNoise` is a clamped
random walk whose energy sits around 3 Hz — below what any phone speaker reproduces. Add two
candidate sources (pink, and brown with an audible corner frequency) on their own sliders so the
balance can be judged by ear, side by side with the two shipping channels. The candidates stay in
the code behind one flag when the comparison is over, so trying another idea later is a rebuild
rather than a re-implementation.

## Diagnosis (measured from the code, not assumed)
`BrownNoise.fill` walks `lastOut += 0.02 * uniform(-1, 1)` and clamps to +-1.

- step RMS = `0.02 * 0.577` = 0.0115 per sample
- crossing the whole +-1 range takes `(1 / 0.0115)^2` ~= 7500 samples ~= 0.17 s at 44.1 kHz
- so the walk's corner is ~3 Hz and its spectrum is 1/f^2 above that

For 1/f^2 noise of total power P with corner fc, the density at f >> fc is `2 * P * fc / (pi * f^2)`:
audible-band energy is proportional to `P * fc`. With fc at 3 Hz, nearly all of P is spent on a
subsonic wander the speaker throws away. Raising the corner into the audible range — not adding
gain, which only clips — is the lever.

## Decisions
- Two candidates, not a rewrite of the shipping sources — `media/WhiteNoise` and `media/BrownNoise`
  are not touched, so the comparison is against today's app rather than against a memory of it.
- Candidates are registered in one list (`media/NoiseLab`), and the sliders are built from that
  list, so adding a third candidate later is one entry plus one source class.
- Lab volumes persist in `APP_PREFS` under their own keys, like the shipping sliders, and default
  to 0 — an existing install sounds exactly as it does today until a slider is moved.
- Lab slider labels are `translatable="false"`, the pattern the project already uses for strings
  that are not user-facing product copy.
- Gating is one explicit compile-time constant, `NOISE_LAB_ENABLED`, declared in `media/NoiseLab.kt`
  rather than in `MainActivity.kt` as first written — not an environment variable, not `BuildConfig.DEBUG`, not a
  runtime setting. Turning the lab off is editing `true` to `false`: a `const val` is inlined, so
  R8 drops the whole branch from a release build while the sources, channels and preference keys
  stay in the tree for the next experiment. The comparison has to be possible on the alpha build
  the phone actually runs at night, which a `BuildConfig.DEBUG` gate would have prevented.
  The constant sits in `media/` because `PlaybackService` needs it as much as the Activity does: a
  flag the UI alone honoured would leave a stored lab volume playing with no slider to turn it down.

## Steps
- [x] 1. `media/PinkNoise` — 1/f source (Paul Kellett filter), test-first — files:
      `app/src/main/java/ru/pravbeseda/sleepnoise/media/PinkNoise.kt`,
      `app/src/test/java/ru/pravbeseda/sleepnoise/media/PinkNoiseTest.kt` — lenses: none —
      done when: `PinkNoiseTest` is green, asserting samples stay in `[-1, 1]`, that `reset()`
      returns the filter to silence, and that its low/high band energy ratio is above `WhiteNoise`'s
      on the same seed.
- [x] 2. `media/LeakyBrownNoise` — one-pole low-pass on white at a named cutoff, normalised — files:
      `app/src/main/java/ru/pravbeseda/sleepnoise/media/LeakyBrownNoise.kt`,
      `app/src/test/java/ru/pravbeseda/sleepnoise/media/LeakyBrownNoiseTest.kt` — lenses: none —
      done when: `LeakyBrownNoiseTest` is green, asserting range, `reset()`, and — the point of the
      whole change — that its energy above the band split is higher than `BrownNoise`'s at the same
      peak level.
- [x] 3. `media/NoiseLab` registry and the service wiring — files:
      `app/src/main/java/ru/pravbeseda/sleepnoise/media/NoiseLab.kt`,
      `app/src/test/java/ru/pravbeseda/sleepnoise/media/NoiseLabTest.kt`,
      `app/src/main/java/ru/pravbeseda/sleepnoise/playback/PlaybackService.kt` —
      lenses: compatibility — done when: `NoiseLabTest` is green (each candidate carries a distinct
      preference key and builds its own source) and `PlaybackService` runs its engine over the two
      shipping channels plus every lab channel, reading each one's stored volume at start.
- [ ] 4. The lab sliders, behind the flag — files:
      `app/src/main/res/layout/activity_main.xml`,
      `app/src/main/res/values/strings.xml`,
      `app/src/main/java/ru/pravbeseda/sleepnoise/MainActivity.kt` — lenses: none —
      done when: with the flag on, the screen shows one labelled slider per lab candidate under the
      shipping pair, each moving its channel live and persisting on release; with the flag off, the
      container is gone and the layout is byte-for-byte the one shipping today.
- [ ] 5. Instrumented cover for step 4 and the definition of done — files:
      `app/src/androidTest/java/ru/pravbeseda/sleepnoise/NoiseLabUiTest.kt` — lenses: none —
      done when: `./gradlew spotlessCheck detekt testDebugUnitTest koverVerifyDebug lint` is green
      and the Espresso test asserts one slider per registered candidate. If the test proves flaky or
      unexpressible, it is dropped with a ruling and the PR description names the uncovered
      behaviour, which CLAUDE.md allows and silence does not.

## Rulings
- Step 1, quality reviewer, suggestion: replace `RewindableRandom` in `PinkNoiseTest` with a
  constant-input `Random` (the `AlwaysMaxRandom` shape `BrownNoiseTest` uses). Dropped. The reviewer
  itself put it as the author's call, and it detects no defect the current test misses — a rewound
  seeded stream exercises `reset()` against a live signal, where a constant input drives every
  section to the same steady value. Cost if wrong: one test class carries five lines it could have
  done without.
- Step 2, spec reviewer, suggestion: `LeakyBrownNoise`'s `cutoffHz` constructor parameter is not
  literally asked for by the step, whose wording is "at a named cutoff". Dropped. The corner
  frequency is the one value this whole change exists to try by ear, the Goal section says the next
  experiment should be a rebuild rather than a re-implementation, and a test exercises two values —
  so it is not configurability without a caller. Cost if wrong: one unused default parameter.
- Step 2, second round, suggestion: move the one-line `rms()` helper into the shared test file the
  way `RewindableRandom` was moved. Dropped. `RewindableRandom` was nine duplicated lines; `rms` is
  one, and giving it a home of its own costs a file and a doc comment to save that line. The two
  band-energy helpers next to it are deliberately not shared either — they answer different
  questions (a low/high ratio against `WhiteNoise`, versus high-band energy at unit peak). Cost if
  wrong: one line written twice in the test sources.

## Parked
