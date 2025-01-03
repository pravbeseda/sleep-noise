package ru.pravbeseda.sleepnoise.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseNoiseGenerator {
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100
    private var volume: Float = 1.0f
    private val isPlaying = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)

    abstract fun generateNoiseData(bufferSize: Int): ShortArray

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0.0f, 1.0f)
        audioTrack?.apply {
            setVolume(this@BaseNoiseGenerator.volume)
        }
    }

    fun startNoise() {
        if (isPlaying.get()) return

        isPlaying.set(true)
        isStopped.set(false)

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

        Thread {
            try {
                Log.d("NoiseGenerator", "Thread started for noise playback.")
                while (isPlaying.get()) {
                    val noiseData = generateNoiseData(bufferSize)
                    audioTrack?.let {
                        if (it.state == AudioTrack.STATE_INITIALIZED && !isStopped.get()) {
                            it.write(noiseData, 0, noiseData.size)
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e("NoiseGenerator", "Error during audio playback: ${e.message}")
            } finally {
                stopNoiseInternal()
                Log.d("NoiseGenerator", "Thread stopped.")
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stopNoise() {
        if (!isPlaying.get()) return

        isPlaying.set(false)
        isStopped.set(true)

        stopNoiseInternal()
    }

    private fun stopNoiseInternal() {
        synchronized(this) {
            try {
                audioTrack?.apply {
                    if (state == AudioTrack.STATE_INITIALIZED) {
                        stop()
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e("NoiseGenerator", "Error stopping audio playback: ${e.message}")
            } finally {
                try {
                    audioTrack?.release()
                } catch (e: IllegalStateException) {
                    Log.e("NoiseGenerator", "Error releasing audio playback: ${e.message}")
                }
                audioTrack = null
                Log.d("NoiseGenerator", "AudioTrack released.")
            }
        }
    }
}
