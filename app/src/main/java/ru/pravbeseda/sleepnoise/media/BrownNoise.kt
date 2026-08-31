package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

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
