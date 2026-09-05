package ru.pravbeseda.sleepnoise.media

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Plays every channel through a single [AudioTrack], mixed in software by [NoiseMixer].
 *
 * One writer thread serves the whole engine: the first [start] creates it, it parks between sessions
 * and it exits on [release]. That thread is the sole owner of the track — it builds it, plays it and
 * releases it in its own `finally`, so no other thread can ever see a released track. Everything the
 * caller changes while playing — the channel volumes — reaches it through volatile fields it reads
 * once per cycle.
 *
 * **No caller ever waits for that thread.** [start], [stop] and [release] take a lock the writer holds
 * only long enough to read the intent out of it, so they return within microseconds however long the
 * `AudioTrack.write()` in flight takes to drain. A stop the writer has not noticed yet leaves it
 * finishing that write; a start arriving meanwhile is answered by the same thread once it has torn the
 * old session down, which is what keeps two tracks from ever overlapping without anything blocking on a
 * `join()` to arrange it. Reusing the thread rather than spawning one per session also means an app
 * flapping audio focus costs a flag and a signal, not a thread and a stack per flap.
 *
 * The sources are never reset, so a stop/start cycle resumes the brown integrator where it left off —
 * the behaviour the app has today.
 *
 * [start], [stop] and [release] are expected on one thread (the app's main thread). The first two are
 * each a no-op when the engine is already in the state they ask for, and all three are a no-op once
 * the engine has been released.
 */
class NoiseEngine(private val channels: List<NoiseChannel>) {
    private val mixer = NoiseMixer(channels.map { it.source })

    /** What the caller last asked for, which is not what the writer is doing yet. [RELEASED] is final. */
    private enum class State { STOPPED, PLAYING, RELEASED }

    private val lock = ReentrantLock()
    private val stateChanged = lock.newCondition()

    /** All three guarded by [lock], the writer's reads of [state] included. */
    private var state = State.STOPPED

    /** Counts the runs of [State.PLAYING], so the writer can tell its own session from a newer one. */
    private var session = 0
    private var writer: Thread? = null

    fun start() {
        lock.withLock {
            if (state != State.STOPPED) return
            state = State.PLAYING
            session++
            if (writer == null) {
                // Held over the lock only until the new thread's first read of the state it is about to serve.
                writer = Thread(::writeWhilePlaying, THREAD_NAME).apply { start() }
            } else {
                stateChanged.signalAll()
            }
        }
    }

    /** Silences the engine without waiting for the writer to notice: it does so within one write. */
    fun stop() {
        // No signal: the writer waits on the condition only while the state is already STOPPED.
        lock.withLock { if (state == State.PLAYING) state = State.STOPPED }
    }

    /**
     * Ends the writer thread for good. Like [stop] it returns immediately; the thread finishes the write
     * in flight, releases its track and exits on its own, holding nothing that has to outlive the engine.
     */
    fun release() {
        lock.withLock {
            state = State.RELEASED
            stateChanged.signalAll()
        }
    }

    private fun writeWhilePlaying() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (true) {
            playOneSession(awaitPlaying() ?: return)
        }
    }

    /** Parks until [start] asks for sound again, and answers null once [release] has ended the engine. */
    private fun awaitPlaying(): Int? = lock.withLock {
        while (state == State.STOPPED) stateChanged.await()
        if (state == State.PLAYING) session else null
    }

    private fun playOneSession(session: Int) {
        // A device that cannot serve this format is left to fail: an error size turns the chunk length
        // negative and dies allocating it, and a device that refuses the track dies in the builder. The
        // alternative — logging and returning — is a silence with the UI still showing playback.
        val minBufferSizeBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_MASK, ENCODING)
        // A track buffer of several minimum buffers keeps the hardware fed across a scheduling hiccup;
        // one write covers a fraction of it, so the writer never takes a whole buffer to notice a stop.
        val bufferSizeBytes = minBufferSizeBytes * BUFFERS_AHEAD
        val chunkSamples = bufferSizeBytes / BYTES_PER_SAMPLE / WRITES_PER_BUFFER
        val chunk = ShortArray(chunkSamples)
        val volumes = FloatArray(channels.size)

        val track = buildTrack(bufferSizeBytes)
        try {
            track.play()
            while (isPlaying()) {
                channels.forEachIndexed { index, channel -> volumes[index] = channel.volume }
                mixer.mix(volumes, chunk)
                val written = track.write(chunk, 0, chunk.size)
                if (written < 0) {
                    // A dead track reports itself here rather than by throwing; looping on it would spin.
                    Log.e(TAG, "AudioTrack.write failed with $written; stopping playback.")
                    stopFromWriter(session)
                    break
                }
            }
            track.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Audio playback failed", e)
            stopFromWriter(session)
        } finally {
            track.release()
        }
    }

    /**
     * Deliberately blind to the session number: a stop and a start the writer never got to see leave it
     * playing on the track it already has, which is the stop/start behaviour the engine has always had.
     */
    private fun isPlaying(): Boolean = lock.withLock { state == State.PLAYING }

    /**
     * Drops the caller's request, because this session is ending for a reason the caller does not know
     * about — a dead track, not a stop. Only its own session is dropped: a [start] that arrived while
     * the write was failing is a newer request than anything this session can speak for, and leaving it
     * standing is what has the writer build a fresh track for it instead of parking over it.
     */
    private fun stopFromWriter(session: Int) {
        lock.withLock { if (state == State.PLAYING && this.session == session) state = State.STOPPED }
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

    internal companion object {
        const val TAG = "NoiseEngine"
        const val THREAD_NAME = "NoiseEngine"
        const val SAMPLE_RATE_HZ = 44100
        const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** PCM 16-bit mono: the one place a byte count turns into a sample count. */
        const val BYTES_PER_SAMPLE = 2

        /** How many minimum buffers the track holds. */
        const val BUFFERS_AHEAD = 4

        /**
         * How many writes fill that track buffer. One write is how long the writer takes to notice a stop,
         * and so how long the next start waits before it can build its track — no caller blocks for it any
         * more, which leaves this the knob between how promptly a session turns over and the number of
         * wakeups over a night. It stays at 2 until there is a measurement to move it: on the emulator a
         * single `write()` takes far longer than the audio it carries, so nothing there can tell the two
         * settings apart.
         */
        const val WRITES_PER_BUFFER = 2
    }
}
