package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The mix a user hears with every slider at the top, measured through the real mixing law.
 *
 * The mixer clamps its sum, so headroom is shared: a source that spends its level below the audible band still
 * takes that headroom from the others, and the clamp turns the shortfall into distortion that waxes and wanes
 * with the offending source rather than into a steady, forgivable colouring.
 */
class ShippingNoiseMixTest {
    @Test
    fun everyShippingNoiseAtFullVolumeBarelyReachesTheMixersClamp() {
        val mixer = NoiseMixer(
            listOf(
                shippingWhiteNoise(Random(WHITE_SEED)),
                shippingPinkNoise(Random(PINK_SEED)),
                shippingBrownNoise(Random(BROWN_SEED)),
            ),
        )
        val fullVolume = floatArrayOf(1.0f, 1.0f, 1.0f)
        val out = ShortArray(BUFFER_SIZE)

        var clipped = 0
        repeat(BUFFERS) {
            mixer.mix(fullVolume, out)
            clipped += out.count { it == Short.MAX_VALUE || it == NEGATIVE_FULL_SCALE }
        }

        val clippedShare = clipped.toDouble() / (BUFFER_SIZE.toDouble() * BUFFERS)
        assertTrue(
            "the shipping noises clip a share of $clippedShare of their samples at full volume, " +
                "so one source is eating the others' headroom",
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

        val brownEnergy = highBandEnergyAtUnitPeak(brown)
        val walkEnergy = highBandEnergyAtUnitPeak(walk)

        val factor = brownEnergy / walkEnergy
        assertTrue(
            "the shipping corner carries only $factor times the walk's energy above $AUDIBLE_BAND_SPLIT_HZ Hz: " +
                "it has drifted back towards the subsonic wander this replaced",
            factor > MIN_HIGH_BAND_FACTOR,
        )
    }

    private companion object {
        const val WHITE_SEED = 20260909
        const val PINK_SEED = 20260906
        const val BROWN_SEED = 20260907

        /** Twelve seconds at the engine's rate: long enough that a source wandering over about a second is judged fairly. */
        const val BUFFER_SIZE = 1 shl 14
        const val BUFFERS = 32

        /** The mixer's negative clamp, `-1.0f * Short.MAX_VALUE`, which is one short of `Short.MIN_VALUE`. */
        const val NEGATIVE_FULL_SCALE = (-Short.MAX_VALUE).toShort()

        /**
         * Measured at 1.97 % for the three shipping noises on these seeds, against 0.4-0.6 % for the pair that
         * shipped before white joined them and 8-13 % for the random walk brown once replaced. **The bound is
         * loose on purpose and the figure is the thing to watch**: white took the mix four times closer to the
         * clamp than the pair sat, and a fourth shipping source would spend what is left. What buys the headroom
         * back is [NORMALISED_SOURCE_RMS], which every source shares — so the next source to ship is the one
         * that has to move it, and this number is how that argument gets made.
         */
        const val MAX_CLIPPED_SHARE = 0.03

        const val MEASURED_SAMPLES = 1 shl 16

        /**
         * The shipping corner measures 7.09 on this seed, against 1.0 for the walk by definition. Verified to
         * bite where it matters: the test fails at 30 Hz and below and passes from 45 Hz up. Across seeds the two
         * ranges do overlap a little — 6.4-8.2 at 60 Hz against 3.7-5.1 at 30 Hz — so what separates them here is
         * the pinned seed and not the bound.
         */
        const val MIN_HIGH_BAND_FACTOR = 5.0
    }
}
