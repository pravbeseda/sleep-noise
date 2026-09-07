package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class WhiteNoiseTest {
    /** Long enough for the RMS below to be the distribution's and not the seed's. */
    private val bufferSize = 1 shl 16
    private val seed = 42
    private val rmsTolerance = 0.025

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        WhiteNoise().fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        assertEquals("normalisation missed its target level", NORMALISED_SOURCE_RMS, rms(buffer), rmsTolerance)
    }

    @Test
    fun sameSeedProducesSameSamples() {
        val first = FloatArray(bufferSize)
        val second = FloatArray(bufferSize)

        WhiteNoise(Random(seed)).fill(first)
        WhiteNoise(Random(seed)).fill(second)

        assertArrayEquals(first, second, 0.0f)
    }

    @Test
    fun differentSeedsProduceDifferentSamples() {
        val first = FloatArray(bufferSize)
        val other = FloatArray(bufferSize)

        WhiteNoise(Random(seed)).fill(first)
        WhiteNoise(Random(seed + 1)).fill(other)

        assertFalse("both seeds produced the same buffer", first.contentEquals(other))
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)
}
