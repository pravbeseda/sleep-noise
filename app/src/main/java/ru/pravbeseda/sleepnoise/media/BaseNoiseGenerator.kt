package ru.pravbeseda.sleepnoise.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseNoiseGenerator(private val source: NoiseSource) {
    private var audioTrack: AudioTrack? = null
    private var volume: Float = 1.0f
    private val isPlaying = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)

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
        source.reset()

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        val bufferSize = minBufferSize * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.apply {
            setVolume(volume)
            play()
        }

        Thread {
            val samples = FloatArray(bufferSize)
            val noiseData = ShortArray(bufferSize)
            try {
                Log.d("NoiseGenerator", "Thread started for noise playback.")
                while (isPlaying.get()) {
                    source.fill(samples)
                    for (i in samples.indices) {
                        noiseData[i] = (samples[i] * Short.MAX_VALUE).toInt().toShort()
                    }
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

    private companion object {
        const val SAMPLE_RATE_HZ = 44100
    }
}
