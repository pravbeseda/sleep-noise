package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class BlueNoiseTest {
    private val bufferSize = 1 shl 16
    private val headSize = 64
    private val seed = 20260908
    private val rmsTolerance = 0.025

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        BlueNoise(Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        assertEquals("normalisation missed its target level", NORMALISED_SOURCE_RMS, rms(buffer), rmsTolerance)
    }

    @Test
    fun resetReturnsTheFilterToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = BlueNoise(random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        BlueNoise(Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    /**
     * Blue's place on the ladder, asserted against both neighbours at once: it rises where white is flat, and
     * it rises less steeply than violet, which is differenced white rather than differenced pink. A source
     * that merely tilted upwards would pass the first half and fail the second.
     */
    @Test
    fun blueTiltsUpMoreThanWhiteAndLessThanViolet() {
        val blue = highToLowRatio(FloatArray(bufferSize).also { BlueNoise(Random(seed)).fill(it) })
        val white = highToLowRatio(FloatArray(bufferSize).also { WhiteNoise(Random(seed)).fill(it) })
        val violet = highToLowRatio(FloatArray(bufferSize).also { VioletNoise(Random(seed)).fill(it) })

        assertTrue("blue does not tilt up at all: blue $blue, white $white", blue > white)
        assertTrue("blue tilts up as hard as violet: blue $blue, violet $violet", blue < violet)
    }

    private fun highToLowRatio(signal: FloatArray): Double {
        val shares = bandEnergyShares(signal, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)
        return shares.high / shares.low
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)

    private companion object {
        const val LOW_SPLIT_HZ = 300.0
        const val HIGH_SPLIT_HZ = 3_000.0
    }
}
