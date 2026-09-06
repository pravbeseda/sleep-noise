package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * The one switch for the whole noise lab, read by the playback service as much as by the Activity:
 * a flag the UI alone honoured would leave a stored lab volume playing with no slider to turn it down.
 *
 * Putting the lab away is editing this to `false`. It is a `const val`, so the compiler inlines it and
 * every branch behind it becomes unreachable — the release build does not shrink (`isMinifyEnabled` is
 * false), so the candidate classes stay in the APK, unused. Their sources, preference keys and tests
 * deliberately stay in the tree too, which makes the next experiment a rebuild rather than a
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
 * mutable singleton it does not have. It takes the generator for the same reason `ShippingNoises` does — a
 * seeded one turns "this source depends on nothing but itself" into something a test can compare exactly.
 */
class NoiseLabCandidate(
    val preferenceKey: String,
    /** Where this candidate's checkbox is stored. Its own key, so switching one experiment off leaves the rest alone. */
    val enabledPreferenceKey: String,
    /** Developer-facing debug copy. The lab never reaches a user, so this is deliberately never translated. */
    val label: String,
    val createSource: (Random) -> NoiseSource,
)

/**
 * One candidate, with both of its preference keys derived from [name] so that a new experiment cannot be
 * given a key that belongs to another one by mistyping it.
 */
private fun candidate(name: String, label: String, createSource: (Random) -> NoiseSource) =
    NoiseLabCandidate("lab${name}NoiseVolume", "lab${name}NoiseEnabled", label, createSource)

/**
 * One [LeakyBrownNoise] on trial at [cutoffHz], which is the only thing that separates one of them from the
 * next: the cutoff is in the key and in the label, so the rows cannot be told apart by their position alone.
 */
private fun leakyBrown(cutoffHz: Int) =
    candidate("LeakyBrown$cutoffHz", "Leaky brown $cutoffHz Hz") { random -> LeakyBrownNoise(cutoffHz.toDouble(), random) }

/** Bright enough to sit close to pink: the top of the range worth judging by ear. */
private const val BRIGHT_LEAKY_BROWN_HZ = 250

/** Between the two, where the spectrum darkens while a phone speaker still returns most of it. */
private const val MID_LEAKY_BROWN_HZ = 120

/**
 * Every candidate under test, in the order their sliders appear. Adding another experiment is one entry
 * here plus one [NoiseSource]: the service's channels and the Activity's sliders are both built from this
 * list, so nothing else in the app carries a second copy of it.
 */
val NOISE_LAB_CANDIDATES: List<NoiseLabCandidate> = listOf(
    leakyBrown(BRIGHT_LEAKY_BROWN_HZ),
    leakyBrown(MID_LEAKY_BROWN_HZ),
    candidate("Surf", "Surf") { random -> SurfNoise(random) },
    candidate("Rain", "Rain") { random -> RainNoise(random) },
    candidate("WheelClatter", "Wheel clatter") { random -> WheelClatterNoise(random) },
)
