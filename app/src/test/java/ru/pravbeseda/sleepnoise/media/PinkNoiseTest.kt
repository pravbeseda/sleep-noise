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

class PinkNoiseTest {
    private val bufferSize = 1 shl 16
    private val headSize = 64
    private val seed = 20260905
    private val sampleRate = SAMPLE_RATE_HZ.toDouble()

    /**
     * The normalisation target, pinned from both sides: a floor on the peak alone passes a gain three times too
     * large, which clips a fifth of the samples.
     */
    private val expectedRms = 0.25
    private val rmsTolerance = 0.025

    /** Well inside the band a phone speaker reproduces, and far enough from both edges that the two sources cannot tie. */
    private val bandSplitHz = 500.0

    /** Measured factor is ~38; a tenth of it still fails for any source that is not tilted towards the low end. */
    private val minimumTilt = 10.0

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        PinkNoise(Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        val rms = rms(buffer)
        assertEquals("normalisation missed its target level", expectedRms, rms, rmsTolerance)
    }

    @Test
    fun resetReturnsTheFilterToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = PinkNoise(random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        PinkNoise(Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    @Test
    fun pinkTiltsItsEnergyLowerThanWhiteDoes() {
        val pink = FloatArray(bufferSize).also { PinkNoise(Random(seed)).fill(it) }
        val white = FloatArray(bufferSize).also { WhiteNoise(Random(seed)).fill(it) }

        val pinkTilt = lowToHighEnergyRatio(pink)
        val whiteTilt = lowToHighEnergyRatio(white)

        assertTrue(
            "pink should hold far more of its energy below $bandSplitHz Hz than white: pink $pinkTilt, white $whiteTilt",
            pinkTilt > minimumTilt * whiteTilt,
        )
    }

    /**
     * Splits the signal with a one-pole low-pass and its complementary high-pass, and reports how the energy divides.
     * Cheaper than an FFT and enough for a claim about the tilt of a spectrum rather than its shape.
     */
    private fun lowToHighEnergyRatio(signal: FloatArray): Double {
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
        return lowEnergy / highEnergy
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)
}
