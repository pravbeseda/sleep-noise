package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class GreyNoiseTest {
    private val bufferSize = 1 shl 16
    private val headSize = 64
    private val seed = 20260908
    private val rmsTolerance = 0.025

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        GreyNoise(Random(seed)).fill(buffer)

        buffer.forEachIndexed { index, sample ->
            assertTrue("sample $index out of range: $sample", sample >= -1.0f && sample <= 1.0f)
        }
        assertEquals("normalisation missed its target level", NORMALISED_SOURCE_RMS, rms(buffer), rmsTolerance)
    }

    @Test
    fun resetReturnsTheFilterToItsInitialState() {
        val random = RewindableRandom(seed)
        val source = GreyNoise(random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        GreyNoise(Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    /**
     * What separates grey from the two sources it sits between, asserted against one of each: it lifts the
     * bottom the way pink does, and unlike pink it does not pay for that by rolling the top off.
     *
     * Both claims are ratios against the middle band, because the three shares sum to one — a lift at one end
     * shrinks every other share, so a bare share comparison reads a boosted end as a cut one. And the top is
     * measured against pink rather than against white: a one-pole low shelf big enough to be heard reaches
     * into the middle whatever its corner, so grey's top does not out-weigh its own middle and never will.
     * What it can do, and what being grey rather than pink means, is out-weigh pink's.
     */
    @Test
    fun greyLiftsTheBottomLikePinkAndKeepsMoreOfTheTop() {
        val grey = bandEnergyShares(FloatArray(bufferSize).also { GreyNoise(Random(seed)).fill(it) }, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)
        val white = bandEnergyShares(FloatArray(bufferSize).also { WhiteNoise(Random(seed)).fill(it) }, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)
        val pink = bandEnergyShares(FloatArray(bufferSize).also { PinkNoise(Random(seed)).fill(it) }, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)

        assertTrue("grey did not lift its bottom over white's: $grey vs $white", grey.low / grey.mid > white.low / white.mid)
        assertTrue("grey kept no more of its top than pink: $grey vs $pink", grey.high / grey.mid > pink.high / pink.mid)
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)

    private companion object {
        /** Under the low shelf's corner, so the band it lifts is measured and not the shoulder of it. */
        const val LOW_SPLIT_HZ = 300.0

        /** Over the 3-4 kHz the ear peaks at, so "the top" means where sensitivity falls off and not where it is best. */
        const val HIGH_SPLIT_HZ = 8_000.0
    }
}
