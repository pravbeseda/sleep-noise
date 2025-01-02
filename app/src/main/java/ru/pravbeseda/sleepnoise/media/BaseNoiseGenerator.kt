package ru.pravbeseda.sleepnoise.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

abstract class BaseNoiseGenerator {
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100
    private var volume: Float = 1.0f

    abstract fun generateNoiseData(bufferSize: Int): ShortArray

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0.0f, 1.0f)
        audioTrack?.apply {
            setVolume(this@BaseNoiseGenerator.volume)
        }
    }

    fun startNoise() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = minBufferSize * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.apply {
            setVolume(volume)
            play()
        }

        Log.d("NoiseGenerator", "AudioTrack State: ${audioTrack?.playState}")

        Thread {
            Log.d("NoiseGenerator", "Thread started for noise playback.")
            while (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val noiseData = generateNoiseData(bufferSize)
                audioTrack?.write(noiseData, 0, noiseData.size)
            }
            Log.d("NoiseGenerator", "Thread stopped.")
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stopNoise() {
        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
    }
}
