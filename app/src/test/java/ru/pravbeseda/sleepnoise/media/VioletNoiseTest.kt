package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

class VioletNoiseTest {
    private val bufferSize = 1 shl 16
    private val headSize = 64
    private val seed = 20260907
    private val sampleRate = SAMPLE_RATE_HZ.toDouble()

    private val expectedRms = NORMALISED_SOURCE_RMS
    private val rmsTolerance = 0.025

    /** The same split [PinkNoiseTest] measures its tilt at, so the two claims are read against one another. */
    private val bandSplitHz = 500.0

    /** Measured factor is ~40; a tenth of it still fails for any source that is not tilted towards the high end. */
    private val minimumTilt = 10.0

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        VioletNoise(Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        assertEquals("normalisation missed its target level", expectedRms, rms(buffer), rmsTolerance)
    }

    /**
     * The difference of two samples of white cannot exceed twice their own bound, so at this gain the source
     * has headroom the others spend in the mixer's clamp. Asserted rather than assumed: it is the one thing
     * that makes a source this bright cheap to mix.
     */
    @Test
    fun fillNeverReachesFullScale() {
        val buffer = FloatArray(bufferSize)

        VioletNoise(Random(seed)).fill(buffer)

        val peak = buffer.maxOf { abs(it) }
        assertTrue("violet reached $peak, so it is spending headroom it was supposed to keep", peak < 0.7f)
    }

    @Test
    fun resetReturnsTheFilterToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = VioletNoise(random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        VioletNoise(Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    @Test
    fun violetTiltsItsEnergyHigherThanWhiteDoes() {
        val violet = FloatArray(bufferSize).also { VioletNoise(Random(seed)).fill(it) }
        val white = FloatArray(bufferSize).also { WhiteNoise(Random(seed)).fill(it) }

        val violetTilt = highToLowEnergyRatio(violet)
        val whiteTilt = highToLowEnergyRatio(white)

        assertTrue(
            "violet should hold far more of its energy above $bandSplitHz Hz than white: violet $violetTilt, white $whiteTilt",
            violetTilt > minimumTilt * whiteTilt,
        )
    }

    /**
     * The mirror of the split [PinkNoiseTest] uses, reported the other way up. Kept local rather than shared with
     * it: lifting that helper out is a change to a test this PR has no other reason to touch.
     */
    private fun highToLowEnergyRatio(signal: FloatArray): Double {
        val smoothing = exp(-2.0 * PI * bandSplitHz / sampleRate)
        var low = 0.0
        var lowEnergy = 0.0
        var highEnergy = 0.0
        for (sample in signal) {
            low = (smoothing * low) + ((1.0 - smoothing) * sample)
            val high = sample - low
            lowEnergy += low * low
            highEnergy += high * high
        }
        return highEnergy / lowEnergy
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)
}
