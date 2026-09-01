package ru.pravbeseda.sleepnoise.timer

import java.util.Locale

/**
 * The sleep timer's arithmetic: a deadline on a monotonic clock, and how long is left on it.
 *
 * The clock is passed in rather than read here — the app supplies `SystemClock.elapsedRealtime()` —
 * so this class imports nothing from `android.*` and is tested on the JVM.
 */
class SleepTimer(private val deadlineMillis: Long) {

    /** Milliseconds left at [nowMillis], never negative. */
    fun remaining(nowMillis: Long): Long = (deadlineMillis - nowMillis).coerceAtLeast(0)

    fun hasExpired(nowMillis: Long): Boolean = nowMillis >= deadlineMillis

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60
        private const val MINUTES_PER_HOUR = 60

        fun forDuration(nowMillis: Long, durationMinutes: Int): SleepTimer =
            SleepTimer(nowMillis + durationMinutes * SECONDS_PER_MINUTE * MILLIS_PER_SECOND)

        /**
         * `mm:ss`, or `hh:mm:ss` once an hour is left. A user reads this, so the locale is the default
         * one: on an Arabic device the digits are Arabic, the same as the system clock's.
         */
        fun formatRemaining(remainingMillis: Long): String {
            val totalSeconds = remainingMillis / MILLIS_PER_SECOND
            val hours = totalSeconds / SECONDS_PER_MINUTE / MINUTES_PER_HOUR
            val minutes = totalSeconds / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
            val seconds = totalSeconds % SECONDS_PER_MINUTE
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        }
    }
}
