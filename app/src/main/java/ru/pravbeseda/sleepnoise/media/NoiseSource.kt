package ru.pravbeseda.sleepnoise.media

/**
 * The rate every source in this package is generated at and [NoiseEngine] plays at.
 *
 * It lives here rather than on the engine because a filtered source needs it to turn a cutoff in hertz into a
 * pole, and this file is the one place in `media/` both an Android-free source and the engine can read.
 */
const val SAMPLE_RATE_HZ = 44100

/**
 * The level a normalised source scales itself to, a quarter of full scale.
 *
 * One value for all of them rather than one per source: candidates are judged against each other by ear, and
 * two sources normalised to different levels cannot be compared at equal slider positions. It leaves room for
 * the peaks — every source here runs a crest factor near five — and each source clamps what still overshoots.
 */
const val NORMALISED_SOURCE_RMS = 0.25

/** A generator of raw noise samples in `[-1, 1]`, free of any audio-platform dependency. */
interface NoiseSource {
    fun fill(buffer: FloatArray)

    fun reset()
}
