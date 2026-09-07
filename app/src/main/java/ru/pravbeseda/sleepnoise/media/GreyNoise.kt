package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * Grey: white with both ends of the band lifted over its middle, because the ear is least sensitive at the
 * bottom and less sensitive again at the top, so a flat spectrum is not heard as a flat one.
 *
 * A true grey noise weights white by the inverse of a published equal-loudness contour. This is a coarse
 * three-band stand-in for that curve: full-band white as the base, with a low shelf and a smaller high shelf
 * added on top. The weights are set by ear rather than fitted to a curve, which is why it is a lab candidate
 * and not something the app ships — promoting it means fitting the real contour first.
 *
 * Both shelves come off [OnePole], whose gains put either half of a split back at the level of the white it
 * was fed, so the weights below are read against that base rather than against whatever the cutoff happened
 * to leave.
 */
class GreyNoise(private val random: Random = Random.Default) : NoiseSource {
    private val lowShelf = OnePole(LOW_SHELF_HZ)
    private val highShelf = OnePole(HIGH_SHELF_HZ)

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            val low = lowShelf.low(white) * lowShelf.lowGain
            val high = (white - highShelf.low(white)) * highShelf.highGain
            val mixed = white + (low * LOW_WEIGHT) + (high * HIGH_WEIGHT)
            buffer[i] = (mixed * OUTPUT_GAIN).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        lowShelf.reset()
        highShelf.reset()
    }

    private companion object {
        /** Where the ear starts losing the bottom of the band, roughly: below this the contour falls away fast. */
        const val LOW_SHELF_HZ = 250.0

        /** And where it starts losing the top, far more gently — hence the much smaller weight. */
        const val HIGH_SHELF_HZ = 8_000.0

        const val LOW_WEIGHT = 1.5
        const val HIGH_WEIGHT = 1.0

        /** Measured RMS of the mix above. */
        const val MIXED_RMS = 1.504

        const val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / MIXED_RMS
    }
}
