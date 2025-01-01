package ru.pravbeseda.whitenoise.media

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.random.Random

class BrownNoiseGenerator {
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100

    fun startNoise() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )

        audioTrack?.play()
        Thread {
            val noiseData = ShortArray(bufferSize)
            var lastOut = 0.0
            while (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                for (i in noiseData.indices) {
                    val white = Random.nextDouble(-1.0, 1.0)
                    lastOut = (lastOut + (0.02 * white)).coerceIn(-1.0, 1.0)
                    noiseData[i] = (lastOut * Short.MAX_VALUE).toInt().toShort()
                }
                audioTrack?.write(noiseData, 0, noiseData.size)
            }
        }.start()
    }

    fun stopNoise() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
