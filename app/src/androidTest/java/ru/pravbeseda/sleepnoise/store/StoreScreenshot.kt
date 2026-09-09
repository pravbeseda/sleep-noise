package ru.pravbeseda.sleepnoise.store

/**
 * Marks a test that photographs the app for the Google Play listing rather than asserting something about
 * it. Such a test drives the screen through the states the store page shows, in every locale the app ships,
 * and it is minutes of work no pull request needs.
 *
 * The annotation is what keeps the two apart: `app/build.gradle.kts` hands `notAnnotation` to the runner on
 * an ordinary run and `annotation` on a `-PstoreScreenshots` one, so `connectedAndroidTest` means the same
 * thing on a developer's machine as it does on CI, and the store errand is asked for by name.
 *
 * Runtime retention because the runner reads it off the loaded class, not off the source.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class StoreScreenshot
