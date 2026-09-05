package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

class PinkNoiseTest {
    private val bufferSize = 1 shl 16
    private val headSize = 64
    private val seed = 20260905
    private val sampleRate = 44_100.0

    /** The normalisation aims at a quarter of full scale, so a correct buffer peaks well above this. */
    private val minimumPeak = 0.5f

    /** Well inside the band a phone speaker reproduces, and far enough from both edges that the two sources cannot tie. */
    private val bandSplitHz = 500.0

    /** Measured factor is ~38; a tenth of it still fails for any source that is not tilted towards the low end. */
    private val minimumTilt = 10.0

    /** A Random the test can rewind, so a reset source and a fresh one are fed the identical white input. */
    private class RewindableRandom(private val seed: Int) : Random() {
        private var delegate = Random(seed)

        override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

        fun rewind() {
            delegate = Random(seed)
        }
    }

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        PinkNoise(Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        val peak = buffer.maxOf { abs(it) }
        assertTrue("fill left the buffer silent", buffer.any { it != 0.0f })
        assertTrue("normalisation left the output far below full scale: peak $peak", peak > minimumPeak)
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
}
