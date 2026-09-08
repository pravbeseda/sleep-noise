package ru.pravbeseda.sleepnoise.media

/**
 * The rate every source in this package is generated at and [NoiseEngine] plays at.
 *
 * It lives here rather than on the engine because a filtered source needs it to turn a cutoff in hertz into a
 * pole, and this file is the one place in `media/` both an Android-free source and the engine can read.
 */
const val SAMPLE_RATE_HZ = 44100

/**
 * The level a normalised source scales itself to, a little under a sixth of full scale.
 *
 * One value for all of them rather than one per source: sources are judged against each other by ear, and two
 * normalised to different levels cannot be compared at equal slider positions. It leaves room for the peaks —
 * every source here runs a crest factor near five — and each source clamps what still overshoots.
 *
 * It was a quarter of full scale while three sources shipped, and moved when six did: the mixer clamps its
 * sum, so the level every source shares is what decides how much of that sum survives. Six at a quarter clip
 * 8.2 % of their samples together, which is a crackle rather than a colouring; at this level they clip 0.5 %,
 * which is where the pair of pink and brown sat before white joined them. The cost is that every source is
 * 4 dB quieter than it was, and the slider is what answers that.
 */
const val NORMALISED_SOURCE_RMS = 0.1575

/** A generator of raw noise samples in `[-1, 1]`, free of any audio-platform dependency. */
interface NoiseSource {
    fun fill(buffer: FloatArray)

    fun reset()
}
