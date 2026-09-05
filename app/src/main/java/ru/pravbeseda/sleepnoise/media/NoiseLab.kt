package ru.pravbeseda.sleepnoise.media

/**
 * The one switch for the whole noise lab, read by the playback service as much as by the Activity:
 * a flag the UI alone honoured would leave a stored lab volume playing with no slider to turn it down.
 *
 * Putting the lab away is editing this to `false`. It is a `const val`, so it is inlined and R8 drops
 * every branch behind it out of a release build; the candidate sources, their preference keys and
 * their tests deliberately stay in the tree, which makes the next experiment a rebuild rather than a
 * re-implementation.
 */
const val NOISE_LAB_ENABLED = true

/** What an unmoved lab slider is worth, so an existing install sounds exactly as it does today. */
const val DEFAULT_LAB_NOISE_VOLUME = 0.0f

/**
 * One experimental source on trial, with the key its slider persists under and the label that slider carries.
 *
 * [createSource] is a factory rather than a shared instance on purpose: a filter carries state, so one cached
 * source handed to two channels would have them drive one filter, and this package would grow the hidden
 * mutable singleton it does not have.
 */
class NoiseLabCandidate(
    val preferenceKey: String,
    /** Developer-facing debug copy. The lab never reaches a user, so this is deliberately never translated. */
    val label: String,
    val createSource: () -> NoiseSource,
)

/**
 * Every candidate under test, in the order their sliders appear. Adding a third experiment is one entry
 * here plus one [NoiseSource]: the service's channels and the Activity's sliders are both built from this
 * list, so nothing else in the app carries a second copy of it.
 */
val NOISE_LAB_CANDIDATES: List<NoiseLabCandidate> = listOf(
    NoiseLabCandidate("labPinkNoiseVolume", "Pink") { PinkNoise() },
    NoiseLabCandidate("labLeakyBrownNoiseVolume", "Leaky brown") { LeakyBrownNoise() },
)
