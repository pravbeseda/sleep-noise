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
 * The cutoff has no default because it is the knob this class exists to try by ear: the lab puts one candidate on
 * the screen per value it wants judged, so every caller names the value it is judging.
 */
class LeakyBrownNoise(cutoffHz: Double, private val random: Random = Random.Default) : NoiseSource {
    private val smoothing = exp(-RADIANS_PER_CYCLE * cutoffHz / SAMPLE_RATE_HZ)

    /**
     * The unity-DC-gain form below has output variance `varWhite * (1 - a) / (1 + a)`, so the normalising gain
     * follows from the pole instead of being measured and pasted, and stays correct at any cutoff.
     */
    private val outputGain = NORMALISED_SOURCE_RMS / sqrt(WHITE_VARIANCE * (1.0 - smoothing) / (1.0 + smoothing))

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
        const val RADIANS_PER_CYCLE = 2.0 * PI

        /** Variance of the uniform white input over `[-1, 1]`. */
        const val WHITE_VARIANCE = 1.0 / 3.0
    }
}
