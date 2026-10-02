package ru.pravbeseda.sleepnoise

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.playback.PlaybackService
import ru.pravbeseda.sleepnoise.settings.APP_PREFS
import ru.pravbeseda.sleepnoise.settings.CURRENT_THEME
import ru.pravbeseda.sleepnoise.settings.settingsRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A theme change mid-playback recreates the Activity, and the new one shows the session from its first
 * frame. The screen is read as the new instance resumes, which is before its first frame is drawn and
 * before any binding of its own could have answered: a service connection is delivered in a later message
 * on the main thread, never inside the one that creates, starts and resumes the Activity.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackRecreateUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
    private val settings = settingsRepository(context)

    /** Granted rather than dismissed: the Activity asks for it on the first play, and the dialog would cover the screen. */
    @Before
    fun prepare() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        settings.timerMinutes = TIMER_MINUTES
    }

    @After
    fun leaveAnUntouchedInstall() {
        preferences.edit(commit = true) { remove(CURRENT_THEME) }
        settings.timerMinutes = 0
    }

    @Test
    fun aThemeChangeMidPlaybackShowsThePauseIconAndTheCountdownFromTheFirstFrame() {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            val idle = screen.read { it.timerLabel().text.toString() }
            screen.onActivity { it.playButton().performClick() }
            try {
                screen.awaitCountdown(idle)
                val before = screen.read { it }

                val firstFrame = captureTheNextResumed(before) {
                    onView(withId(R.id.theme_button)).perform(click())
                }

                assertTrue("the pause icon on the recreated screen", firstFrame.showsPause)
                assertFalse("the play icon on the recreated screen", firstFrame.showsPlay)
                assertNotEquals("the timer label on the recreated screen", idle, firstFrame.label)
                assertEquals("the timer seekbar on the recreated screen", View.INVISIBLE, firstFrame.seekBarVisibility)
            } finally {
                // Sent rather than pressed: the button stops only if the screen it sits on is right, which is what is under test.
                screen.onActivity { it.startService(Intent(it, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP)) }
                instrumentation.waitForIdleSync()
            }
        }
    }

    private class FirstFrame(val showsPause: Boolean, val showsPlay: Boolean, val label: String, val seekBarVisibility: Int)

    /** Reads the first `MainActivity` other than [previous] to resume after [action], on the main thread as it resumes. */
    private fun captureTheNextResumed(previous: Activity, action: () -> Unit): FirstFrame {
        val application = context.applicationContext as Application
        val resumed = CountDownLatch(1)
        var captured: FirstFrame? = null
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity !is MainActivity || activity === previous || captured != null) return
                captured = FirstFrame(
                    showsPause = activity.playButtonShows(R.drawable.ic_pause),
                    showsPlay = activity.playButtonShows(R.drawable.ic_play),
                    label = activity.timerLabel().text.toString(),
                    seekBarVisibility = activity.findViewById<SeekBar>(R.id.timerSeekBar).visibility,
                )
                resumed.countDown()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        try {
            action()
            assertTrue(
                "no recreated screen resumed within $RECREATE_TIMEOUT_SECONDS s",
                resumed.await(RECREATE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
        return requireNotNull(captured)
    }

    /** Compared as pictures, since the button keeps no record of which resource it was last given. */
    private fun MainActivity.playButtonShows(icon: Int): Boolean {
        val shown = playButton().drawable.toBitmap()
        val expected = requireNotNull(AppCompatResources.getDrawable(this, icon)).toBitmap()
        return shown.sameAs(expected)
    }

    /** The countdown replaces the timer's own value, so a label that still reads it is a session not started. */
    private fun ActivityScenario<MainActivity>.awaitCountdown(idle: String) {
        repeat(TICK_ATTEMPTS) {
            if (read { it.timerLabel().text.toString() } != idle) return
            Thread.sleep(TICK_POLL_MILLIS)
        }
        fail("the countdown never started: the timer still reads $idle")
    }

    private fun MainActivity.timerLabel(): TextView = findViewById(R.id.timerTextView)

    private fun MainActivity.playButton(): ImageButton = findViewById(R.id.playButton)

    private companion object {
        const val TIMER_MINUTES = 30
        const val TICK_ATTEMPTS = 20
        const val TICK_POLL_MILLIS = 250L
        const val RECREATE_TIMEOUT_SECONDS = 10L
    }
}
