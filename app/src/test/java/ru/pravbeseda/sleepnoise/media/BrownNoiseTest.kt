package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BrownNoiseTest {
    private val bufferSize = 1024
    private val maxStep = 0.02f
    private val tolerance = 1e-6f

    /** Always draws the top of the requested range, so the integrator can only walk towards the upper clamp. */
    private class AlwaysMaxRandom : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextDouble(): Double = 1.0
    }

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        BrownNoise().fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        assertTrue("fill left the buffer silent", buffer.any { it != 0.0f })
    }

    @Test
    fun consecutiveSamplesNeverStepFurtherThanTheIntegrationRate() {
        val buffer = FloatArray(bufferSize)

        BrownNoise().fill(buffer)

        var previous = 0.0f
        buffer.forEachIndexed { index, sample ->
            assertTrue("step at $index too large: ${abs(sample - previous)}", abs(sample - previous) <= maxStep + tolerance)
            previous = sample
        }
        assertTrue("fill left the buffer silent", buffer.any { it != 0.0f })
    }

    @Test
    fun biasedRandomSaturatesAtTheClamp() {
        val buffer = FloatArray(bufferSize)

        BrownNoise(AlwaysMaxRandom()).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index ran past the clamp: $sample", sample <= 1.0f)
        }
        assertEquals("integrator did not reach the clamp", 1.0f, buffer.last(), 0.0f)
    }

    @Test
    fun resetReturnsTheIntegratorToZero() {
        val source = BrownNoise(AlwaysMaxRandom())
        val saturated = FloatArray(bufferSize)
        source.fill(saturated)
        assertEquals("precondition: buffer should be saturated", 1.0f, saturated.last(), 0.0f)

        source.reset()

        val afterReset = FloatArray(bufferSize)
        source.fill(afterReset)
        assertEquals("first sample after reset should be one step away from zero", maxStep, afterReset.first(), tolerance)
    }
}
