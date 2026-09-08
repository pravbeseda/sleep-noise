package ru.pravbeseda.sleepnoise.media

import ru.pravbeseda.sleepnoise.BROWN_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.BROWN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.DEFAULT_BROWN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.DEFAULT_SILENT_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.GREEN_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.GREEN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.GREY_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.GREY_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.PINK_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.PINK_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.SURF_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.SURF_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.WHITE_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.WHITE_NOISE_VOLUME
import kotlin.random.Random

/**
 * Dark enough to sit where the clamped random walk this replaced sat by ear, and high enough that the corner is
 * inside the band a speaker returns. The walk cornered at ~3 Hz, so almost all of its level went on a subsonic
 * wander nothing reproduces — level that still cost the mixer's headroom and clipped whatever it played with.
 */
const val BROWN_NOISE_CUTOFF_HZ = 60.0

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
 * Every noise the app ships, in the order their rows appear: brown and white first as the two most reached
 * for, then the rest by how much use they are for sleeping.
 *
 * The screen builds one row per entry and the service one channel per entry, so a noise is added here and
 * nowhere else. Only brown starts audible; the other five open at zero, which is what keeps an install made
 * before they existed sounding exactly as it did.
 *
 * Named here rather than inside the service because the service is Android plumbing no JVM test can
 * construct, while how these six share the mixer's headroom is exactly the kind of claim a JVM test should
 * be making — `ShippingNoiseMixTest` makes it.
 */
val SHIPPING_NOISES: List<ShippingNoise> = listOf(
    ShippingNoise(
        BROWN_NOISE_VOLUME,
        BROWN_NOISE_ENABLED,
        DEFAULT_BROWN_NOISE_VOLUME,
        R.string.brown_noise_name,
        R.string.brown_noise_volume,
    ) { random -> LeakyBrownNoise(BROWN_NOISE_CUTOFF_HZ, random) },
    ShippingNoise(
        WHITE_NOISE_VOLUME,
        WHITE_NOISE_ENABLED,
        DEFAULT_SILENT_NOISE_VOLUME,
        R.string.white_noise_name,
        R.string.white_noise_volume,
    ) { random -> WhiteNoise(random) },
    ShippingNoise(
        PINK_NOISE_VOLUME,
        PINK_NOISE_ENABLED,
        DEFAULT_SILENT_NOISE_VOLUME,
        R.string.pink_noise_name,
        R.string.pink_noise_volume,
    ) { random -> PinkNoise(random) },
    ShippingNoise(
        SURF_NOISE_VOLUME,
        SURF_NOISE_ENABLED,
        DEFAULT_SILENT_NOISE_VOLUME,
        R.string.surf_noise_name,
        R.string.surf_noise_volume,
    ) { random -> SurfNoise(random) },
    ShippingNoise(
        GREY_NOISE_VOLUME,
        GREY_NOISE_ENABLED,
        DEFAULT_SILENT_NOISE_VOLUME,
        R.string.grey_noise_name,
        R.string.grey_noise_volume,
    ) { random -> GreyNoise(random) },
    ShippingNoise(
        GREEN_NOISE_VOLUME,
        GREEN_NOISE_ENABLED,
        DEFAULT_SILENT_NOISE_VOLUME,
        R.string.green_noise_name,
        R.string.green_noise_volume,
    ) { random -> GreenNoise(random) },
)
