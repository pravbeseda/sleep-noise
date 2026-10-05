package ru.pravbeseda.sleepnoise.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WidgetFaceTest {
    @Test
    fun aStoppedWidgetOffersAStartAndShowsTheChosenTimer() {
        val face = WidgetFace.stopped(timerMinutes = 90)

        assertFalse(face.playing)
        assertEquals("1:30", face.plannedTimer)
        assertNull(face.countdownDeadlineMillis)
    }

    @Test
    fun aStoppedWidgetWithNoTimerShowsNoLabel() {
        assertNull(WidgetFace.stopped(timerMinutes = 0).plannedTimer)
    }

    @Test
    fun aPlannedTimerUnderAnHourKeepsItsZeroHour() {
        assertEquals("0:30", WidgetFace.stopped(timerMinutes = 30).plannedTimer)
    }

    @Test
    fun aPlayingWidgetCountsDownToTheDeadline() {
        val face = WidgetFace.playing(deadlineMillis = DEADLINE)

        assertTrue(face.playing)
        assertEquals(DEADLINE, face.countdownDeadlineMillis)
        assertNull(face.plannedTimer)
    }

    @Test
    fun aPlayingWidgetWithNoTimerShowsNoCountdown() {
        val face = WidgetFace.playing(deadlineMillis = null)

        assertTrue(face.playing)
        assertNull(face.countdownDeadlineMillis)
        assertNull(face.plannedTimer)
    }

    /** Read by a person, so in their digits, as the app's own timer label is. */
    @Test
    fun aPlannedTimerIsWrittenInTheDefaultLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("١:٣٠", WidgetFace.stopped(timerMinutes = 90).plannedTimer)
        } finally {
            Locale.setDefault(saved)
        }
    }

    private companion object {
        const val DEADLINE = 1_234_567L
    }
}
