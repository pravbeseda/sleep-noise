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
    }
}
