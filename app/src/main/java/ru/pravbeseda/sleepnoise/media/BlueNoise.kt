package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * f^1/2 noise: the first difference of [PinkNoise], which is the mirror of pink across white and sits halfway
 * to [VioletNoise].
 *
 * Differencing lifts a spectrum by 6 dB per octave, and pink falls at 3, so the difference of pink rises at 3
 * — the definition of blue. Building it out of the pink bank rather than a second set of coefficients means
 * the tilt is exactly the one `PinkNoiseTest` already measures, reflected, instead of a new approximation
 * nothing checks against it.
 *
 * Blue is the one colour here nobody reaches for to sleep — it is a dithering noise and a tinnitus one. It is
 * on trial to complete the ladder the app now covers from brown to violet, and to be measured against the two
 * either side of it.
 */
class BlueNoise(random: Random = Random.Default) : NoiseSource {
    private val pink = PinkNoise(random)

    private var previousPink = 0.0

    override fun fill(buffer: FloatArray) {
        pink.fill(buffer)
        for (i in buffer.indices) {
            val current = buffer[i].toDouble()
            buffer[i] = ((current - previousPink) * OUTPUT_GAIN).coerceIn(-1.0, 1.0).toFloat()
            previousPink = current
        }
    }

    override fun reset() {
        pink.reset()
        previousPink = 0.0
    }

    private companion object {
        /**
         * Measured RMS of the difference. Not derived like [VioletNoise]'s: successive samples of pink are
         * heavily correlated, so the doubling that holds for independent draws says nothing here.
         */
        const val DIFFERENCE_RMS = 0.149

        const val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / DIFFERENCE_RMS
    }
}
