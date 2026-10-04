package ru.pravbeseda.sleepnoise.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {
    private val idle = PlaybackState(timerMinutes = TIMER_MINUTES)
    private val playing = PlaybackState(playing = true, remainingMillis = REMAINING_MILLIS, timerMinutes = TIMER_MINUTES)

    @Test
    fun anIdleStateIsSilent() {
        assertFalse(idle.audible)
    }

    @Test
    fun aStartPlaysAndKeepsTheTimer() {
        val started = idle.afterStart()

        assertEquals(PlaybackState(playing = true, timerMinutes = TIMER_MINUTES), started)
        assertTrue(started.audible)
    }

    /** Play pressed while another app holds the output is the user asking for the sound back. */
    @Test
    fun aStartDuringAPauseResumes() {
        val started = playing.afterPause(true).afterStart()

        assertEquals(playing, started)
        assertTrue(started.audible)
    }

    @Test
    fun aBindTakesTheServicesSessionAndKeepsTheTimer() {
        val bound = idle.afterBind(playing = true, paused = true, remainingMillis = REMAINING_MILLIS)

        assertEquals(
            PlaybackState(
                playing = true,
                paused = true,
                remainingMillis = REMAINING_MILLIS,
                timerMinutes = TIMER_MINUTES,
                confirmed = true,
            ),
            bound,
        )
    }

    /** The session ended while nothing was bound to hear it: the screen must not go on showing it. */
    @Test
    fun aBindToAStoppedServiceEndsTheSessionOnScreen() {
        assertEquals(idle.copy(confirmed = true), playing.afterBind(playing = false, paused = false, remainingMillis = 0))
    }

    /** Until the service answers, `playing` is the screen's own guess: a cold start reads as stopped whatever is sounding. */
    @Test
    fun aSessionIsConfirmedOnlyOnceTheServiceAnswers() {
        assertFalse(idle.confirmed)
        assertFalse(idle.afterStart().afterStop().confirmed)
        assertTrue(idle.afterBind(playing = false, paused = false, remainingMillis = 0).confirmed)
        assertTrue(idle.afterBind(playing = true, paused = false, remainingMillis = 0).afterStop().confirmed)
    }

    @Test
    fun aTickMovesTheCountdownOnly() {
        assertEquals(playing.copy(remainingMillis = LATER_REMAINING_MILLIS), playing.afterTick(LATER_REMAINING_MILLIS))
    }

    /** A paused session is still a session: the countdown stays, and only the button offers to start again. */
    @Test
    fun aPauseSilencesWithoutEndingTheSession() {
        val paused = playing.afterPause(true)

        assertTrue(paused.playing)
        assertEquals(REMAINING_MILLIS, paused.remainingMillis)
        assertFalse(paused.audible)
    }

    @Test
    fun theEndOfAPauseIsAudibleAgain() {
        assertEquals(playing, playing.afterPause(true).afterPause(false))
    }

    @Test
    fun aStopEndsTheSessionAndKeepsTheTimer() {
        assertEquals(idle, playing.afterPause(true).afterStop())
    }

    @Test
    fun aTimerChangeLeavesTheSessionAlone() {
        assertEquals(playing.copy(timerMinutes = OTHER_TIMER_MINUTES), playing.afterTimerChange(OTHER_TIMER_MINUTES))
    }

    private companion object {
        const val TIMER_MINUTES = 90
        const val OTHER_TIMER_MINUTES = 120
        const val REMAINING_MILLIS = 5_400_000L
        const val LATER_REMAINING_MILLIS = 5_399_000L
    }
}
