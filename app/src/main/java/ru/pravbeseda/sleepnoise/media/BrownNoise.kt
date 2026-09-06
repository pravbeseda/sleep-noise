package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * A clamped random walk: the brown noise the app used to ship, cornered at ~3 Hz.
 *
 * It no longer sounds anywhere — [LeakyBrownNoise] at [BROWN_NOISE_CUTOFF_HZ] took its slider, because almost all
 * of the walk's level sits below the audible band and the mixer's clamp charged the other channels for it. It
 * stays as the reference `LeakyBrownNoiseTest` measures that difference against.
 */
class BrownNoise(private val random: Random = Random.Default) : NoiseSource {
    private var lastOut = 0.0

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            lastOut = (lastOut + (INTEGRATION_RATE * white)).coerceIn(-1.0, 1.0)
            buffer[i] = lastOut.toFloat()
        }
    }

    override fun reset() {
        lastOut = 0.0
    }

    private companion object {
        /** How far one sample may move the integrator; the whole difference between brown and white noise. */
        const val INTEGRATION_RATE = 0.02
    }
}
