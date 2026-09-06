package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class RainNoiseTest {
    private val seed = 20260906
    private val headSize = 64
    private val bufferSize = 1 shl 20

    private val expectedRms = NORMALISED_SOURCE_RMS
    private val rmsTolerance = 0.025

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        RainNoise(Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        assertEquals("normalisation missed its target level", expectedRms, rms(buffer), rmsTolerance)

        val clamped = buffer.count { it <= -1.0f || it >= 1.0f }
        assertTrue("$clamped samples of ${buffer.size} ran into the clamp", clamped < buffer.size * MAX_CLAMPED_SHARE)
    }

    @Test
    fun resetReturnsTheSourceToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = RainNoise(random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        RainNoise(Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    /**
     * Rain is drops on a sheet of hiss, and a drop is a peak the steady sources never reach. Kurtosis is what
     * tells the two apart at equal level: a filtered noise settles near a Gaussian's 3, and every peak above
     * that pulls it up.
     */
    @Test
    fun theDropsPeakAboveWhatAFilteredNoiseEverDoes() {
        val rain = FloatArray(bufferSize).also { RainNoise(Random(seed)).fill(it) }
        val steady = FloatArray(bufferSize).also { LeakyBrownNoise(BROWN_NOISE_CUTOFF_HZ, Random(seed)).fill(it) }

        val rainKurtosis = kurtosis(rain)
        val steadyKurtosis = kurtosis(steady)

        assertTrue(
            "rain should peak far above a steady noise: rain $rainKurtosis, steady $steadyKurtosis",
            rainKurtosis > MINIMUM_DROP_KURTOSIS && rainKurtosis > steadyKurtosis * MINIMUM_KURTOSIS_FACTOR,
        )
    }

    /** Rain is heard high up; the brown the app ships is the opposite, and the two must not be confusable. */
    @Test
    fun rainCarriesFarMoreOfItsEnergyHighThanBrownDoes() {
        val rain = FloatArray(bufferSize).also { RainNoise(Random(seed)).fill(it) }
        val brown = FloatArray(bufferSize).also { LeakyBrownNoise(BROWN_NOISE_CUTOFF_HZ, Random(seed)).fill(it) }

        val rainEnergy = highBandEnergyAtUnitPeak(rain)
        val brownEnergy = highBandEnergyAtUnitPeak(brown)

        assertTrue(
            "rain should sit well above $AUDIBLE_BAND_SPLIT_HZ Hz: rain $rainEnergy, brown $brownEnergy",
            rainEnergy > MINIMUM_BRIGHTNESS_FACTOR * brownEnergy,
        )
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)

    /** Fourth moment over the square of the second: 3 for a Gaussian, 1.8 for the uniform white underneath. */
    private fun kurtosis(buffer: FloatArray): Double {
        val mean = buffer.sumOf { it.toDouble() } / buffer.size
        val second = buffer.sumOf {
            val d = it - mean
            d * d
        } / buffer.size
        val fourth = buffer.sumOf {
            val d = it - mean
            d * d * d * d
        } / buffer.size
        return fourth / (second * second)
    }

    private companion object {
        /**
         * A drop is a peak, and a source held to the shared RMS cannot also keep every peak inside full scale:
         * the two are the same trade the shipping pair already makes, and it clips about half a percent of its
         * samples where this clips a third of one. Tightening this any further would flatten the drops, which
         * are the source.
         */
        const val MAX_CLAMPED_SHARE = 0.005

        /** Comfortably above a Gaussian's 3, and far below what this source measures. */
        const val MINIMUM_DROP_KURTOSIS = 4.0

        const val MINIMUM_KURTOSIS_FACTOR = 1.3

        /**
         * Measured factor is ~2.8. It is not larger because the comparison is made at equal peak level and the
         * drops own rain's peaks, which costs its sheet some of the scale — the claim is only that rain is
         * plainly the brighter of the two, and half the measured margin is enough to fail anything that is not.
         */
        const val MINIMUM_BRIGHTNESS_FACTOR = 1.4
    }
}
