package ru.pravbeseda.sleepnoise.media

import kotlin.math.exp
import kotlin.random.Random

/**
 * Rain: a sheet of hiss with the weight of the downpour under it, and drops landing on top of both.
 *
 * A drop here is a short burst of the same bright band rather than a tone: water hitting a surface is
 * broadband, and a pitched ping reads as a cave rather than as a window. Drops that land while another is
 * still fading simply take the envelope over — at this rate they overlap constantly, and the ear hears the
 * crackle of the pair either way, which is cheaper than keeping a voice per drop.
 */
class RainNoise(private val random: Random = Random.Default) : NoiseSource {
    private val sheet = OnePole(SHEET_CUTOFF_HZ)
    private val body = OnePole(BODY_CUTOFF_HZ)

    private var dropLevel = 0.0

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            val bright = (white - sheet.low(white)) * sheet.highGain
            val low = body.low(white) * body.lowGain

            if (random.nextDouble() < DROP_PROBABILITY) {
                dropLevel = random.nextDouble(QUIETEST_DROP, 1.0)
            }
            val mixed = (bright * SHEET_WEIGHT) + (low * BODY_WEIGHT) + (bright * dropLevel * DROP_WEIGHT)
            dropLevel *= DROP_DECAY

            buffer[i] = (mixed * OUTPUT_GAIN).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        sheet.reset()
        body.reset()
        dropLevel = 0.0
    }

    private companion object {
        /** Above it is the hiss of rain on a surface; the drops are struck out of the same band. */
        const val SHEET_CUTOFF_HZ = 1_200.0

        /** The weight underneath, which is what a downpour has and a tap running does not. */
        const val BODY_CUTOFF_HZ = 400.0

        const val SHEET_WEIGHT = 1.0
        const val BODY_WEIGHT = 0.35

        /** Loud enough that a drop is heard over the sheet it lands on, quiet enough not to read as a click. */
        const val DROP_WEIGHT = 4.0

        const val QUIETEST_DROP = 0.4

        /**
         * Drops a second. Sparse enough that most of them land on a sheet nothing else is happening on, which
         * is what makes a drop audible as one: at three times this rate they overlap into a second hiss.
         */
        const val DROPS_PER_SECOND = 8.0
        const val DROP_PROBABILITY = DROPS_PER_SECOND / SAMPLE_RATE_HZ

        /** A drop is gone in about 10 ms, which is what makes it a tick rather than a swell. */
        const val DROP_DECAY_SECONDS = 0.010
        val DROP_DECAY = exp(-1.0 / (DROP_DECAY_SECONDS * SAMPLE_RATE_HZ))

        /** Measured RMS of the mix above. */
        const val MIXED_RMS = 0.809

        const val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / MIXED_RMS
    }
}
