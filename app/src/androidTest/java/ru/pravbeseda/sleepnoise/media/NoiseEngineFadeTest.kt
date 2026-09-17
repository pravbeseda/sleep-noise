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

    /**
     * Waits for the writer to exit, since `release()` does not: a writer left running is counted by the next class
     * that looks for threads of that name, [NoiseEngineHammerTest] among them.
     */
    @After
    fun releaseEngine() {
        engine.release()
        val deadline = System.nanoTime() + THREAD_EXIT_TIMEOUT_MILLIS * NANOS_PER_MILLI
        while (liveWriterThreads() > 0 && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)
        assertEquals("writer threads still alive after release()", 0, liveWriterThreads())
    }

    @Test
    fun aStopIsReportedOnceItsFadeHasRun() {
        engine.start()
        Thread.sleep(PLAY_MILLIS)

        engine.stop()

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

    private fun liveWriterThreads(): Int = Thread.getAllStackTraces().keys.count { it.name == NoiseEngine.THREAD_NAME && it.isAlive }

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

        /**
         * A second of fade takes several times that on an emulator, where one `write()` takes far longer than
         * the audio it carries; the negative cases wait the whole bound, so a missed report would have arrived.
         */
        const val FADE_REPORT_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L

        /** As in [NoiseEngineHammerTest]: the write in flight is what the writer takes to exit. */
        const val THREAD_EXIT_TIMEOUT_MILLIS = 2_000L
        const val NANOS_PER_MILLI = 1_000_000
    }
}
