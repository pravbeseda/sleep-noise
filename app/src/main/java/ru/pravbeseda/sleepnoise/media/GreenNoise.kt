package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * Green: the middle of white kept and both ends dropped, which is the band an outdoor ambience carries most
 * of its weight in and the reason this one is sold as the sound of somewhere rather than as a colour.
 *
 * **Two poles on each side, not one.** A single pole falls at 6 dB per octave, and the band above a 1.2 kHz
 * corner is fifteen times wider than the band under it — so a one-pole tail still put more of this source's
 * energy above 2 kHz than inside its own passband, which is a slightly dull white and not a green at all.
 * Twelve dB per octave is what makes the passband the loudest part of the spectrum.
 *
 * The poles carry no normalising gains here, unlike the sources that mix two bands: there is one band, so
 * nothing has to be weighed against anything and [OUTPUT_GAIN] is the only level in the file.
 */
class GreenNoise(private val random: Random = Random.Default) : NoiseSource {
    private val bottomFirst = OnePole(BOTTOM_CUTOFF_HZ)
    private val bottomSecond = OnePole(BOTTOM_CUTOFF_HZ)
    private val topFirst = OnePole(TOP_CUTOFF_HZ)
    private val topSecond = OnePole(TOP_CUTOFF_HZ)

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            val aboveOnce = white - bottomFirst.low(white)
            val aboveTwice = aboveOnce - bottomSecond.low(aboveOnce)
            val band = topSecond.low(topFirst.low(aboveTwice))
            buffer[i] = (band * OUTPUT_GAIN).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        bottomFirst.reset()
        bottomSecond.reset()
        topFirst.reset()
        topSecond.reset()
    }

    private companion object {
        /** Under this is the weight green is defined by not having; the rumble belongs to brown. */
        const val BOTTOM_CUTOFF_HZ = 250.0

        /** And over this is the hiss it is defined by not having either, which belongs to white and violet. */
        const val TOP_CUTOFF_HZ = 1_200.0

        /** Measured RMS of the band above. */
        const val MIXED_RMS = 0.08804

        const val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / MIXED_RMS
    }
}
