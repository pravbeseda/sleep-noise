package ru.pravbeseda.sleepnoise.media

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * f^2 noise: the first difference of uniform white, which is the brightest of the named colours and the mirror
 * of what [BrownNoise] does by integrating the same input.
 *
 * The one source here that cannot clip. Successive samples of white are independent, so the difference has twice
 * their variance and at most twice their bound: normalising the first to [NORMALISED_SOURCE_RMS] puts the second
 * at 0.61 of full scale, a crest factor of 2.4 where every other source here runs near 5. So there is no clamp —
 * not a clamp that never fires, but a bound the arithmetic already carries, and `fill`'s contract is met by it.
 */
class VioletNoise(private val random: Random = Random.Default) : NoiseSource {
    private var previousWhite = 0.0

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            buffer[i] = ((white - previousWhite) * OUTPUT_GAIN).toFloat()
            previousWhite = white
        }
    }

    override fun reset() {
        previousWhite = 0.0
    }

    private companion object {
        /**
         * RMS of the difference, derived rather than measured: uniform noise on `[-1, 1]` has variance `1/3`, and
         * differencing two independent draws of it doubles that.
         */
        val DIFFERENCE_RMS = sqrt(2.0 / 3.0)

        val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / DIFFERENCE_RMS
    }
}
