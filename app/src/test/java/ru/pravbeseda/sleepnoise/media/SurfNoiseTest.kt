package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class SurfNoiseTest {
    private val seed = 20260906
    private val headSize = 64

    /** Long enough to hold several waves, whatever length each of them is drawn at. */
    private val bufferSize = SAMPLE_RATE_HZ * 60

    /**
     * Surf is judged at the break, not on the average: it is quiet between waves by design, so holding its
     * long-run level to the shared target would put the break itself far past full scale. The loudest second
     * is what reaches the level every steady source sits at.
     */
    private val expectedBreakRms = NORMALISED_SOURCE_RMS
    private val breakRmsTolerance = 0.04

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        SurfNoise(Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        assertEquals("the break missed its target level", expectedBreakRms, secondBySecondRms(buffer).max(), breakRmsTolerance)

        val clamped = buffer.count { it <= -1.0f || it >= 1.0f }
        assertTrue("$clamped samples of ${buffer.size} ran into the clamp", clamped < buffer.size * MAX_CLAMPED_SHARE)
    }

    @Test
    fun resetReturnsTheSourceToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = SurfNoise(random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        SurfNoise(Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    /**
     * What separates surf from any steady noise: the level swells and recedes instead of sitting still. Measured
     * over one-second windows, which is far shorter than a wave and far longer than the noise inside it.
     */
    @Test
    fun theLevelSwellsAndRecedesRatherThanSittingStill() {
        val buffer = FloatArray(bufferSize).also { SurfNoise(Random(seed)).fill(it) }

        val windows = secondBySecondRms(buffer)

        val loudest = windows.max()
        val quietest = windows.min()
        assertTrue(
            "the level barely moves across the minute: loudest window $loudest, quietest $quietest",
            loudest > MINIMUM_SWELL * quietest,
        )
    }

    /** A wave is a slow thing: over a minute there are several of them, and nowhere near one a second. */
    @Test
    fun theWavesComeAtTheRateSurfComesAt() {
        val buffer = FloatArray(bufferSize).also { SurfNoise(Random(seed)).fill(it) }

        val windows = secondBySecondRms(buffer)
        val threshold = (windows.max() + windows.min()) / 2
        val swells = windows.zipWithNext().count { (before, after) -> before <= threshold && after > threshold }

        assertTrue("waves counted over a minute: $swells", swells in MINIMUM_WAVES_PER_MINUTE..MAXIMUM_WAVES_PER_MINUTE)
    }

    /** One level per second: far shorter than a wave, far longer than the noise inside it. */
    private fun secondBySecondRms(buffer: FloatArray): List<Double> =
        buffer.toList().chunked(SAMPLE_RATE_HZ) { window -> sqrt(window.sumOf { it.toDouble() * it } / window.size) }

    private companion object {
        /** Peaks run about three times the RMS at the break, so the clamp should be catching next to nothing. */
        const val MAX_CLAMPED_SHARE = 0.001

        /** Measured swing on this seed is ~4x; half of it still fails any source whose level is merely noisy. */
        const val MINIMUM_SWELL = 2.0

        // A minute of surf holds a handful of waves. The bounds are wide because each wave's length is drawn
        // at random; they fail a source that pulses every second as surely as one that never breaks at all.
        const val MINIMUM_WAVES_PER_MINUTE = 3
        const val MAXIMUM_WAVES_PER_MINUTE = 12
    }
}
