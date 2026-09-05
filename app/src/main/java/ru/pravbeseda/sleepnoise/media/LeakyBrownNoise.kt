package ru.pravbeseda.sleepnoise.media

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Brown noise as a one-pole low-pass on white noise: flat below the corner frequency and 1/f^2 above it.
 *
 * `BrownNoise` is a clamped random walk whose corner sits around 3 Hz, so almost all of its power is spent on a
 * subsonic wander no phone speaker reproduces. Moving the corner into the audible band is the lever; adding gain
 * is not, since it only clips.
 *
 * The cutoff is a constructor parameter because it is the knob this class exists to try by ear — production takes
 * the default, the tests drive more than one value.
 */
class LeakyBrownNoise(cutoffHz: Double = DEFAULT_CUTOFF_HZ, private val random: Random = Random.Default) : NoiseSource {
    private val smoothing = exp(-RADIANS_PER_CYCLE * cutoffHz / SAMPLE_RATE_HZ)

    /**
     * The unity-DC-gain form below has output variance `varWhite * (1 - a) / (1 + a)`, so the normalising gain
     * follows from the pole instead of being measured and pasted, and stays correct at any cutoff.
     */
    private val outputGain = TARGET_RMS / sqrt(WHITE_VARIANCE * (1.0 - smoothing) / (1.0 + smoothing))

    private var state = 0.0

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            state = (smoothing * state) + ((1.0 - smoothing) * white)
            buffer[i] = (state * outputGain).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        state = 0.0
    }

    private companion object {
        /** Well inside the band a phone speaker reproduces; the value to move first when judging the result by ear. */
        const val DEFAULT_CUTOFF_HZ = 250.0

        const val RADIANS_PER_CYCLE = 2.0 * PI

        /** Must match `NoiseEngine.SAMPLE_RATE_HZ`; declared here so this file stays free of `android.*`. */
        const val SAMPLE_RATE_HZ = 44_100.0

        /** Variance of the uniform white input over `[-1, 1]`. */
        const val WHITE_VARIANCE = 1.0 / 3.0

        /** A quarter of full scale, as in `PinkNoise`: peaks run a few times the RMS, so `fill`'s clamp rarely bites. */
        const val TARGET_RMS = 0.25
    }
}
