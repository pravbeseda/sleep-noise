package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class WheelClatterNoiseTest {
    private val seed = 20260906
    private val headSize = 64

    /** Half a minute, which holds enough joints for their rhythm to be counted rather than guessed at. */
    private val bufferSize = SAMPLE_RATE_HZ * 30

    private val expectedRms = NORMALISED_SOURCE_RMS
    private val rmsTolerance = 0.025

    @Test
    fun fillProducesSamplesWithinRange() {
        val buffer = FloatArray(bufferSize)

        WheelClatterNoise(Random(seed)).fill(buffer)

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
        val source = WheelClatterNoise(random)
        source.fill(FloatArray(bufferSize))

        source.reset()
        random.rewind()
        val afterReset = FloatArray(headSize)
        source.fill(afterReset)

        val fresh = FloatArray(headSize)
        WheelClatterNoise(Random(seed)).fill(fresh)
        assertArrayEquals("a reset source does not start where a fresh one does", fresh, afterReset, 0.0f)
    }

    /**
     * The sound is the rhythm: a bogie takes a rail joint with two axles a fraction of a second apart, and the
     * next joint is seconds away. Both gaps have to be there — a steady tick is a clock, and one thump per
     * joint is a hammer.
     */
    @Test
    fun theThumpsFallInPairsWithTheJointsSecondsApart() {
        val buffer = FloatArray(bufferSize).also { WheelClatterNoise(Random(seed)).fill(it) }

        val gaps = onsetsIn(buffer).zipWithNext { earlier, later -> (later - earlier).toDouble() / SAMPLE_RATE_HZ }

        val withinBogie = gaps.count { it < BOGIE_GAP_LIMIT_SECONDS }
        val betweenJoints = gaps.count { it > BOGIE_GAP_LIMIT_SECONDS }
        assertTrue(
            "gaps between thumps over the half minute: $withinBogie within a bogie, $betweenJoints between joints",
            withinBogie >= MINIMUM_OF_EACH_GAP && betweenJoints >= MINIMUM_OF_EACH_GAP,
        )
        assertTrue("no gap between joints reached a second: ${gaps.maxOrNull()}", gaps.any { it > 1.0 })
    }

    /** A train is heard from underneath: what a phone speaker returns of it is the rumble, not a hiss. */
    @Test
    fun theClatterIsDarkerThanRainIs() {
        val clatter = FloatArray(bufferSize).also { WheelClatterNoise(Random(seed)).fill(it) }
        val rain = FloatArray(bufferSize).also { RainNoise(Random(seed)).fill(it) }

        val clatterEnergy = highBandEnergyAtUnitPeak(clatter)
        val rainEnergy = highBandEnergyAtUnitPeak(rain)

        assertTrue(
            "the clatter should carry far less above $AUDIBLE_BAND_SPLIT_HZ Hz than rain: " +
                "clatter $clatterEnergy, rain $rainEnergy",
            clatterEnergy * MINIMUM_DARKNESS_FACTOR < rainEnergy,
        )
    }

    /**
     * Where the signal jumps: the level is followed with a fast one-pole envelope, and a crossing of a
     * threshold set from the whole take counts as a thump once the previous one has had time to fade.
     */
    private fun onsetsIn(buffer: FloatArray): List<Int> {
        val smoothing = 0.999
        var envelope = 0.0
        val followed = DoubleArray(buffer.size)
        for (i in buffer.indices) {
            envelope = (smoothing * envelope) + ((1.0 - smoothing) * abs(buffer[i].toDouble()))
            followed[i] = envelope
        }
        val threshold = followed.average() * ONSET_THRESHOLD_FACTOR
        val refractory = (REFRACTORY_SECONDS * SAMPLE_RATE_HZ).toInt()

        val onsets = mutableListOf<Int>()
        var index = 1
        while (index < followed.size) {
            if (followed[index] > threshold && followed[index - 1] <= threshold) {
                onsets += index
                index += refractory
            } else {
                index++
            }
        }
        return onsets
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)

    private companion object {
        /**
         * A thump is a peak, on the same terms as rain's drops — see the bound in `RainNoiseTest` for why a
         * source of them spends samples in the clamp at all.
         *
         * Higher than rain's because the clatter clips more: 0.46-0.57 % of its own samples across the seeds
         * tried, 0.47 % on the one pinned here. At rain's 0.5 % this passed on this seed and failed on three of
         * six others, which is a bound that asserts the seed rather than the source. Whether the thumps should
         * simply be quieter is a judgement to make by ear, and the lab is where that is made.
         */
        const val MAX_CLAMPED_SHARE = 0.008

        /** Between the two gaps the source produces, and nowhere near either of them. */
        const val BOGIE_GAP_LIMIT_SECONDS = 0.8

        /** Half a minute holds a dozen joints; ten of each gap fails a source that only ever makes one. */
        const val MINIMUM_OF_EACH_GAP = 5

        const val ONSET_THRESHOLD_FACTOR = 1.6
        const val REFRACTORY_SECONDS = 0.15

        /** Measured margin is wider; this fails anything that is not plainly the darker of the two. */
        const val MINIMUM_DARKNESS_FACTOR = 2.0
    }
}
