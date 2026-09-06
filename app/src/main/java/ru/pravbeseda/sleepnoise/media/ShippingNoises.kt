package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * Dark enough to sit where the clamped random walk this replaced sat by ear, and high enough that the corner is
 * inside the band a speaker returns. The walk cornered at ~3 Hz, so almost all of its level went on a subsonic
 * wander nothing reproduces — level that still cost the mixer's headroom and clipped whatever it played with.
 */
const val BROWN_NOISE_CUTOFF_HZ = 60.0

/**
 * What the two shipping sliders are wired to. Named here rather than built inside the service because the
 * service is Android plumbing no JVM test can construct, and how the shipping pair shares the mixer's headroom
 * is exactly the kind of claim a JVM test should be making.
 */
fun shippingPinkNoise(random: Random = Random.Default): NoiseSource = PinkNoise(random)

fun shippingBrownNoise(random: Random = Random.Default): NoiseSource = LeakyBrownNoise(BROWN_NOISE_CUTOFF_HZ, random)
