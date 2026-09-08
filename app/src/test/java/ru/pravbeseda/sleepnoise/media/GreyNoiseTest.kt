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
     * What makes this grey rather than a taste: its response is the inverse of the 40-phon equal-loudness
     * contour, and the contour it is measured against is the published table, written out below rather than
     * derived from the very constants under test.
     *
     * The lift is capped at [CONTOUR_CAP_DB], which is the one place the source departs from the standard
     * inside the band it follows — [equalLoudnessSections] says why — so the target is the tabulated boost
     * or the cap, whichever is smaller.
     */
    @Test
    fun theShapeIsTheInverseOfTheFortyPhonContour() {
        val response = contourResponse()
        val atOneKilohertz = FORTY_PHON_SPL.getValue(REFERENCE_HZ)

        FORTY_PHON_SPL.filterKeys { it <= FITTED_TOP_HZ }.forEach { (frequency, spl) ->
            val target = (spl - atOneKilohertz).coerceAtMost(CONTOUR_CAP_DB)
            assertEquals("the contour at $frequency Hz", target, responseDb(response, frequency), CONTOUR_TOLERANCE_DB)
        }
    }

    /**
     * Above the table's last entry the contour goes on rising, and following it would spend the source's
     * level on a band most listeners cannot hear — the same bargain [LeakyBrownNoise] struck at the bottom.
     */
    @Test
    fun theTopOfTheBandFallsAwayInsteadOfFollowingTheContourPastTheTable() {
        val response = contourResponse()

        val fall = responseDb(response, FITTED_TOP_HZ) - responseDb(response, PAST_THE_TABLE_HZ)

        assertTrue("the response past the table falls only $fall dB", fall > MIN_ROLL_OFF_DB)
    }

    /**
     * What separates grey from the two sources it sits between, asserted against one of each: it lifts the
     * bottom the way pink does, and unlike pink it does not pay for that by rolling the top off.
     *
     * Both claims are ratios against the middle band, because the three shares sum to one — a lift at one end
     * shrinks every other share, so a bare share comparison reads a boosted end as a cut one. And the top is
     * measured against pink rather than against white, which is the comparison that says what being grey
     * rather than pink means.
     */
    @Test
    fun greyLiftsTheBottomLikePinkAndKeepsMoreOfTheTop() {
        val grey = bandEnergyShares(FloatArray(bufferSize).also { GreyNoise(Random(seed)).fill(it) }, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)
        val white = bandEnergyShares(FloatArray(bufferSize).also { WhiteNoise(Random(seed)).fill(it) }, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)
        val pink = bandEnergyShares(FloatArray(bufferSize).also { PinkNoise(Random(seed)).fill(it) }, LOW_SPLIT_HZ, HIGH_SPLIT_HZ)

        assertTrue("grey did not lift its bottom over white's: $grey vs $white", grey.low / grey.mid > white.low / white.mid)
        assertTrue("grey kept no more of its top than pink: $grey vs $pink", grey.high / grey.mid > pink.high / pink.mid)
    }

    /** One pass of an impulse through the very sections the source uses, which is the whole of its shape. */
    private fun contourResponse(): DoubleArray {
        val sections = equalLoudnessSections()
        return impulseResponse { sample -> sections.fold(sample) { running, section -> section.process(running) } }
    }

    private fun rms(buffer: FloatArray): Double = sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)

    private companion object {
        /** Under the low shelf's corner, so the band it lifts is measured and not the shoulder of it. */
        const val LOW_SPLIT_HZ = 300.0

        /** Over the 3-4 kHz the ear peaks at, so "the top" means where sensitivity falls off and not where it is best. */
        const val HIGH_SPLIT_HZ = 8_000.0

        /**
         * ISO 226:2003, the sound pressure level in dB that each frequency needs to be heard as loudly as
         * 40 dB at 1 kHz. Grey's response is the inverse of this, so where the table is high the source is loud.
         */
        val FORTY_PHON_SPL = mapOf(
            20.0 to 99.85, 25.0 to 93.94, 31.5 to 88.17, 40.0 to 82.63, 50.0 to 77.78,
            63.0 to 73.08, 80.0 to 68.48, 100.0 to 64.37, 125.0 to 60.59, 160.0 to 56.70,
            200.0 to 53.41, 250.0 to 50.40, 315.0 to 47.58, 400.0 to 44.98, 500.0 to 43.05,
            630.0 to 41.34, 800.0 to 40.06, 1000.0 to 40.01, 1250.0 to 41.82, 1600.0 to 42.51,
            2000.0 to 39.23, 2500.0 to 36.51, 3150.0 to 35.61, 4000.0 to 36.65, 5000.0 to 40.01,
            6300.0 to 45.83, 8000.0 to 51.80, 10000.0 to 54.28, 12500.0 to 51.49,
        )

        const val REFERENCE_HZ = 1000.0

        /** The lift the source stops at, stated here as well because the target is the smaller of the two. */
        const val CONTOUR_CAP_DB = 12.0

        /** The last tabulated centre the source still follows: past it the response is rolled off on purpose. */
        const val FITTED_TOP_HZ = 10_000.0

        /** Loose next to the 0.60 dB the fit actually lands on, and far inside a shelf set to the wrong gain. */
        const val CONTOUR_TOLERANCE_DB = 1.0

        const val PAST_THE_TABLE_HZ = 16_000.0

        /** The roll-off measures 10.1 dB over that span; the bound only has to see it fall at all. */
        const val MIN_ROLL_OFF_DB = 6.0
    }
}
