package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

class WhiteNoise(private val random: Random = Random.Default) : NoiseSource {
    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            buffer[i] = random.nextDouble(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        // Memoryless source: nothing carries over between buffers.
    }
}
