package ru.pravbeseda.sleepnoise.store

import android.Manifest
import android.app.LocaleManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.APP_PREFS
import ru.pravbeseda.sleepnoise.CURRENT_LANGUAGE
import ru.pravbeseda.sleepnoise.CURRENT_THEME
import ru.pravbeseda.sleepnoise.MainActivity
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.media.BROWN_NOISE
import ru.pravbeseda.sleepnoise.media.GREEN_NOISE
import ru.pravbeseda.sleepnoise.media.NOISE_LAB_ENABLED
import ru.pravbeseda.sleepnoise.media.PINK_NOISE
import ru.pravbeseda.sleepnoise.media.SHIPPING_NOISES
import ru.pravbeseda.sleepnoise.media.SURF_NOISE
import ru.pravbeseda.sleepnoise.media.ShippingNoise
import ru.pravbeseda.sleepnoise.models.AppTheme
import ru.pravbeseda.sleepnoise.timer.TimerPreferences
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The phone screenshots of the Play listing, taken from the running app rather than from a designer's
 * mock-up: three states per locale, six locales, into the very directories Gradle Play Publisher reads
 * (`app/src/main/play/listings/<play locale>/graphics/phone-screenshots/`). The workflow that dispatches
 * this test pulls them off the device and pushes them as a branch — see `.github/workflows/screenshots.yml`.
 *
 * It is a store errand, not a test of the app: nothing here would tell a pull request that something broke,
 * and running it costs minutes and a foreground service playing real audio. [StoreScreenshot] is how it
 * stays out of an ordinary `connectedAndroidTest` run.
 *
 * Three things it does assert, because each of them is a way the run produces images that look fine and are
 * useless: that the app really is in the locale it is being photographed in — an English screenshot filed
 * under `ru-RU` is worse than none; that Play would accept the geometry, which is a property of the device
 * profile the run was given rather than of this code; and that the noise lab is off, since its rows carry
 * English labels no locale translates.
 *
 * The window is copied with [PixelCopy] rather than photographed with `UiAutomation.takeScreenshot`, which
 * takes the whole display. The app draws edge to edge, so its own window already covers the screen: copying
 * it gives the same picture with the emulator's clock, battery and navigation buttons left out, and asks
 * nothing of the system UI the image would otherwise have to be cleaned up for.
 */
@RunWith(AndroidJUnit4::class)
@StoreScreenshot
class StoreScreenshotTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    private val timerPreferences = TimerPreferences(context)

    /**
     * A store screenshot is staged, and this is the staging: an untouched install has one noise at 30 % and
     * five at zero, which photographs as a screen nobody is using.
     */
    @Before
    fun stageTheMix() {
        assertFalse(
            "the noise lab is switched on, and its rows are labelled in English only — no store page should show them",
            NOISE_LAB_ENABLED,
        )
        grantNotificationPermission()
        preferences.edit(commit = true) {
            SHIPPING_NOISES.forEach { noise ->
                putFloat(noise.volumeKey, STAGED_MIX[noise] ?: SILENT)
                putBoolean(noise.enabledKey, true)
            }
        }
        timerPreferences.saveTimerValue(TIMER_MINUTES)
    }

    /** The device is a shared machine often enough — the levels this test writes are its own to take back. */
    @After
    fun forgetTheStaging() {
        preferences.edit(commit = true) {
            SHIPPING_NOISES.forEach { noise ->
                remove(noise.volumeKey)
                remove(noise.enabledKey)
            }
            remove(CURRENT_LANGUAGE)
            remove(CURRENT_THEME)
        }
        timerPreferences.saveTimerValue(0)
        // The per-app locale belongs to the framework rather than to these preferences, so removing the
        // key leaves the device in whichever language was photographed last.
        applyLocale(SYSTEM_LOCALE)
    }

    @Test
    fun everyLocaleGetsItsOwnSetOfStoreScreenshots() {
        val root = File(context.getExternalFilesDir(null), SCREENSHOT_DIR)
        assertTrue("the screenshots of the last run could not be cleared out of $root", !root.exists() || root.deleteRecursively())

        PLAY_LOCALES.forEach { (language, playLocale) ->
            val into = File(root, playLocale)
            assertTrue("$into could not be created", into.mkdirs())

            useTheApp(language, AppTheme.PURPLE) { screen ->
                screen.capture(File(into, MIXER_SHOT))
                screen.whilePlaying { screen.capture(File(into, TIMER_SHOT)) }
            }
            useTheApp(language, AppTheme.DARK) { screen ->
                screen.capture(File(into, THEME_SHOT))
            }
        }

        val taken = root.walkTopDown().count { it.extension == "jpg" }
        assertEquals("screenshots taken under $root", PLAY_LOCALES.size * SHOTS_PER_LOCALE, taken)
    }

    /**
     * Opens the app the way a user of that language and theme opens it: both are preferences the Activity
     * reads in `onCreate`, so they are written before the launch rather than switched afterwards through the
     * menu, which would photograph the dialog that switched them.
     */
    private fun useTheApp(language: String, theme: AppTheme, body: (ActivityScenario<MainActivity>) -> Unit) {
        preferences.edit(commit = true) {
            putString(CURRENT_LANGUAGE, language)
            putString(CURRENT_THEME, theme.key)
        }
        // Before the launch, so that the Activity comes up in the language rather than being recreated into
        // it from inside its own onCreate.
        applyLocale(language)
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            screen.awaitLanguage(language)
            screen.hideScrollbars()
            body(screen)
        }
    }

    /**
     * Who owns a per-app locale changes at API 33, and so does the way in. Below it AppCompat holds the
     * locale itself and its own call is the only door. From 33 the framework holds it, and AppCompat's call
     * reaches it through a context it picks up from a running Activity: asked before the first launch it
     * stored nothing at all — measured on an API 36 emulator, where `getApplicationLocales` came back empty
     * and every locale was photographed in English. So the framework is asked directly.
     */
    private fun applyLocale(language: String) {
        // An empty tag list is how both of them spell "follow the system", which is what the test hands
        // the device back.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
        } else {
            instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
        }
    }

    /**
     * The locale is applied asynchronously either way, so the screen is asked what language it came up in and
     * given [LANGUAGE_ATTEMPTS] recreations to come up in the right one.
     */
    private fun ActivityScenario<MainActivity>.awaitLanguage(language: String) {
        repeat(LANGUAGE_ATTEMPTS) {
            if (read { it.getString(R.string.lang) } == language) return
            Thread.sleep(SETTLE_MILLIS)
            recreate()
        }
        fail(
            "the screen is in ${read { it.getString(R.string.lang) }} and was asked for $language; " +
                "its own configuration says ${read { it.resources.configuration.locales.toLanguageTags() }}",
        )
    }

    /**
     * The framework flashes a scrollbar when a scrolling view is laid out, and it is still on screen when
     * the picture is taken: a store image with a grey sliver down its edge reads as a photograph of a
     * developer's phone. Every other thing the screen shows is the app's own doing and stays.
     */
    private fun ActivityScenario<MainActivity>.hideScrollbars() {
        onActivity { screen ->
            SCROLLING_VIEWS.forEach {
                val scroll = screen.findViewById<View>(it)
                scroll.isVerticalScrollBarEnabled = false
                // The flag decides what the next draw paints, and nothing here has asked for one.
                scroll.invalidate()
            }
        }
    }

    /**
     * Presses play, waits for the service's first tick to reach the timer, and presses it again afterwards.
     * The tick is what the second screenshot is for: the seekbar gives way to a countdown, which is the one
     * state of this screen a still picture can show that the first one cannot.
     */
    private fun ActivityScenario<MainActivity>.whilePlaying(body: () -> Unit) {
        val idle = read { it.timerLabel().text.toString() }
        onActivity { it.playButton().performClick() }
        try {
            awaitCountdown(idle)
            body()
        } finally {
            // In a finally because the button is the only way to stop the service from here, and a
            // failure between the two presses would otherwise leave the emulator playing.
            onActivity { it.playButton().performClick() }
            instrumentation.waitForIdleSync()
        }
    }

    /** The countdown replaces the timer's own value, so a label that still reads it is a session not started. */
    private fun ActivityScenario<MainActivity>.awaitCountdown(idle: String) {
        repeat(TICK_ATTEMPTS) {
            if (read { it.timerLabel().text.toString() } != idle) return
            Thread.sleep(SETTLE_MILLIS)
        }
        fail("the countdown never started: the timer still reads $idle")
    }

    private fun ActivityScenario<MainActivity>.capture(into: File) {
        // PixelCopy reads the surface the render thread last drew into, and waitForIdleSync answers for the
        // main thread only: without the pause the picture can be one frame behind what the screen shows.
        instrumentation.waitForIdleSync()
        Thread.sleep(SETTLE_MILLIS)
        val decor = read { it.window.decorView }
        val shot = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        copyWindow(read { it.window }, shot)
        assertPlayWouldTake(shot, into.name)
        into.outputStream().use {
            assertTrue("${into.name} could not be encoded", shot.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it))
        }
    }

    /** The callback lands on the main thread, so this one waits on the test thread and must not be called from it. */
    private fun copyWindow(window: Window, into: Bitmap) {
        val copied = CountDownLatch(1)
        var outcome = PixelCopy.ERROR_UNKNOWN
        PixelCopy.request(window, into, { result ->
            outcome = result
            copied.countDown()
        }, Handler(Looper.getMainLooper()))
        assertTrue("PixelCopy did not answer within $COPY_TIMEOUT_SECONDS s", copied.await(COPY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals("PixelCopy returned an error rather than a picture", PixelCopy.SUCCESS, outcome)
    }

    /**
     * Play refuses a screenshot with a side under 320 px or a ratio past 2:1, and both are decided by the
     * device the run was given rather than by anything here: a 1080x2400 phone profile is 2.22:1 and every
     * image of it would be rejected at upload, long after the run reported green.
     */
    private fun assertPlayWouldTake(shot: Bitmap, name: String) {
        val shortest = minOf(shot.width, shot.height)
        val longest = maxOf(shot.width, shot.height)
        val size = "${shot.width}x${shot.height}"
        assertTrue("$name is $size, and Play takes no side under $MIN_SIDE_PX px", shortest >= MIN_SIDE_PX)
        assertTrue(
            "$name is $size, a ratio past $MAX_RATIO:1 that Play refuses — run this on a 16:9 device profile",
            longest <= shortest * MAX_RATIO,
        )
    }

    private fun <T : Any> ActivityScenario<MainActivity>.read(of: (MainActivity) -> T): T {
        var value: T? = null
        onActivity { value = of(it) }
        return requireNotNull(value) { "the screen answered with nothing" }
    }

    private fun MainActivity.timerLabel(): TextView = findViewById(R.id.timerTextView)

    private fun MainActivity.playButton(): ImageButton = findViewById(R.id.playButton)

    /**
     * Granted rather than dismissed: the Activity asks for it on the first play, and the system dialog that
     * would open sits over the screenshot the run is there to take.
     */
    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        /**
         * The app's locale codes against Play's, which spell four of the six differently. The other copy of
         * this table is `PlayMetadataTest.PLAY_LOCALES`, and the workflow refuses to file a directory whose
         * name matches no listing — which is what a language added to one table and not the other looks like.
         */
        val PLAY_LOCALES = mapOf(
            "en" to "en-US",
            "ar" to "ar",
            "de" to "de-DE",
            "es" to "es-ES",
            "ru" to "ru-RU",
            "uk" to "uk",
        )

        /**
         * What the sliders show: four noises at levels somebody might actually sleep to, and the two left out
         * sitting at zero, so the picture shows both halves of what the toggles do.
         */
        val STAGED_MIX: Map<ShippingNoise, Float> = mapOf(
            BROWN_NOISE to 0.55f,
            PINK_NOISE to 0.35f,
            SURF_NOISE to 0.45f,
            GREEN_NOISE to 0.25f,
        )

        const val SILENT = 0f

        /** What both locale APIs read as "follow the system", and what the device is left holding. */
        const val SYSTEM_LOCALE = ""

        /** An hour and a half: long enough that the countdown reads hh:mm:ss and is told apart from the idle value. */
        const val TIMER_MINUTES = 90

        /** Under the app's own external files directory, which is where the workflow pulls from. */
        const val SCREENSHOT_DIR = "play-screenshots"

        /** The two views that scroll, and so the two that draw a scrollbar over the picture. */
        val SCROLLING_VIEWS = listOf(R.id.contentScroll, R.id.noiseScroll)

        // Play orders a listing's screenshots by file name, so the order they are shown in is written into it.
        const val MIXER_SHOT = "1-mixer.jpg"
        const val TIMER_SHOT = "2-timer.jpg"
        const val THEME_SHOT = "3-theme.jpg"
        const val SHOTS_PER_LOCALE = 3

        /**
         * JPEG rather than PNG, which Play and the publisher both take. This screen is a dithered gradient
         * behind flat text, which is the shape PNG stores worst: the same picture came to 1.5 MB losslessly
         * and 208 KB at this quality, indistinguishable side by side, and every refresh of the screenshots
         * carries its whole weight into the repository's history for good.
         */
        const val JPEG_QUALITY = 92
        const val MIN_SIDE_PX = 320
        const val MAX_RATIO = 2
        const val COPY_TIMEOUT_SECONDS = 5L

        const val LANGUAGE_ATTEMPTS = 5
        const val TICK_ATTEMPTS = 20
        const val SETTLE_MILLIS = 500L
    }
}
