package ru.pravbeseda.sleepnoise.widget

import android.app.ActivityManager
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.playback.PlaybackService
import ru.pravbeseda.sleepnoise.settings.settingsRepository
import java.util.concurrent.atomic.AtomicReference

/**
 * The widget as the launcher would draw it: the RemoteViews applied in this process, and a tap on them
 * sending the PendingIntent a launcher would send.
 */
@RunWith(AndroidJUnit4::class)
class PlayWidgetsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val settings = settingsRepository(context)

    @After
    fun leaveAnUntouchedInstall() {
        settings.timerMinutes = 0
    }

    @Test
    fun aStoppedWidgetOffersAStartAndShowsTheChosenTimer() {
        val widget = applied(WidgetFace.stopped(timerMinutes = 90))

        assertEquals(context.getString(R.string.play_button), widget.contentDescription)
        assertEquals(View.VISIBLE, widget.planned.visibility)
        assertEquals("1:30", widget.planned.text.toString())
        assertEquals(View.GONE, widget.countdown.visibility)
    }

    @Test
    fun aPlayingWidgetCountsDownToTheDeadline() {
        val deadline = SystemClock.elapsedRealtime() + NINETY_MINUTES_MILLIS
        val widget = applied(WidgetFace.playing(deadline))

        assertEquals(context.getString(R.string.notification_stop), widget.contentDescription)
        assertEquals(View.GONE, widget.planned.visibility)
        assertEquals(View.VISIBLE, widget.countdown.visibility)
        val countdown = widget.countdown.text.toString()
        assertTrue("a countdown from 1:30:00, read $countdown", countdown.matches(Regex("1:(29|30):\\d\\d")))
    }

    @Test
    fun aPlayingWidgetWithNoTimerShowsNoText() {
        val widget = applied(WidgetFace.playing(deadlineMillis = null))

        assertEquals(View.GONE, widget.planned.visibility)
        assertEquals(View.GONE, widget.countdown.visibility)
    }

    @Test
    fun aTapStartsTheSessionAndASecondTapStopsIt() {
        settings.timerMinutes = 90

        tap(PlayWidgets.face(context))
        awaitPlaying(true)
        tap(PlayWidgets.face(context))
        awaitPlaying(false)
    }

    /**
     * A widget left showing "playing" by a process that died sends its Stop as a foreground start to a service
     * with nothing to stop. Unanswered, Android throws on the main thread as the service is brought down. Under
     * instrumentation that kills the main thread and leaves the process and this test running, so the crash is
     * caught here rather than waited for. The wait is for the bring-down rather than for a fixed time: a first
     * start of the service outlasts any sleep short of a guess.
     */
    @Test
    fun aStopFromAStaleWidgetIsAnsweredWithoutACrash() {
        val crash = AtomicReference<Throwable>()
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            crash.set(error)
            if (thread != Looper.getMainLooper().thread) handler?.uncaughtException(thread, error)
        }
        try {
            tap(WidgetFace.playing(deadlineMillis = null))
            awaitService(running = true)
            awaitService(running = false)
            SystemClock.sleep(CRASH_DELIVERY_MILLIS)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }

        assertNull("the stop crashed the app", crash.get())
        assertFalse(PlayWidgets.face(context).playing)
    }

    private class AppliedWidget(val root: View) {
        val contentDescription: CharSequence? get() = root.contentDescription
        val planned: TextView get() = root.findViewById(R.id.widget_planned)
        val countdown: Chronometer get() = root.findViewById(R.id.widget_countdown)
    }

    private fun applied(face: WidgetFace): AppliedWidget {
        var root: View? = null
        instrumentation.runOnMainSync {
            root = PlayWidgets.render(context, R.layout.widget_button, face).apply(context, FrameLayout(context))
        }
        return AppliedWidget(requireNotNull(root))
    }

    private fun tap(face: WidgetFace) {
        val widget = applied(face)
        instrumentation.runOnMainSync { widget.root.performClick() }
    }

    // Deprecated for other apps' services only: an app is still told about its own.
    @Suppress("DEPRECATION")
    private fun serviceRunning(): Boolean = context.getSystemService(ActivityManager::class.java)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == PlaybackService::class.java.name }

    private fun awaitService(running: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + SERVICE_AWAIT_MILLIS
        while (serviceRunning() != running) {
            if (SystemClock.elapsedRealtime() > deadline) fail("the playback service never read running=$running")
            SystemClock.sleep(SERVICE_POLL_MILLIS)
        }
    }

    private fun awaitPlaying(playing: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + AWAIT_MILLIS
        while (PlayWidgets.face(context).playing != playing) {
            if (SystemClock.elapsedRealtime() > deadline) fail("the widget never read playing=$playing")
            SystemClock.sleep(POLL_MILLIS)
        }
    }

    private companion object {
        const val NINETY_MINUTES_MILLIS = 90 * 60 * 1000L
        const val AWAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
        const val SERVICE_AWAIT_MILLIS = 10_000L
        const val SERVICE_POLL_MILLIS = 10L
        const val CRASH_DELIVERY_MILLIS = 1_000L
    }
}
