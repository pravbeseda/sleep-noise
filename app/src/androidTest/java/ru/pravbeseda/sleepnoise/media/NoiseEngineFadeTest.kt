package ru.pravbeseda.sleepnoise.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the service relies on to end a session: a faded stop is reported once it is silent, and a stop that is
 * cut or turned around is not reported at all. Timed against a real [android.media.AudioTrack], so the bounds
 * are what an emulator's slow writes still meet rather than the fade's own second.
 */
@RunWith(AndroidJUnit4::class)
class NoiseEngineFadeTest {
    private val fadedOut = AtomicInteger()
    private val engine = NoiseEngine(listOf(NoiseChannel(WhiteNoise()).apply { volume = VOLUME })) { fadedOut.incrementAndGet() }

    @After
    fun releaseEngine() {
        engine.release()
    }

    @Test
    fun aStopIsReportedOnceItsFadeHasRunAndStillReturnsAtOnce() {
        engine.start()
        Thread.sleep(PLAY_MILLIS)

        val stopStartedAt = System.nanoTime()
        engine.stop()
        val stopMillis = (System.nanoTime() - stopStartedAt) / NANOS_PER_MILLI

        assertTrue(
            "stop() took $stopMillis ms, over the $MAX_HANDOFF_MILLIS ms a main-thread caller may block for",
            stopMillis <= MAX_HANDOFF_MILLIS,
        )
        assertTrue("the fade-out was never reported", awaitFadedOut())
        assertEquals("the fade-out was reported more than once", 1, fadedOut.get())
    }

    @Test
    fun aStartDuringTheFadeOutTurnsItAroundAndNothingIsReported() {
        engine.start()
        Thread.sleep(PLAY_MILLIS)
        engine.stop()
        Thread.sleep(PLAY_MILLIS)

        engine.start()

        assertFalse("a fade-out turned around was reported as finished", awaitFadedOut())
    }

    @Test
    fun aCutIsNotReported() {
        engine.start()
        Thread.sleep(PLAY_MILLIS)

        engine.stopNow()

        assertFalse("stopNow() was reported as a finished fade-out", awaitFadedOut())
    }

    private fun awaitFadedOut(): Boolean {
        val deadline = System.nanoTime() + FADE_REPORT_TIMEOUT_MILLIS * NANOS_PER_MILLI
        while (System.nanoTime() < deadline) {
            if (fadedOut.get() > 0) return true
            Thread.sleep(POLL_MILLIS)
        }
        return fadedOut.get() > 0
    }

    private companion object {
        const val VOLUME = 0.5f
        const val PLAY_MILLIS = 300L

        /** As in [NoiseEngineHammerTest]: a lock handoff, far under a wait for the writer or for the fade. */
        const val MAX_HANDOFF_MILLIS = 50L

        /**
         * A second of fade takes several times that on an emulator, where one `write()` takes far longer than
         * the audio it carries; the negative cases wait the whole bound, so a missed report would have arrived.
         */
        const val FADE_REPORT_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
        const val NANOS_PER_MILLI = 1_000_000
    }
}
