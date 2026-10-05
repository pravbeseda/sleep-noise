package ru.pravbeseda.sleepnoise.widget

import java.util.Locale

/**
 * What a play widget shows: the glyph, and either the timer the next start carries or the countdown of the
 * one running. Imports nothing from `android.*`, so it is tested on the JVM; `PlayWidgets` renders it.
 */
data class WidgetFace(
    val playing: Boolean,
    /** The chosen timer as `h:mm`, shown while stopped; null with no timer, and always while playing. */
    val plannedTimer: String?,
    /** Where the countdown ends on the `elapsedRealtime` clock; null while stopped or with no timer. */
    val countdownDeadlineMillis: Long?,
    /** How long the countdown runs in all; null exactly when [countdownDeadlineMillis] is. */
    val countdownDurationMillis: Long?,
) {
    /** How much of the timer is left at [nowMillis], for a ring that empties with it: none while stopped, all with no timer. */
    fun ringShare(nowMillis: Long): Float {
        val deadline = countdownDeadlineMillis
        val duration = countdownDurationMillis
        return when {
            !playing -> 0f
            deadline == null || duration == null -> 1f
            else -> ((deadline - nowMillis).toFloat() / duration).coerceIn(0f, 1f)
        }
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60

        fun stopped(timerMinutes: Int): WidgetFace = WidgetFace(
            playing = false,
            plannedTimer = if (timerMinutes > 0) formatPlanned(timerMinutes) else null,
            countdownDeadlineMillis = null,
            countdownDurationMillis = null,
        )

        fun playing(deadlineMillis: Long?, durationMillis: Long?): WidgetFace = WidgetFace(
            playing = true,
            plannedTimer = null,
            countdownDeadlineMillis = deadlineMillis,
            countdownDurationMillis = durationMillis,
        )

        // h:mm, the shape the widget's Chronometer counts down in, rather than the app's zero-padded hh:mm.
        private fun formatPlanned(minutes: Int): String =
            String.format(Locale.getDefault(), "%d:%02d", minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }
}
