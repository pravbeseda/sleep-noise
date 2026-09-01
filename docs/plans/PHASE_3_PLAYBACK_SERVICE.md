# Phase 3 — Foreground playback service (issue #1)

## Goal

Playback and the sleep timer survive the Activity, so "switch it on and put the phone down for
the night" actually works. Implements phase 3 of `docs/plans/REFACTORING_PLAN.md` and closes
issue #1.

## Decisions

- Service shape → a plain `Service` with `foregroundServiceType="mediaPlayback"` and its own
  notification, not media3's `MediaSessionService`, because media3 wants a `Player`
  implementation and this app plays a generated `AudioTrack`, not a media item. Lock-screen and
  headset-button controls are deliberately out of scope; they can arrive later as their own PR.
- Audio focus dependency → none. `minSdk` goes 24 → 26 instead, which makes `AudioFocusRequest`
  and `NotificationChannel` available directly and keeps `androidx.media:media` off the
  classpath. Cost: Android 7.x devices stop receiving updates (~1% of the install base, and they
  keep the version they already have).
- Sleep timer mechanism → a deadline in `SystemClock.elapsedRealtime()` plus a `Handler` inside
  the service, **not** the `AlarmManager.setExactAndAllowWhileIdle` the refactoring plan named.
  With `targetSdk` 36 an exact alarm needs `SCHEDULE_EXACT_ALARM`, which Android 14+ does not
  grant on install, and the `USE_EXACT_ALARM` alternative is reserved by Play policy for alarm
  clocks. While a foreground service is really playing audio the process is alive and not dozing,
  so the case the alarm would rescue cannot happen: if the process dies, so does the noise the
  timer exists to stop.
- Task removal → playback continues when the app is swiped away. The notification's Stop action
  is how a session ends; that is the whole point of the issue.
- Notification permission denial → playback still starts. On API 33+ the ongoing notification is
  simply not shown; the foreground service runs regardless.
- Robolectric → not added. The service is Android plumbing and a new test dependency is a
  separate decision; the service's behaviour is covered by the manual smoke test in the PR
  description, and the PR says so.
- Version → `1.0.4`, displayed as `Version 1.0.4 (128)`. `versionCode` stays derived from the
  commit count. Bumping it here breaks the "release-PR territory" rule in `CLAUDE.md`, and does
  it because the maintainer asked for it in the same run.
- New user-facing strings are translated into all six supported locales in the same commit,
  rather than landing English-only under `values/`.

## Steps

- [ ] 1. Raise `minSdk` 24 → 26 — files: `app/build.gradle.kts`, `README.md`, `CLAUDE.md`,
      `docs/plans/REFACTORING_PLAN.md` — lenses: compatibility — done when: no `minSdk = 24`
      anywhere, the three documents say 26, and `./gradlew assembleDebug lint` is green.
- [ ] 2. Extract the countdown arithmetic as pure logic, test-first — files:
      `app/src/main/java/ru/pravbeseda/sleepnoise/timer/SleepTimer.kt`,
      `app/src/test/java/ru/pravbeseda/sleepnoise/timer/SleepTimerTest.kt`,
      `timer/TimerController.kt` — lenses: none — done when: `SleepTimerTest` covers remaining
      time from a deadline, expiry, and both `mm:ss` / `hh:mm:ss` formats, and passes; nothing
      in `SleepTimer` imports `android.*`.
- [ ] 3. Add `PlaybackService` that owns the engine, and make `MainActivity` a client of it —
      files: `playback/PlaybackService.kt`, `AndroidManifest.xml`, `MainActivity.kt`,
      `res/drawable/ic_notification.xml`, `res/values*/strings.xml` — lenses: none — done when:
      the service holds the two `NoiseChannel`s and the `NoiseEngine`, runs in the foreground
      with an ongoing notification carrying a Stop action, `MainActivity.onDestroy` no longer
      stops the noise, and the four-command definition of done is green.
- [ ] 4. Request `POST_NOTIFICATIONS` on API 33+ — files: `MainActivity.kt`,
      `AndroidManifest.xml` — lenses: security — done when: the permission is requested on the
      first play, a denial still starts playback, and no code path treats the permission as
      required.
- [ ] 5. Move the sleep timer into the service — files: `playback/PlaybackService.kt`,
      `MainActivity.kt`, `timer/TimerController.kt` (deleted), `timer/TimerView.kt` — lenses:
      none — done when: the service computes its own deadline with `SleepTimer`, stops itself
      when it expires, updates the notification with the remaining time, pushes ticks to a bound
      Activity, and `TimerController` is gone with no caller left behind.
- [ ] 6. Handle audio focus and headphone unplug — files: `playback/PlaybackService.kt` — lenses:
      security — done when: the service requests focus before playing, stops on
      `AUDIOFOCUS_LOSS`, pauses on `LOSS_TRANSIENT` and resumes after, ducks on
      `LOSS_TRANSIENT_CAN_DUCK`, and a context-registered (never manifest-registered) receiver
      stops playback on `ACTION_AUDIO_BECOMING_NOISY`.
- [ ] 7. Version 1.0.4 with the build number beside it — files: `app/version.properties`,
      `res/values/strings.xml`, `MainActivity.kt` — lenses: compatibility — done when
      `version.properties` reads 1.0.4 and the main screen shows `Version 1.0.4 (<versionCode>)`
      from `BuildConfig`.

## Rulings

## Parked
