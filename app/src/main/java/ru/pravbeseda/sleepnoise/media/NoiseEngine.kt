package ru.pravbeseda.sleepnoise.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * Plays every channel through a single [AudioTrack], mixed in software by [NoiseMixer].
 *
 * The writer thread is the sole owner of the track: it builds it, plays it and releases it in its own
 * `finally`, so no other thread can ever see a released track. Everything the caller changes while
 * playing — the channel volumes — reaches the thread through volatile fields it reads once per cycle.
 *
 * The sources are never reset, so a stop/start cycle resumes the brown integrator where it left off —
 * the behaviour the app has today.
 *
 * [start] and [stop] are expected on one thread (the app's main thread) and are each a no-op when the
 * engine is already in the state they ask for.
 */
class NoiseEngine(private val channels: List<NoiseChannel>) {
    private val mixer = NoiseMixer(channels.map { it.source })

    @Volatile
    private var running = false
    private var writer: Thread? = null

    fun start() {
        if (running) return
        running = true
        writer = Thread(::writeUntilStopped, THREAD_NAME).apply { start() }
    }

    fun stop() {
        running = false
        writer?.join()
        writer = null
    }

    private fun writeUntilStopped() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val minBufferSizeBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_MASK, ENCODING)
        if (minBufferSizeBytes <= 0) {
            Log.e(TAG, "AudioTrack reports no usable buffer size ($minBufferSizeBytes); not playing.")
            running = false
            return
        }
        // A track buffer of several minimum buffers keeps the hardware fed across a scheduling hiccup;
        // one write covers a fraction of it, so stop() never waits for a whole buffer to drain.
        val bufferSizeBytes = minBufferSizeBytes * BUFFERS_AHEAD
        val chunkSamples = bufferSizeBytes / BYTES_PER_SAMPLE / WRITES_PER_BUFFER
        val chunk = ShortArray(chunkSamples)
        val volumes = FloatArray(channels.size)

        val track = buildTrack(bufferSizeBytes)
        try {
            track.play()
            while (running) {
                channels.forEachIndexed { index, channel -> volumes[index] = channel.volume }
                mixer.mix(volumes, chunk)
                val written = track.write(chunk, 0, chunk.size)
                if (written < 0) {
                    // A dead track reports itself here rather than by throwing; looping on it would spin.
                    Log.e(TAG, "AudioTrack.write failed with $written; stopping playback.")
                    break
                }
            }
            track.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Audio playback failed", e)
        } finally {
            running = false
            track.release()
        }
    }

    private fun buildTrack(bufferSizeBytes: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_HZ)
                .setEncoding(ENCODING)
                .setChannelMask(CHANNEL_MASK)
                .build(),
        )
        .setBufferSizeInBytes(bufferSizeBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private companion object {
        const val TAG = "NoiseEngine"
        const val THREAD_NAME = "NoiseEngine"
        const val SAMPLE_RATE_HZ = 44100
        const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** PCM 16-bit mono: the one place a byte count turns into a sample count. */
        const val BYTES_PER_SAMPLE = 2

        /** How many minimum buffers the track holds. */
        const val BUFFERS_AHEAD = 4

        /** How many writes fill that track buffer. */
        const val WRITES_PER_BUFFER = 2
    }
}
