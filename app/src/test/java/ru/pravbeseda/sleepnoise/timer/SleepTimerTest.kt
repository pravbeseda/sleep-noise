package ru.pravbeseda.sleepnoise.timer

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SleepTimerTest {

    private val systemLocale: Locale = Locale.getDefault()

    // The formatting reads the default locale, so the assertions below would otherwise depend on
    // whichever locale the machine running them happens to have.
    @Before
    fun pinTheDefaultLocale() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreTheDefaultLocale() {
        Locale.setDefault(systemLocale)
    }

    @Test
    fun forDurationPutsTheDeadlineThatManyMinutesAhead() {
        val timer = SleepTimer.forDuration(nowMillis = 1_000, durationMinutes = 30)

        assertEquals(30 * 60_000L, timer.remaining(1_000))
    }

    @Test
    fun remainingCountsDownWithTheClock() {
        val timer = SleepTimer.forDuration(nowMillis = 0, durationMinutes = 1)

        assertEquals(45_000L, timer.remaining(15_000))
    }

    @Test
    fun remainingClampsAtZeroPastTheDeadline() {
        val timer = SleepTimer.forDuration(nowMillis = 0, durationMinutes = 1)

        assertEquals(0L, timer.remaining(90_000))
    }

    @Test
    fun hasExpiredOnlyFromTheDeadlineOn() {
        val timer = SleepTimer.forDuration(nowMillis = 0, durationMinutes = 1)

        assertFalse(timer.hasExpired(59_999))
        assertTrue(timer.hasExpired(60_000))
        assertTrue(timer.hasExpired(60_001))
    }

    @Test
    fun formatRemainingUsesMinutesAndSecondsBelowAnHour() {
        assertEquals("00:00", SleepTimer.formatRemaining(0))
        assertEquals("01:05", SleepTimer.formatRemaining(65_000))
        assertEquals("59:59", SleepTimer.formatRemaining(3_599_000))
    }

    @Test
    fun formatRemainingAddsHoursFromAnHourOn() {
        assertEquals("01:00:00", SleepTimer.formatRemaining(3_600_000))
        assertEquals("08:30:09", SleepTimer.formatRemaining(30_609_000))
    }

    @Test
    fun formatRemainingTruncatesTheOddMillisecondsAway() {
        assertEquals("00:01", SleepTimer.formatRemaining(1_999))
    }

    @Test
    fun formatRemainingFollowsTheDefaultLocalesDigits() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))

        // The digits are the locale's, not ASCII — a device set to Arabic reads its own numerals,
        // the same as its system clock. Locale.ROOT here would be a regression, not a fix.
        assertNotEquals("01:05", SleepTimer.formatRemaining(65_000))
    }
}
