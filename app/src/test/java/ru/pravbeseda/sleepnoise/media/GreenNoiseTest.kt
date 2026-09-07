package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class GreenNoiseTest {
    private val bufferSize = 1 shl 16
    private val headSize = 64
    private val seed = 20260908
    private val rmsTolerance = 0.025

    /** Measured share is ~0.95; half of it still fails for anything that is not a hump in the middle. */
    private val minimumMidShare = 0.45

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        GreenNoise(Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        assertEquals("normalisation missed its target level", NORMALISED_SOURCE_RMS, rms(buffer), rmsTolerance)
    }

    @Test
    fun resetReturnsTheFilterToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = GreenNoise(random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        GreenNoise(Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    @Test
    fun greenKeepsItsEnergyInTheMiddleOfTheBand() {
        val green = FloatArray(bufferSize).also { GreenNoise(Random(seed)).fill(it) }
        val white = FloatArray(bufferSize).also { WhiteNoise(Random(seed)).fill(it) }

        val greenShares = bandEnergyShares(green, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)
        val whiteShares = bandEnergyShares(white, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)

        assertTrue("green is not a hump in the middle: $greenShares", greenShares.mid > minimumMidShare)
        assertTrue(
            "green holds no more of its energy in the middle than white: $greenShares vs $whiteShares",
            greenShares.mid > whiteShares.mid,
        )
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)

    private companion object {
        const val LOW_SPLIT_HZ = 250.0
        const val HIGH_SPLIT_HZ = 2_000.0
    }
}
