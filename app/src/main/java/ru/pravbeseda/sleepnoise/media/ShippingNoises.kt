package ru.pravbeseda.sleepnoise.media

import ru.pravbeseda.sleepnoise.R
import kotlin.random.Random

/**
 * Dark enough to sit where the clamped random walk this replaced sat by ear, and high enough that the corner is
 * inside the band a speaker returns. The walk cornered at ~3 Hz, so almost all of its level went on a subsonic
 * wander nothing reproduces — level that still cost the mixer's headroom and clipped whatever it played with.
 */
const val BROWN_NOISE_CUTOFF_HZ = 60.0

/**
 * What a noise opens at when nobody has touched its slider. Every noise but brown starts silent: each of them
 * arrived after some install did, and a key that did not exist yet must not make a running app louder the day
 * it appears.
 */
const val DEFAULT_SILENT_NOISE_VOLUME = 0.0f

/**
 * Brown is the one an untouched install can be heard playing. It sits well under half because the level every
 * source shares came down when six of them started sharing the mixer's headroom, and a fresh install should
 * open quiet enough to fall asleep to rather than loud enough to reach for the slider.
 */
const val DEFAULT_BROWN_NOISE_VOLUME = 0.3f

/**
 * One noise the app ships: where its level and its switch are stored, what it starts at, what it is called,
 * and how to build it.
 *
 * [createSource] is a factory and not a shared instance for the reason [NoiseLabCandidate] gives: a filter
 * carries state, so one source handed to two channels would have them driving one filter.
 *
 * The two resource ids are plain `Int`s and this file imports no Android of its own, which is what keeps
 * `media/` buildable and testable off a device — `AndroidFreeSourcesTest` holds that line.
 */
class ShippingNoise(
    val volumeKey: String,
    val enabledKey: String,
    val defaultVolume: Float,
    /** The noise's name, which is what its toggle announces to a screen reader. */
    val nameRes: Int,
    /** The text over its slider, which takes the level as a percentage. */
    val volumeLabelRes: Int,
    val createSource: (Random) -> NoiseSource,
)

/**
 * One noise, with both of its preference keys derived from [name] so that a noise cannot be given a key that
 * belongs to another one by mistyping it — the same bargain `NoiseLab.kt` strikes with its candidates.
 *
 * The three names the app already shipped under — `white`, `pink` and `brown` — spell the keys those installs
 * already hold, so deriving them changes nothing in the store. **A name here is a stored key**: renaming one
 * loses every level saved under the old spelling.
 */
private fun shipping(name: String, defaultVolume: Float, nameRes: Int, volumeLabelRes: Int, createSource: (Random) -> NoiseSource) =
    ShippingNoise("${name}NoiseVolume", "${name}NoiseEnabled", defaultVolume, nameRes, volumeLabelRes, createSource)

val BROWN_NOISE = shipping("brown", DEFAULT_BROWN_NOISE_VOLUME, R.string.brown_noise_name, R.string.brown_noise_volume) {
    LeakyBrownNoise(BROWN_NOISE_CUTOFF_HZ, it)
}

val WHITE_NOISE = shipping("white", DEFAULT_SILENT_NOISE_VOLUME, R.string.white_noise_name, R.string.white_noise_volume) {
    WhiteNoise(it)
}

val PINK_NOISE = shipping("pink", DEFAULT_SILENT_NOISE_VOLUME, R.string.pink_noise_name, R.string.pink_noise_volume) {
    PinkNoise(it)
}

val SURF_NOISE = shipping("surf", DEFAULT_SILENT_NOISE_VOLUME, R.string.surf_noise_name, R.string.surf_noise_volume) {
    SurfNoise(it)
}

val GREY_NOISE = shipping("grey", DEFAULT_SILENT_NOISE_VOLUME, R.string.grey_noise_name, R.string.grey_noise_volume) {
    GreyNoise(it)
}

val GREEN_NOISE = shipping("green", DEFAULT_SILENT_NOISE_VOLUME, R.string.green_noise_name, R.string.green_noise_volume) {
    GreenNoise(it)
}

/**
 * Every noise the app ships, in the order their rows appear: brown and white first as the two most reached
 * for, then the rest by how much use they are for sleeping.
 *
 * The screen builds one row per entry and the service one channel per entry, so a noise is added here and
 * nowhere else. Only brown starts audible.
 *
 * Named here rather than inside the service because the service is Android plumbing no JVM test can
 * construct, while how these six share the mixer's headroom is exactly the kind of claim a JVM test should
 * be making — `ShippingNoiseMixTest` makes it.
 */
val SHIPPING_NOISES: List<ShippingNoise> = listOf(BROWN_NOISE, WHITE_NOISE, PINK_NOISE, SURF_NOISE, GREY_NOISE, GREEN_NOISE)
