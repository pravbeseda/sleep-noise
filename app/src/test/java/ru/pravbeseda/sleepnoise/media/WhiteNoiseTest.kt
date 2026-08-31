package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WhiteNoiseTest {
    private val bufferSize = 1024
    private val seed = 42

    @Test
    fun fillProducesSamplesWithinRange() {
        val source = WhiteNoise()
        val buffer = FloatArray(bufferSize)

        source.fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
    }

    @Test
    fun sameSeedProducesSameSamples() {
        val first = FloatArray(bufferSize)
        val second = FloatArray(bufferSize)

        WhiteNoise(Random(seed)).fill(first)
        WhiteNoise(Random(seed)).fill(second)

        assertArrayEquals(second, first)
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { index, sample ->
            assertEquals("sample $index differs", sample, actual[index], 0.0f)
        }
    }
}
