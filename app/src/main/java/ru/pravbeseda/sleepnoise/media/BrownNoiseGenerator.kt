package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

class BrownNoiseGenerator : BaseNoiseGenerator() {
    private var lastOut = 0.0

    override fun generateNoiseData(bufferSize: Int): ShortArray {
        val noiseData = ShortArray(bufferSize)
        for (i in noiseData.indices) {
            val white = Random.nextDouble(-1.0, 1.0)
            lastOut = (lastOut + (0.02 * white)).coerceIn(-1.0, 1.0)
            noiseData[i] = (lastOut * Short.MAX_VALUE).toInt().toShort()
        }
        return noiseData
    }
}

