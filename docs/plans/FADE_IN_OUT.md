# Fade in and fade out

## Goal

Playback starts by rising from silence and a user stop ends by falling to silence, each over
`FADE_DURATION_MS` (1000 ms), on a quadratic curve. Today the engine starts at full level and cuts
off within one write.

## Decisions

- **Quadratic curve.** The fade keeps a linear progress `p` in `[0, 1]` that moves by
  `1 / fadeSamples` per sample towards its target; the applied gain is `p²`. A linear gain sounds
  abrupt at the quiet end.
- **Per sample, not per chunk.** A chunk is tens of milliseconds; a step per chunk is audible.
- **Reversal is continuous.** A start during a fade-out, or a stop during a fade-in, turns `p`
  around from where it is. Nothing jumps.
- **Which stops fade.** The Stop action and an expired sleep timer fade. Headphones unplugged
  (`BECOMING_NOISY`) and a focus loss (`LOST`, `PAUSE`) cut at once: a second of noise from the
  phone speaker, or over a call, is worse than an abrupt stop. Resuming after `REGAINED` fades in.
- **The service waits for the fade (option 1).** On a faded stop the UI and the listener see
  "stopped" at once; `stopForeground`, `abandon` focus and `stopSelf` run only once the engine
  reports silence. The process stays a foreground service for as long as anything is audible.
- **Volume slider changes stay stepwise.** Out of scope.

## Steps

1. **`media/Fade`** — pure Kotlin, test-first. `fadeIn()`, `fadeOut()`, `isSilent`, and
   `cut()`, `apply(buffer: FloatArray)` multiplying each sample by `p²` while advancing `p`.
   Tests: full ramp takes exactly `fadeSamples`; gain follows `p²`; reversal mid-ramp is continuous;
   `isSilent` only at `p == 0` with target 0; a settled fade leaves samples untouched.
2. **`NoiseMixer`** applies a `Fade` to the mixed sum before the clamp. Test: a fade-in buffer
   starts at silence and ends at the unfaded mix.
3. **`NoiseEngine`**
   - `start()` fades in: from 0 for a new session, from the current level on a stop not yet
     finished.
   - `stop()` fades out and still returns immediately. The writer keeps writing until the fade is
     silent, then checks the state under the lock: `PLAYING` again → carry on in the same session;
     still `STOPPED` → stop the track and call `onFadedOut`.
   - `stopNow()` keeps today's cut.
   - `release()` stays immediate.
   - `onFadedOut` is a constructor callback invoked on the writer thread.
4. **`PlaybackService`**
   - `stopPlayback()` takes a `fade` flag. Faded: timer and ticks cleared, `playing = false`,
     `onPlaybackStopped` sent, `fadingOut = true`, `noiseEngine.stop()`; the teardown waits.
     The noisy receiver stays registered until teardown, so an unplug during the fade cuts at once.
   - `onFadedOut` posts to the main handler; the teardown runs only if `fadingOut` is still set, so a start that arrived in between wins.
   - `startPlayback()` during a fade-out clears the flag, re-reads the volumes and calls
     `noiseEngine.start()`; focus is requested again and the receiver re-registered.
5. **Instrumented test** `NoiseEngineFadeTest`, and the hammer alternates `stop()` and `stopNow()`: `stop()` still returns within
   50 ms, and `onFadedOut` arrives within `FADE_DURATION_MS` plus a margin. The service part stays
   uncovered like the rest of the service; the PR description says so.
6. **Docs** — the Audio and Playback sections of `CLAUDE.md`, including the stale "stop cuts
   within one write" wording.

## Done

`./gradlew spotlessCheck detekt testDebugUnitTest koverVerifyDebug lint` green, plus a by-hand check
on a device: Stop, timer expiry, unplugging headphones mid-fade, Play pressed mid-fade.
