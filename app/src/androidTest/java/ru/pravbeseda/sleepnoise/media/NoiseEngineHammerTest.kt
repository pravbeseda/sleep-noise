package ru.pravbeseda.sleepnoise.media

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phase's done-criterion, executed: 100 start/stop cycles against a real [android.media.AudioTrack]
 * are served by exactly one writer thread, cost their caller no measurable wait, produce no
 * `IllegalStateException`, and leave no thread behind once the engine is released.
 *
 * The dwell between start and stop is varied so the stop lands at different points of the writer's loop —
 * before the track exists, inside a `write()`, and after several buffers have gone out.
 */
@RunWith(AndroidJUnit4::class)
class NoiseEngineHammerTest {
    private val uncaught = mutableListOf<Throwable>()
    private var replacedHandler: Thread.UncaughtExceptionHandler? = null

    /** Both channels audible: the mixer skips a muted one, and a skipped channel proves nothing. */
    private val engine = NoiseEngine(
        listOf(
            NoiseChannel(WhiteNoise()).apply { volume = WHITE_VOLUME },
            NoiseChannel(BrownNoise()).apply { volume = BROWN_VOLUME },
        ),
    )

    @Before
    fun captureUncaughtExceptions() {
        replacedHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // The handler is process-wide: the engine's thread is this test's business, and anything
            // else goes back to the handler that would otherwise have killed the process over it.
            if (thread.name == NoiseEngine.THREAD_NAME) {
                synchronized(uncaught) { uncaught += throwable }
            } else {
                replacedHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    @After
    fun releaseEngine() {
        // Idempotent, and the only thing that ends the writer thread: a failed assertion must not leak it.
        engine.release()
    }

    @After
    fun restoreUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(replacedHandler)
    }

    @Test
    fun hammeredStartStopKeepsOneWriterAndBlocksNoCaller() {
        val startMarker = logMarker("start")

        var worstStopMillis = 0L
        repeat(CYCLES) { cycle ->
            engine.start()
            Thread.sleep(DWELLS_MILLIS[cycle % DWELLS_MILLIS.size])
            // One writer serves every session, and it is the only handle on the one track.
            assertEquals("writer threads alive during cycle $cycle", 1, liveWriterThreads().size)

            val stopStartedAt = System.nanoTime()
            engine.stop()
            worstStopMillis = maxOf(worstStopMillis, (System.nanoTime() - stopStartedAt) / NANOS_PER_MILLI)

            // A stop parks that thread rather than ending it: nothing is spawned per session.
            assertEquals("writer threads alive after the stop of cycle $cycle", 1, liveWriterThreads().size)
        }

        val releaseStartedAt = System.nanoTime()
        engine.release()
        val releaseMillis = (System.nanoTime() - releaseStartedAt) / NANOS_PER_MILLI
        Log.i(TAG, "$CYCLES cycles done; worst stop() took $worstStopMillis ms, release() took $releaseMillis ms")

        assertTrue(
            "worst stop() took $worstStopMillis ms, over the $MAX_HANDOFF_MILLIS ms a main-thread caller may block for",
            worstStopMillis <= MAX_HANDOFF_MILLIS,
        )
        assertTrue(
            "release() took $releaseMillis ms, over the $MAX_HANDOFF_MILLIS ms a main-thread caller may block for",
            releaseMillis <= MAX_HANDOFF_MILLIS,
        )
        assertEquals("writer threads still alive after release()", emptyList<String>(), awaitNoWriterThreads())
        assertEquals("the writer thread died of an uncaught exception: $uncaught", 0, uncaught.size)
        assertEquals("${NoiseEngine.TAG} logged an error during the run", 0, engineErrorsSince(startMarker))
    }

    private fun liveWriterThreads(): List<String> =
        Thread.getAllStackTraces().keys.filter { it.name == NoiseEngine.THREAD_NAME && it.isAlive }.map { it.toString() }

    /**
     * The writer threads left once the engine's thread has had time to exit. `release()` does not wait for it —
     * that is the point of it — so the wait is here instead, and it is the write in flight that it covers.
     */
    private fun awaitNoWriterThreads(): List<String> {
        val deadline = System.nanoTime() + THREAD_EXIT_TIMEOUT_MILLIS * NANOS_PER_MILLI
        while (System.nanoTime() < deadline) {
            val alive = liveWriterThreads()
            if (alive.isEmpty()) return alive
            Thread.sleep(THREAD_EXIT_POLL_MILLIS)
        }
        return liveWriterThreads()
    }

    /**
     * How many errors the engine logged since [startMarker], counted over that slice of logcat rather than over
     * the whole ring buffer — which also holds earlier runs of this test and drops its oldest lines as it fills.
     */
    private fun engineErrorsSince(startMarker: String): Int {
        val lines = logcatThrough(logMarker("end"))
        val from = lines.indexOfFirst { it.contains(startMarker) }
        if (from < 0) throw AssertionError("logcat rotated past this run's start marker; the count would be meaningless")
        return lines.drop(from).count { it.contains(NoiseEngine.TAG) }
    }

    private fun logMarker(name: String): String = "hammer-$name-${System.nanoTime()}".also { Log.e(TAG, it) }

    /**
     * Logcat as of [marker]. The marker is what makes the read trustworthy: the lines of one process reach the
     * buffer in order, so once it is visible every error the engine logged is visible too, and if it never shows
     * up logcat cannot be read from here and the test says so instead of counting an empty list.
     */
    private fun logcatThrough(marker: String): List<String> {
        repeat(PROBE_ATTEMPTS) {
            val lines = readLogcat()
            if (lines.any { it.contains(marker) }) return lines
            Thread.sleep(PROBE_POLL_MILLIS)
        }
        throw AssertionError("logcat is unreadable from the test process: the probe line never appeared")
    }

    /** Error-level lines of the engine's tag and of this test's own marker tag; everything else is silenced. */
    private fun readLogcat(): List<String> {
        val process = ProcessBuilder("logcat", "-d", "-s", "${NoiseEngine.TAG}:E", "$TAG:E").redirectErrorStream(true).start()
        return try {
            process.inputStream.bufferedReader().use { it.readLines() }
        } finally {
            process.destroy()
        }
    }

    private companion object {
        const val CYCLES = 100

        const val WHITE_VOLUME = 0.6f
        const val BROWN_VOLUME = 0.4f

        /**
         * Cycled over the 100 runs. Zero stops before the writer has built its track, the small values land
         * inside the first `write()`, and the two long ones after several buffers have been written.
         */
        val DWELLS_MILLIS = longArrayOf(0, 1, 2, 3, 5, 8, 0, 1, 40, 120)

        /**
         * What `stop()` and `release()` cost the thread that calls them: a lock the writer holds only to read
         * the intent out of it, so the real figure is well under a millisecond. The bound is loose enough to
         * ride out a scheduling hiccup on a loaded emulator and still far below the 176-208 ms the joining
         * `stop()` this replaced was measured at, so a regression to waiting for the writer fails here.
         */
        const val MAX_HANDOFF_MILLIS = 50L
        const val NANOS_PER_MILLI = 1_000_000

        /** One write in flight is what the writer takes to exit, and that write is the emulator's ~200 ms. */
        const val THREAD_EXIT_TIMEOUT_MILLIS = 2_000L
        const val THREAD_EXIT_POLL_MILLIS = 20L

        const val TAG = "NoiseHammer"

        const val PROBE_ATTEMPTS = 20
        const val PROBE_POLL_MILLIS = 50L
    }
}
