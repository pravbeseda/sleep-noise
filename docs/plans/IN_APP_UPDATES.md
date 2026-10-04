# In-app updates

Offer a newer version from inside the app through Google Play's In-App Updates API, so installs with
auto-update switched off do not stay on an old build unaware.

## Decisions

- **Flexible flow only.** The update priority Play can carry is ignored and `release.yml` sends none.
  The app is offline with no server or account, so no old version is dangerous enough to block the screen;
  an immediate flow can be added later in a PR of its own.
- **Asked once per version.** A dismissed offer stores the declined `versionCode`; the next offer comes
  only when Play reports a higher one. A failed flow (`RESULT_IN_APP_UPDATE_FAILED`) is not a dismissal.
- **Offered only while nothing plays**, on the same gate as the rating prompt: `PlaybackState.confirmed`
  and not `playing`.
- **Installed only on the user's tap.** Once the download finishes, a Snackbar "Update downloaded —
  Restart" calls `completeUpdate()`. It is shown only while nothing plays, and again on every screen
  open while the install status stays `DOWNLOADED`. No silent install in the background: a wrong
  "not playing" check there would cut the noise for someone falling asleep.
- **The update offer wins over the rating prompt** on the same screen open: two Play dialogs in a row
  is one too many, and the rating prompt keeps its chance for the next open.

## Steps

1. **Dependency.** Add `com.google.android.play:app-update-ktx` to `gradle/libs.versions.toml` and `:app`,
   beside `review-ktx`.
2. **Pure policy, test-first.** `update/UpdatePolicy.kt` with `updateAction(offerable, availableVersionCode,
   declinedVersionCode, install)` answering offer, restart or nothing; `UpdatePolicyTest` on the JVM. Add the file to `AndroidFreeSourcesTest` and to the Kover filter in `app/build.gradle.kts`.
3. **Setting.** `DECLINED_UPDATE_VERSION` in `settings/AppPreferences.kt` and `declinedUpdateVersion` in
   `SettingsRepository`, tested in `SettingsRepositoryTest`.
4. **Plumbing.** `update/UpdatePrompt` (shaped like `review/ReviewPrompt`): takes an `AppUpdateManager`
   as a constructor parameter, starts the flow through `startUpdateFlow`, whose task carries the result code,
   records a dismissal, listens to install state while the Activity is started, and shows the Snackbar.
   `MainActivity` calls it where it calls `reviewPrompt.askIfDue()` and skips the rating prompt when an
   offer went out.
5. **Strings.** The Snackbar text and the "Restart" action in all six locales.
6. **Instrumented test.** `UpdatePromptTest` drives `FakeAppUpdateManager` (shipped in the library, not
   a mock): an available update is offered when stopped and not while playing; a dismissed version is not
   offered again; a finished download shows the Snackbar and its action calls `completeUpdate()`.
7. **Manual check on Play.** Two builds with rising `versionCode` through internal app sharing: the offer
   appears, the download runs, the restart installs.
8. **Docs.** An "Update prompt" section in `AGENTS.md` beside "Rating prompt"; the new file in the floor
   lists of `AGENTS.md` and `README.md`; `update/` in the README package tree.

## Done when

`./gradlew spotlessCheck detekt testDebugUnitTest koverVerifyDebug lint` and
`./gradlew connectedDebugAndroidTest` are green, and step 7 was seen working.
