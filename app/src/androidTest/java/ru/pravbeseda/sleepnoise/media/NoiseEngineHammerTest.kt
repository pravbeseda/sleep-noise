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
 * leave no writer thread behind, cost the caller a bounded `stop()`, and produce no `IllegalStateException`.
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
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            synchronized(uncaught) { uncaught += throwable }
        }
    }

    @After
    fun restoreUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(replacedHandler)
    }

    @Test
    fun hammeredStartStopLeavesNoThreadAndNoError() {
        val engineErrorsBefore = countEngineErrors(readLogcat())
        val engine = NoiseEngine(
            listOf(
                NoiseChannel(WhiteNoise()).apply { volume = WHITE_VOLUME },
                NoiseChannel(BrownNoise()).apply { volume = BROWN_VOLUME },
            ),
        )

        var worstStopMillis = 0L
        var worstSettledStopMillis = 0L
        repeat(CYCLES) { cycle ->
            val dwell = DWELLS_MILLIS[cycle % DWELLS_MILLIS.size]
            engine.start()
            Thread.sleep(dwell)
            val stopStartedAt = System.nanoTime()
            engine.stop()
            val stopMillis = (System.nanoTime() - stopStartedAt) / NANOS_PER_MILLI
            worstStopMillis = maxOf(worstStopMillis, stopMillis)
            if (dwell >= SETTLED_DWELL_MILLIS) worstSettledStopMillis = maxOf(worstSettledStopMillis, stopMillis)
        }
        Log.i(TAG, "$CYCLES cycles done; worst stop() $worstStopMillis ms, worst settled stop() $worstSettledStopMillis ms")

        assertEquals("writer threads still alive after the last stop()", emptyList<String>(), liveWriterThreads())
        assertEquals("the writer thread died of an uncaught exception: $uncaught", 0, uncaught.size)
        assertTrue(
            "worst stop() took $worstStopMillis ms, over the $MAX_STOP_MILLIS ms a caller may block for",
            worstStopMillis <= MAX_STOP_MILLIS,
        )
        assertTrue(
            "worst stop() after real playback took $worstSettledStopMillis ms, over $MAX_SETTLED_STOP_MILLIS ms",
            worstSettledStopMillis <= MAX_SETTLED_STOP_MILLIS,
        )
        assertEquals("$ENGINE_TAG logged an error during the run", engineErrorsBefore, countEngineErrors(logcatAfterRun()))
    }

    private fun liveWriterThreads(): List<String> =
        Thread.getAllStackTraces().keys.filter { it.name == WRITER_THREAD_NAME && it.isAlive }.map { it.toString() }

    /**
     * Logcat as of a marker logged after the last cycle. The marker is what makes the read trustworthy: the
     * lines of one process reach the buffer in order, so once it is visible every error the engine logged is
     * visible too, and if it never shows up logcat cannot be read from here and the test says so instead of
     * asserting against an empty list.
     */
    private fun logcatAfterRun(): List<String> {
        val marker = "hammer-probe-${System.nanoTime()}"
        Log.e(TAG, marker)
        repeat(PROBE_ATTEMPTS) {
            val lines = readLogcat()
            if (lines.any { it.contains(marker) }) return lines
            Thread.sleep(PROBE_POLL_MILLIS)
        }
        throw AssertionError("logcat is unreadable from the test process: the probe line never appeared")
    }

    /** Error-level lines of the engine's tag and of this test's own probe tag; everything else is silenced. */
    private fun readLogcat(): List<String> {
        val process = ProcessBuilder("logcat", "-d", "-s", "$ENGINE_TAG:E", "$TAG:E").redirectErrorStream(true).start()
        return try {
            process.inputStream.bufferedReader().use { it.readLines() }
        } finally {
            process.destroy()
        }
    }

    private fun countEngineErrors(lines: List<String>): Int = lines.count { it.contains(ENGINE_TAG) }

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

        /**
         * `stop()` joins the writer, so the caller blocks for the write in flight and nothing else: probing the
         * engine on the API 36 emulator put `AudioTrack.stop()` at 2 ms, `release()` at 1 ms and a single
         * `write()` at up to 195 ms — for a chunk holding 46 ms of audio, so that emulator's audio sink does not
         * drain in real time. On a device the bound is the chunk's own duration. Measured worst over 100 cycles
         * here: 176-208 ms; the bound keeps a wide margin over that rather than pinning an emulator artifact.
         */
        const val MAX_STOP_MILLIS = 1000L

        /**
         * Measured apart because the two stops are different paths: after real playback the caller waits out a
         * write, while a stop that follows `start()` by nothing at all waits for the track to be built and torn
         * down — the slower path, and not one a finger can produce.
         */
        const val SETTLED_DWELL_MILLIS = 40L
        const val MAX_SETTLED_STOP_MILLIS = 600L
        const val NANOS_PER_MILLI = 1_000_000

        const val TAG = "NoiseHammer"

        /** The engine's log tag, and separately the name it gives its writer thread. */
        const val ENGINE_TAG = "NoiseEngine"
        const val WRITER_THREAD_NAME = "NoiseEngine"

        const val PROBE_ATTEMPTS = 20
        const val PROBE_POLL_MILLIS = 50L
    }
}
