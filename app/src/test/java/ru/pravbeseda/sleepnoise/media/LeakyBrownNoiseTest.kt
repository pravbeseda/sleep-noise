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

class LeakyBrownNoiseTest {
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

    /** Above the shipping walk's ~3 Hz corner and below the leaky source's, so the two land on opposite sides of it. */
    private val bandSplitHz = 200.0

    /** Measured factor on this seed is ~16; half of it is clear of the noise and still fails a corner left subsonic. */
    private val minimumHighBandFactor = 8.0

    /** Measured factor across the decade below is ~2.4; 1.5 clears an implementation that ignores its cutoff. */
    private val minimumCutoffFactor = 1.5

    private val lowerCutoffHz = 100.0
    private val higherCutoffHz = 1_000.0

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        LeakyBrownNoise(random = Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        val rms = rms(buffer)
        assertEquals("normalisation missed its target level", expectedRms, rms, rmsTolerance)
    }

    @Test
    fun resetReturnsTheFilterToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = LeakyBrownNoise(random = random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        LeakyBrownNoise(random = Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    @Test
    fun leakyBrownReachesTheAudibleBandWhereTheShippingWalkDoesNot() {
        val leaky = FloatArray(bufferSize).also { LeakyBrownNoise(random = Random(seed)).fill(it) }
        val walk = FloatArray(bufferSize).also { BrownNoise(Random(seed)).fill(it) }

        val leakyEnergy = highBandEnergyAtUnitPeak(leaky)
        val walkEnergy = highBandEnergyAtUnitPeak(walk)

        assertTrue(
            "at equal peak level the leaky source should carry far more energy above $bandSplitHz Hz: " +
                "leaky $leakyEnergy, walk $walkEnergy, factor ${leakyEnergy / walkEnergy}",
            leakyEnergy > minimumHighBandFactor * walkEnergy,
        )
    }

    @Test
    fun aHigherCutoffMovesMoreEnergyIntoTheHighBand() {
        val higher = FloatArray(bufferSize).also { LeakyBrownNoise(higherCutoffHz, Random(seed)).fill(it) }
        val lower = FloatArray(bufferSize).also { LeakyBrownNoise(lowerCutoffHz, Random(seed)).fill(it) }

        val higherEnergy = highBandEnergyAtUnitPeak(higher)
        val lowerEnergy = highBandEnergyAtUnitPeak(lower)

        assertTrue(
            "$higherCutoffHz Hz should put more energy above $bandSplitHz Hz than $lowerCutoffHz Hz: " +
                "higher $higherEnergy, lower $lowerEnergy, factor ${higherEnergy / lowerEnergy}",
            higherEnergy > minimumCutoffFactor * lowerEnergy,
        )
    }

    /**
     * Scales the signal to a peak of exactly 1 — the level the mixer's headroom actually constrains — then splits it
     * with a one-pole low-pass and its complementary high-pass and reports the mean energy of the high half.
     * Cheaper than an FFT and enough for a claim about how much of a spectrum is audible at all.
     */
    private fun highBandEnergyAtUnitPeak(signal: FloatArray): Double {
        val scale = 1.0 / signal.maxOf { abs(it) }
        val smoothing = exp(-2.0 * PI * bandSplitHz / sampleRate)
        var low = 0.0
        var highEnergy = 0.0
        for (sample in signal) {
            val scaled = sample * scale
            low = (smoothing * low) + ((1.0 - smoothing) * scaled)
            val high = scaled - low
            highEnergy += high * high
        }
        return highEnergy / signal.size
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)
}
