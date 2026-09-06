package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class LeakyBrownNoiseTest {
    private val bufferSize = 1 shl 16
    private val headSize = 64
    private val seed = 20260905

    /**
     * The normalisation target, pinned from both sides: a floor on the peak alone passes a gain three times too
     * large, which clips a fifth of the samples.
     */
    private val expectedRms = NORMALISED_SOURCE_RMS
    private val rmsTolerance = 0.025

    /** Above the random walk's ~3 Hz corner and below the leaky source's, so the two land on opposite sides of it. */
    private val bandSplitHz = 200.0

    /** Measured factor on this seed is ~16; half of it is clear of the noise and still fails a corner left subsonic. */
    private val minimumHighBandFactor = 8.0

    /** Measured factor across the decade below is ~2.4; 1.5 clears an implementation that ignores its cutoff. */
    private val minimumCutoffFactor = 1.5

    /** Where a single settled value is needed: the brightest cutoff the lab has on trial. */
    private val cutoffHz = 250.0

    private val lowerCutoffHz = 100.0
    private val higherCutoffHz = 1_000.0

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        LeakyBrownNoise(cutoffHz, Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        val rms = rms(buffer)
        assertEquals("normalisation missed its target level", expectedRms, rms, rmsTolerance)
    }

    @Test
    fun resetReturnsTheFilterToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = LeakyBrownNoise(cutoffHz, random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        LeakyBrownNoise(cutoffHz, Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    @Test
    fun leakyBrownReachesTheAudibleBandWhereTheRandomWalkDoesNot() {
        val leaky = FloatArray(bufferSize).also { LeakyBrownNoise(cutoffHz, Random(seed)).fill(it) }
        val walk = FloatArray(bufferSize).also { BrownNoise(Random(seed)).fill(it) }

        val leakyEnergy = highBandEnergyAtUnitPeak(leaky, bandSplitHz)
        val walkEnergy = highBandEnergyAtUnitPeak(walk, bandSplitHz)

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

        val higherEnergy = highBandEnergyAtUnitPeak(higher, bandSplitHz)
        val lowerEnergy = highBandEnergyAtUnitPeak(lower, bandSplitHz)

        assertTrue(
            "$higherCutoffHz Hz should put more energy above $bandSplitHz Hz than $lowerCutoffHz Hz: " +
                "higher $higherEnergy, lower $lowerEnergy, factor ${higherEnergy / lowerEnergy}",
            higherEnergy > minimumCutoffFactor * lowerEnergy,
        )
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)
}
