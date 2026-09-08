package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * The sections that put the inverse of the 40-phon equal-loudness contour on white noise: a low shelf, a
 * peak and a dip across the ear's most sensitive region, a high shelf, and the roll-off that stops the
 * whole thing at the top of the published table.
 *
 * Fitted to ISO 226:2003 rather than set by ear — 0.29 dB rms and 0.60 dB worst case against the tabulated
 * centres up to 10 kHz, which is what `GreyNoiseTest` asserts against the standard's own numbers.
 *
 * Two deliberate departures from the contour, both of them the same lesson this package has already paid
 * for at the other end of the band:
 *
 *  * **The boost is capped at +12 dB.** Taken literally the inverse contour asks for +60 dB at 20 Hz, which
 *    would leave 95 % of the source's energy under 100 Hz — [BrownNoise]'s subsonic wander again, spending
 *    the mixer's headroom on what no speaker returns.
 *  * **The response falls above 12.5 kHz**, where the table ends. The contour goes on rising past there, and
 *    following it would spend the source's level on a band most listeners do not hear at all.
 *
 * What that costs is worth stating: even so, grey puts most of its energy where the ear is least sensitive,
 * so at [NORMALISED_SOURCE_RMS] it is heard about 5.7 dB quieter than white at the same slider position.
 * That is the shape being right rather than the level being wrong — the slider is the answer to it, and
 * raising grey's own gain instead would cost every other channel the headroom.
 */
internal fun equalLoudnessSections(): List<Biquad> = listOf(
    Biquad.lowShelf(LOW_SHELF_HZ, LOW_SHELF_Q, CONTOUR_CAP_DB),
    Biquad.peaking(PRESENCE_HZ, PRESENCE_Q, PRESENCE_DB),
    Biquad.peaking(SENSITIVE_DIP_HZ, SENSITIVE_DIP_Q, SENSITIVE_DIP_DB),
    Biquad.highShelf(HIGH_SHELF_HZ, HIGH_SHELF_Q, HIGH_SHELF_DB),
    Biquad.lowPass(ROLL_OFF_HZ, BUTTERWORTH_Q),
)

/**
 * Grey: white weighted by the inverse of an equal-loudness contour, so that the band is heard as level
 * rather than measured as level. Where pink tilts by a rule and white tilts by nothing, grey follows a
 * curve someone measured on listeners — see [equalLoudnessSections] for which curve, how closely, and
 * where it deliberately stops following it.
 */
class GreyNoise(private val random: Random = Random.Default) : NoiseSource {
    private val contour = equalLoudnessSections()

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            val shaped = contour.fold(white) { running, section -> section.process(running) }
            buffer[i] = (shaped * OUTPUT_GAIN).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        contour.forEach { it.reset() }
    }

    private companion object {
        /** Measured RMS of the shaped white above, which is what the normalising gain divides out. */
        const val SHAPED_RMS = 1.339

        const val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / SHAPED_RMS
    }
}

/** How far the inverse contour is allowed to lift the bottom of the band. See [equalLoudnessSections]. */
private const val CONTOUR_CAP_DB = 12.0

private const val LOW_SHELF_HZ = 380.0
private const val LOW_SHELF_Q = 0.78

/** The ear's own rise either side of 1.5 kHz, which the inverse contour answers with a small lift. */
private const val PRESENCE_HZ = 1560.0
private const val PRESENCE_Q = 1.76
private const val PRESENCE_DB = 4.4

/** And the dip over 3 kHz, where the ear is at its most sensitive and grey therefore gives it least. */
private const val SENSITIVE_DIP_HZ = 3000.0
private const val SENSITIVE_DIP_Q = 0.72
private const val SENSITIVE_DIP_DB = -3.2

private const val HIGH_SHELF_HZ = 6000.0
private const val HIGH_SHELF_Q = 1.17
private const val HIGH_SHELF_DB = 12.4

/** The top of the ISO 226 table, and where this source stops following a curve it can no longer read. */
private const val ROLL_OFF_HZ = 12_500.0

/** A second-order Butterworth: 3 dB down at its own corner, 12 dB per octave above it. */
private const val BUTTERWORTH_Q = 0.7071
