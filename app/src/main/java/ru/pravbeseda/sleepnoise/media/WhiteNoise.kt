package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

private const val MIN_SAMPLE = -1.0
private const val MAX_SAMPLE = 1.0

class WhiteNoise(private val random: Random = Random.Default) : NoiseSource {
    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            buffer[i] = random.nextDouble(MIN_SAMPLE, MAX_SAMPLE).toFloat()
        }
    }

    override fun reset() {
        // Memoryless source: nothing carries over between buffers.
    }
}
