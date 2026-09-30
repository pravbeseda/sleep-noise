package ru.pravbeseda.sleepnoise

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.timer.TimerPreferences
import ru.pravbeseda.sleepnoise.timer.TimerView
import java.util.Locale

/**
 * The timer view stores nothing: the screen hands it the stored minutes and writes back what the user
 * picks. Nothing here presses play, so the countdown is shown on the view directly rather than by a
 * running service.
 */
@RunWith(AndroidJUnit4::class)
class TimerUiTest {
    private val timerPreferences = TimerPreferences(InstrumentationRegistry.getInstrumentation().targetContext)

    /** The test writes the real timer preference on the device, so it takes it back out again. */
    @After
    fun forgetTheTimer() = timerPreferences.saveTimerValue(0)

    @Test
    fun theStoredMinutesAreShownAtLaunch() {
        timerPreferences.saveTimerValue(STORED_MINUTES)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val timer = activity.timer()
                assertEquals("the minutes on the seekbar", STORED_MINUTES, timer.minutes)
                assertEquals("the label", label(STORED_MINUTES), timer.label().text.toString())
            }
        }
    }

    @Test
    fun theMinutesTheUserPicksAreStored() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.timer().setSeekBarByUser(PICKED_STEPS)

                assertEquals("the stored minutes", PICKED_STEPS * MINUTES_PER_STEP, timerPreferences.getTimerValue())
            }
        }
    }

    /** The label used to come back by rereading the preference; now it comes from the seekbar it sits over. */
    @Test
    fun aStopTradesTheCountdownForThePickedMinutes() {
        timerPreferences.saveTimerValue(STORED_MINUTES)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val timer = activity.timer()
                timer.setPlayingState(true)
                timer.showCountdown(COUNTDOWN_MILLIS)

                timer.setPlayingState(false)

                assertEquals("the label after a stop", label(STORED_MINUTES), timer.label().text.toString())
            }
        }
    }

    private fun MainActivity.timer(): TimerView = findViewById(R.id.timerView)

    private fun TimerView.label(): TextView = findViewById(R.id.timerTextView)

    /** As in `NoiseToggleUiTest`: the accessibility action is the user's path, and it reports `fromUser`. */
    private fun TimerView.setSeekBarByUser(progress: Int) {
        val arguments = Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, progress.toFloat())
        }
        val accepted = findViewById<SeekBar>(R.id.timerSeekBar).performAccessibilityAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            arguments,
        )
        assertTrue("the seekbar accepted a user-set value", accepted)
    }

    private fun label(minutes: Int) = String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

    private companion object {
        const val MINUTES_PER_STEP = 30
        const val STORED_MINUTES = 90
        const val PICKED_STEPS = 4
        const val COUNTDOWN_MILLIS = 12_345L
    }
}
