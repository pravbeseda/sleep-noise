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
 * leave exactly one writer thread alive while playing and none afterwards, cost the caller a bounded
 * `stop()`, and produce no `IllegalStateException`.
 *
 * The dwell between start and stop is varied so the stop lands at different points of the writer's loop —
 * before the track exists, inside a `write()`, and after several buffers have gone out.
 */
@RunWith(AndroidJUnit4::class)
class NoiseEngineHammerTest {
    private val uncaught = mutableListOf<Throwable>()
    private var replacedHandler: Thread.UncaughtExceptionHandler? = null

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
    fun restoreUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(replacedHandler)
    }

    @Test
    fun hammeredStartStopLeavesNoThreadAndNoError() {
        val startMarker = logMarker("start")
        val engine = NoiseEngine(
            listOf(
                NoiseChannel(WhiteNoise()).apply { volume = WHITE_VOLUME },
                NoiseChannel(BrownNoise()).apply { volume = BROWN_VOLUME },
            ),
        )

        var worstStopMillis = 0L
        repeat(CYCLES) { cycle ->
            val dwell = DWELLS_MILLIS[cycle % DWELLS_MILLIS.size]
            engine.start()
            Thread.sleep(dwell)
            // While playing there is exactly one writer, and it is the only handle on the one track.
            if (dwell >= SETTLED_DWELL_MILLIS) {
                assertEquals("writer threads alive during cycle $cycle", 1, liveWriterThreads().size)
            }
            val stopStartedAt = System.nanoTime()
            engine.stop()
            worstStopMillis = maxOf(worstStopMillis, (System.nanoTime() - stopStartedAt) / NANOS_PER_MILLI)
        }
        Log.i(TAG, "$CYCLES cycles done; worst stop() took $worstStopMillis ms")

        assertEquals("writer threads still alive after the last stop()", emptyList<String>(), liveWriterThreads())
        assertEquals("the writer thread died of an uncaught exception: $uncaught", 0, uncaught.size)
        assertTrue(
            "worst stop() took $worstStopMillis ms, over the $MAX_STOP_MILLIS ms a caller may block for",
            worstStopMillis <= MAX_STOP_MILLIS,
        )
        assertEquals("${NoiseEngine.TAG} logged an error during the run", 0, engineErrorsSince(startMarker))
    }

    private fun liveWriterThreads(): List<String> =
        Thread.getAllStackTraces().keys.filter { it.name == NoiseEngine.THREAD_NAME && it.isAlive }.map { it.toString() }

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

        /** Both channels audible: the mixer skips a muted one, and a skipped channel proves nothing. */
        const val WHITE_VOLUME = 0.6f
        const val BROWN_VOLUME = 0.4f

        /**
         * Cycled over the 100 runs. Zero stops before the writer has built its track, the small values land
         * inside the first `write()`, and the two long ones after several buffers have been written.
         */
        val DWELLS_MILLIS = longArrayOf(0, 1, 2, 3, 5, 8, 0, 1, 40, 120)

        /** Long enough that the writer is certainly running, so the live count can be asserted at all. */
        const val SETTLED_DWELL_MILLIS = 40L

        /**
         * `stop()` joins the writer, so the caller waits out the `write()` in flight and nothing else — probing
         * the engine put `AudioTrack.stop()` at 2 ms and `release()` at 1 ms against a `write()` of up to 195 ms.
         * That 195 ms is the API 36 emulator's audio sink, not the chunk, which held 46 ms of audio. So the bound
         * is not a claim about what a device costs its caller: it catches a `join()` that stops returning.
         * Measured worst over 100 cycles on that emulator: 176-208 ms.
         */
        const val MAX_STOP_MILLIS = 1000L
        const val NANOS_PER_MILLI = 1_000_000

        const val TAG = "NoiseHammer"

        const val PROBE_ATTEMPTS = 20
        const val PROBE_POLL_MILLIS = 50L
    }
}
