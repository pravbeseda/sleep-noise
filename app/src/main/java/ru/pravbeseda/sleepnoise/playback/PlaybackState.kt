package ru.pravbeseda.sleepnoise.playback

/**
 * What the screen shows of a session, and the timer the next start carries.
 *
 * Every transition is a pure function of the state it starts from, so this file imports nothing from
 * `android.*` and is tested on the JVM; `PlaybackViewModel` feeds it what the service reports.
 */
data class PlaybackState(
    val playing: Boolean = false,
    /** Silent while another app holds the output, and still a session: the countdown goes on. */
    val paused: Boolean = false,
    /** Milliseconds left on the sleep timer, 0 when there is none or before the first tick. */
    val remainingMillis: Long = 0,
    val timerMinutes: Int = 0,
) {
    /** Sounding right now, so the button offers to stop; a paused session is offered a start, which resumes it. */
    val audible: Boolean
        get() = playing && !paused

    /** The service reports no start, so the screen takes it as it asks for one. */
    fun afterStart(): PlaybackState = copy(playing = true, paused = false)

    fun afterBind(playing: Boolean, paused: Boolean, remainingMillis: Long): PlaybackState =
        copy(playing = playing, paused = paused, remainingMillis = remainingMillis)

    fun afterTick(remainingMillis: Long): PlaybackState = copy(remainingMillis = remainingMillis)

    fun afterPause(paused: Boolean): PlaybackState = copy(paused = paused)

    fun afterStop(): PlaybackState = copy(playing = false, paused = false, remainingMillis = 0)

    fun afterTimerChange(minutes: Int): PlaybackState = copy(timerMinutes = minutes)
}
