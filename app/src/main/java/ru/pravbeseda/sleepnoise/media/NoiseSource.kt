package ru.pravbeseda.sleepnoise.media

/** A generator of raw noise samples in `[-1, 1]`, free of any audio-platform dependency. */
interface NoiseSource {
    fun fill(buffer: FloatArray)

    fun reset()
}
