package ru.pravbeseda.sleepnoise.widget

import android.app.ActivityManager
import android.appwidget.AppWidgetManager
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
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
        val widget = applied(WidgetFace.playing(deadline, NINETY_MINUTES_MILLIS))

        assertEquals(context.getString(R.string.notification_stop), widget.contentDescription)
        assertEquals(View.GONE, widget.planned.visibility)
        assertEquals(View.VISIBLE, widget.countdown.visibility)
        val countdown = widget.countdown.text.toString()
        assertTrue("a countdown from 1:30:00, read $countdown", countdown.matches(Regex("1:(29|30):\\d\\d")))
    }

    @Test
    fun aPlayingWidgetWithNoTimerShowsNoText() {
        val widget = applied(WidgetFace.playing(deadlineMillis = null, durationMillis = null))

        assertEquals(View.GONE, widget.planned.visibility)
        assertEquals(View.GONE, widget.countdown.visibility)
    }

    /** A layout that fails to apply is a widget the launcher shows as "Can't load widget". */
    @Test
    fun everyStyleDrawsBothFaces() {
        val styles = installedStyles()
        assertTrue("no widget styles in the manifest", styles.isNotEmpty())
        val deadline = SystemClock.elapsedRealtime() + NINETY_MINUTES_MILLIS
        styles.forEach { (name, style) ->
            val stopped = applied(WidgetFace.stopped(timerMinutes = 90), style)
            assertEquals("$name stopped", context.getString(R.string.play_button), stopped.contentDescription)
            val playing = applied(WidgetFace.playing(deadline, NINETY_MINUTES_MILLIS), style)
            assertEquals("$name playing", context.getString(R.string.notification_stop), playing.contentDescription)
            stopped.plannedOrNull?.let { assertEquals("$name planned timer", "1:30", it.text.toString()) }
            playing.countdownOrNull?.let { assertEquals("$name countdown", View.VISIBLE, it.visibility) }
        }
    }

    @Test
    fun theTimerRingShowsTheShareOfTheTimerLeft() {
        val halfwayDeadline = SystemClock.elapsedRealtime() + NINETY_MINUTES_MILLIS / 2

        assertEquals(0, ringLevel(WidgetFace.stopped(timerMinutes = 90)))
        assertEquals(FULL_LEVEL, ringLevel(WidgetFace.playing(deadlineMillis = null, durationMillis = null)))
        val halfway = ringLevel(WidgetFace.playing(halfwayDeadline, NINETY_MINUTES_MILLIS))
        assertTrue("half the ring at half the timer, read $halfway", halfway in FULL_LEVEL / 2 - LEVEL_SLACK..FULL_LEVEL / 2)
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
            tap(WidgetFace.playing(deadlineMillis = null, durationMillis = null))
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

        // A style may leave the timer out of its layout.
        val plannedOrNull: TextView? get() = root.findViewById(R.id.widget_planned)
        val countdownOrNull: Chronometer? get() = root.findViewById(R.id.widget_countdown)
    }

    private fun applied(face: WidgetFace, style: PlayWidgetStyle = ButtonWidget().style): AppliedWidget {
        var root: View? = null
        instrumentation.runOnMainSync {
            root = PlayWidgets.render(context, style, face).apply(context, FrameLayout(context))
        }
        return AppliedWidget(requireNotNull(root))
    }

    /** Read off the manifest, as `PlayWidgets.refresh` reads them, so a style registered there is a style tested here. */
    private fun installedStyles(): List<Pair<String, PlayWidgetStyle>> =
        AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null).map { info ->
            val provider = Class.forName(info.provider.className).getDeclaredConstructor().newInstance() as PlayWidgetProvider
            info.provider.shortClassName to provider.style
        }

    private fun ringLevel(face: WidgetFace): Int = applied(face, TimerRingWidget().style).root
        .findViewById<ImageView>(R.id.widget_ring).drawable.level

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
        const val FULL_LEVEL = 10_000

        // The test's own few milliseconds between computing the deadline and drawing it.
        const val LEVEL_SLACK = 10
        const val AWAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
        const val SERVICE_AWAIT_MILLIS = 10_000L
        const val SERVICE_POLL_MILLIS = 10L
        const val CRASH_DELIVERY_MILLIS = 1_000L
    }
}
