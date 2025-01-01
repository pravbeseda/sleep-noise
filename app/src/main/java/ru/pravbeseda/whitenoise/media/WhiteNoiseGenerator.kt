package ru.pravbeseda.whitenoise.media

import kotlin.random.Random

class WhiteNoiseGenerator : BaseNoiseGenerator() {
    override fun generateNoiseData(bufferSize: Int): ShortArray {
        val noiseData = ShortArray(bufferSize)
        for (i in noiseData.indices) {
            noiseData[i] = (Random.nextDouble(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return noiseData
    }
}
