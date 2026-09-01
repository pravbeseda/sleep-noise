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

- [x] 1. Raise `minSdk` 24 → 26 — files: `app/build.gradle.kts`, `README.md`, `CLAUDE.md`,
      `docs/plans/REFACTORING_PLAN.md` — lenses: compatibility — done when: no `minSdk = 24`
      anywhere, the three documents say 26, and `./gradlew assembleDebug lint` is green.
- [x] 2. Extract the countdown arithmetic as pure logic, test-first — files:
      `app/src/main/java/ru/pravbeseda/sleepnoise/timer/SleepTimer.kt`,
      `app/src/test/java/ru/pravbeseda/sleepnoise/timer/SleepTimerTest.kt`,
      `timer/TimerController.kt` — lenses: none — done when: `SleepTimerTest` covers remaining
      time from a deadline, expiry, and both `mm:ss` / `hh:mm:ss` formats, and passes; nothing
      in `SleepTimer` imports `android.*`.
- [x] 3. Add `PlaybackService` that owns the engine, and make `MainActivity` a client of it —
      files: `playback/PlaybackService.kt`, `AndroidManifest.xml`, `MainActivity.kt`,
      `res/drawable/ic_notification.xml`, `res/values*/strings.xml` — lenses: none — done when:
      the service holds the two `NoiseChannel`s and the `NoiseEngine`, runs in the foreground
      with an ongoing notification carrying a Stop action, `MainActivity.onDestroy` no longer
      stops the noise, and the four-command definition of done is green.
- [x] 4. Request `POST_NOTIFICATIONS` on API 33+ — files: `MainActivity.kt`,
      `AndroidManifest.xml` — lenses: security — done when: the permission is requested on the
      first play, a denial still starts playback, and no code path treats the permission as
      required.
- [x] 5. Move the sleep timer into the service — files: `playback/PlaybackService.kt`,
      `MainActivity.kt`, `timer/TimerController.kt` (deleted), `timer/TimerView.kt` — lenses:
      none — done when: the service computes its own deadline with `SleepTimer`, stops itself
      when it expires, updates the notification with the remaining time, pushes ticks to a bound
      Activity, and `TimerController` is gone with no caller left behind.
- [ ] 6. Handle audio focus and headphone unplug — files: `playback/PlaybackService.kt` — lenses:
      security — done when: the service requests focus before playing, stops on
      `AUDIOFOCUS_LOSS`, pauses on `LOSS_TRANSIENT` and resumes after, ducks on
      `LOSS_TRANSIENT_CAN_DUCK`, and a context-registered (never manifest-registered) receiver
      stops playback on `ACTION_AUDIO_BECOMING_NOISY`.
- [ ] 7. Version 1.0.4 with the build number beside it, and the documentation caught up — files:
      `app/version.properties`, `res/values/strings.xml`, `MainActivity.kt`, `CLAUDE.md`,
      `README.md`, `docs/plans/REFACTORING_PLAN.md` — lenses: compatibility — done when
      `version.properties` reads 1.0.4, the main screen shows `Version 1.0.4 (<versionCode>)` from
      `BuildConfig`, and no document still describes playback as living in the Activity.

## Rulings

- Step 1, spec: the diff touched `app/lint-baseline.xml` and renamed `mipmap-anydpi-v26/`, both
  outside the step's file list. Accepted as unavoidable — at minSdk 26 lint's `ObsoleteSdkInt`
  rejects the `-v26` qualifier and `warningsAsErrors` turns that into a failed build, so the
  step's own "lint is green" criterion cannot be met without it. The baseline entries only
  followed the files; the count is unchanged at 26. The PR description states this, because
  `CLAUDE.md` reserves baseline edits for PRs about reducing them.
- Step 1, spec: "the trailing newline added to the two renamed XML files traces to nothing in the
  toolchain" — dropped, the claim is wrong. `./gradlew spotlessCheck` failed on exactly those two
  files (`:spotlessXmlCheck`, "format violations") until the newline was added; Spotless owns XML
  here as well as Kotlin.

## Parked
- Step 2, quality (blocking) and spec: the format tests depended on the machine's default locale
  and would have passed against a `Locale.ROOT` implementation — the exact regression the
  project's Locale convention exists to prevent. Fixed: the tests pin the default locale around
  every assertion and one case asserts that an Arabic-digit locale really changes the output.
- Step 2, spec and quality: `SleepTimer`'s instance API has no production caller yet, and
  `TimerController` still converts minutes to milliseconds inline. Dropped: step 5 moves the
  timer into the service, which calls exactly that API, and deletes `TimerController` along with
  its inline conversion. Removing the class's other half now to re-add it two steps later is
  churn, not simplicity.
- Step 2, spec: `CLAUDE.md` still describes `timer/` as three pieces. Deferred to step 5, where
  `timer/` takes its final shape — the documentation has to be right at the end of the branch,
  and rewriting the same paragraph twice is waste.
- Step 2, spec: the two `MagicNumber` baseline entries for `TimerController` are stale. Step 5
  deletes the file, so they are removed there — a baseline shrinking by two, which the project
  rule welcomes.
- Step 3, quality (blocking): `startPlayback()` returned early when already playing, leaving a
  `startForegroundService()` unanswered by a `startForeground()` — a crash five seconds later,
  reachable by tapping play in the window after `recreate()` before the service state arrives.
  Fixed: the foreground notification is posted before the guard, so every start request is
  answered.
- Step 3, quality: the notification's content intent stacked a second `MainActivity` on the task.
  Fixed with `FLAG_ACTIVITY_SINGLE_TOP`.
- Step 3, quality: "`foregroundServiceType()` duplicates what `ServiceCompat` already does" —
  tried and reverted. Lint rejects naming `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` at minSdk 26
  (`InlinedApi`), so the version branch is what keeps the build green; the reason is now a
  comment on the method.
- Step 3, quality and spec (blocking): the sleep timer stayed in the Activity while playback left
  it, so a `recreate()` orphaned the countdown and a Stop from the notification could crash a
  backgrounded app through `startService`. Not fixed in place: step 5 deletes `TimerController`
  and moves the deadline into the service, which removes both paths rather than guarding them.
  The state exists only between two commits of one branch, never in a release, and the final gate
  re-checks it.
- Step 3, quality: no Robolectric test for the service. Dropped — Robolectric is a new test
  dependency and was ruled out above; the PR description states what is uncovered, which is what
  `CLAUDE.md` requires in that case.
- Step 3, quality: "POST_NOTIFICATIONS is missing" — dropped, the reviewer only saw step 3. Step 4
  adds it in the next commit.
- Step 3, spec: the `onPlaybackStoppedByService` comment claimed the callback only fires for the
  notification's Stop action, while the service fires it on every stop. Fixed: the comment now
  says what the code does.
- Step 3, spec: the volumes had two owners — pushed by the Activity on connect and re-read from
  preferences by the service on start. Fixed: preferences are the single source at start (the
  sliders persist on every move), the binder setters carry only live changes, and the two mirror
  fields in the Activity are gone.
- Step 4, spec: the `checkSelfPermission` gate duplicated what `ActivityResultContracts.RequestPermission`
  already does. Fixed: the helper is the version check and the launch.
- Step 4, spec: "requested on the first play, not on every play" — dropped. Android caps the
  prompt after two denials, so the repeat is invisible, and tracking a first-play flag would add
  state to save a call that does nothing.
- Step 5, spec: `TimerView` was in the step's file list but untouched, and the Activity imported
  `SleepTimer` only to format a string for that view. Fixed: `showCountdown` takes the remaining
  milliseconds and formats them itself, next to the idle time it already formats.
- Step 5, quality: `buildNotification()` rebuilt both `PendingIntent`s on every one-second tick —
  two binder round-trips to system_server per tick, 72 000 of them over a ten-hour timer. Fixed:
  both are `by lazy` fields, since neither ever changes.
- Step 5, quality: `binder.isPlaying && binder.remainingMillis > 0` had an unreachable half —
  `remainingMillis` is non-zero only while playing. Fixed: the guard is the remaining time alone.
- Step 5, spec: the ruling above predicted two stale detekt entries and four were removed
  (14 → 10). The PR description states both counts, as `CLAUDE.md` requires of any baseline edit.
