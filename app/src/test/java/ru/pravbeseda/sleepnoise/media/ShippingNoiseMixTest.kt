package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The mix a user hears with both sliders at the top, measured through the real mixing law.
 *
 * The mixer clamps its sum, so headroom is shared: a source that spends its level below the audible band still
 * takes that headroom from the other source, and the clamp turns the shortfall into distortion that waxes and
 * wanes with the offending source rather than into a steady, forgivable colouring.
 */
class ShippingNoiseMixTest {
    @Test
    fun bothNoisesAtFullVolumeBarelyReachTheMixersClamp() {
        val mixer = NoiseMixer(listOf(shippingPinkNoise(Random(PINK_SEED)), shippingBrownNoise(Random(BROWN_SEED))))
        val fullVolume = floatArrayOf(1.0f, 1.0f)
        val out = ShortArray(BUFFER_SIZE)

        var clipped = 0
        repeat(BUFFERS) {
            mixer.mix(fullVolume, out)
            clipped += out.count { it == Short.MAX_VALUE || it == NEGATIVE_FULL_SCALE }
        }

        val clippedShare = clipped.toDouble() / (BUFFER_SIZE.toDouble() * BUFFERS)
        assertTrue(
            "the shipping pair clips a share of $clippedShare of its samples at full volume, " +
                "so one source is eating the other's headroom",
            clippedShare < MAX_CLIPPED_SHARE,
        )
    }

    /**
     * The mixing law alone cannot hold the corner where it was put: the source normalises to
     * [NORMALISED_SOURCE_RMS] from its own pole, so the clipped share above barely moves with the cutoff and the
     * bound there still passes with the corner set back to the walk's own ~3 Hz. This is what holds it, in the
     * measure [LeakyBrownNoiseTest] judges a cutoff by: the shipping brown has to reach the audible band.
     */
    @Test
    fun theShippingBrownReachesTheAudibleBandWhereTheRandomWalkDoesNot() {
        val brown = FloatArray(MEASURED_SAMPLES).also { shippingBrownNoise(Random(BROWN_SEED)).fill(it) }
        val walk = FloatArray(MEASURED_SAMPLES).also { BrownNoise(Random(BROWN_SEED)).fill(it) }

        val brownEnergy = highBandEnergyAtUnitPeak(brown, BAND_SPLIT_HZ)
        val walkEnergy = highBandEnergyAtUnitPeak(walk, BAND_SPLIT_HZ)

        val factor = brownEnergy / walkEnergy
        assertTrue(
            "the shipping corner carries only $factor times the walk's energy above $BAND_SPLIT_HZ Hz: " +
                "it has drifted back towards the subsonic wander this replaced",
            factor > MIN_HIGH_BAND_FACTOR,
        )
    }

    private companion object {
        const val PINK_SEED = 20260906
        const val BROWN_SEED = 20260907

        /** Twelve seconds at the engine's rate: long enough that a source wandering over about a second is judged fairly. */
        const val BUFFER_SIZE = 1 shl 14
        const val BUFFERS = 32

        /** The mixer's negative clamp, `-1.0f * Short.MAX_VALUE`, which is one short of `Short.MIN_VALUE`. */
        const val NEGATIVE_FULL_SCALE = (-Short.MAX_VALUE).toShort()

        /**
         * Measured at 0.4-0.6% for the shipping pair and 8-13% for the random walk it replaced, across five seeds.
         * The bound sits between them with an order of magnitude of room on either side.
         */
        const val MAX_CLIPPED_SHARE = 0.02

        /** Where LeakyBrownNoiseTest splits audible from subsonic, so the two tests judge a cutoff the same way. */
        const val BAND_SPLIT_HZ = 200.0

        const val MEASURED_SAMPLES = 1 shl 16

        /**
         * Measured 6.4-8.2 for the shipping 60 Hz across seeds, 3.7-5.1 for 30 Hz and 1.0 for the walk itself.
         * The floor sits below the shipping corner's worst seed and above the next octave down, so it fails on a
         * corner moved back towards the subsonic and not on an unlucky seed.
         */
        const val MIN_HIGH_BAND_FACTOR = 5.0
    }
}
