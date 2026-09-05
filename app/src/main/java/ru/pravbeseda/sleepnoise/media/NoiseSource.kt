package ru.pravbeseda.sleepnoise.media

/**
 * The rate every source in this package is generated at and [NoiseEngine] plays at.
 *
 * It lives here rather than on the engine because a filtered source needs it to turn a cutoff in hertz into a
 * pole, and this file is the one place in `media/` both an Android-free source and the engine can read.
 */
const val SAMPLE_RATE_HZ = 44100

/** A generator of raw noise samples in `[-1, 1]`, free of any audio-platform dependency. */
interface NoiseSource {
    fun fill(buffer: FloatArray)

    fun reset()
}
