package ru.pravbeseda.sleepnoise.media

import kotlin.math.exp
import kotlin.random.Random

/**
 * Surf: one wave after another, each swelling and washing out, over a bed that never falls silent.
 *
 * Two bands rather than one, because that is what a wave does to the sound rather than to the volume: the
 * low rumble is always there and only breathes with the wave, while the bright spray belongs to the break
 * and is gone by the time the water has run back. Weighing them against each other is what makes the second
 * band worth having, and the weights are meaningful only because [OnePole] hands both bands back at the
 * level of the white they came from.
 *
 * Wave lengths are drawn one at a time instead of running off a fixed period: surf that repeats exactly is
 * heard as a machine within a minute or two, which is the opposite of the point.
 */
class SurfNoise(private val random: Random = Random.Default) : NoiseSource {
    private val rumble = OnePole(RUMBLE_CUTOFF_HZ)
    private val spray = OnePole(SPRAY_CUTOFF_HZ)

    private var waveLengthSamples = 0
    private var wavePosition = 0

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            if (wavePosition >= waveLengthSamples) startWave()
            val envelope = envelopeAt(wavePosition++)
            val white = random.nextDouble(-1.0, 1.0)
            val low = rumble.low(white) * rumble.lowGain
            val high = (white - spray.low(white)) * spray.highGain
            val mixed = (low * (WASH_FLOOR + envelope) * RUMBLE_WEIGHT) + (high * envelope * envelope * SPRAY_WEIGHT)
            buffer[i] = (mixed * OUTPUT_GAIN).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        rumble.reset()
        spray.reset()
        waveLengthSamples = 0
        wavePosition = 0
    }

    private fun startWave() {
        waveLengthSamples = random.nextInt(SHORTEST_WAVE_SAMPLES, LONGEST_WAVE_SAMPLES)
        wavePosition = 0
    }

    /**
     * The shape of one wave: a straight swell up to the break, then a wash that runs out under an exponential.
     * The asymmetry is the whole of it — a symmetrical envelope reads as a siren, not as water.
     */
    private fun envelopeAt(position: Int): Double {
        val swellSamples = (waveLengthSamples * SWELL_SHARE).toInt()
        if (position < swellSamples) return position.toDouble() / swellSamples
        val washed = (position - swellSamples).toDouble() / (waveLengthSamples - swellSamples)
        return exp(-WASH_DECAY * washed)
    }

    private companion object {
        /** The body of the wave: everything a phone speaker actually returns of the water itself. */
        const val RUMBLE_CUTOFF_HZ = 300.0

        /** Where the spray lives. Above it the break hisses; below it the wave would only get louder. */
        const val SPRAY_CUTOFF_HZ = 1_200.0

        const val RUMBLE_WEIGHT = 1.0

        /** Quieter than the rumble even at the break: spray that matches it in level reads as static. */
        const val SPRAY_WEIGHT = 0.45

        /** What is left between waves. Surf never goes quiet, and a bed that does sounds like a fault. */
        const val WASH_FLOOR = 0.3

        const val SHORTEST_WAVE_SAMPLES = 7 * SAMPLE_RATE_HZ
        const val LONGEST_WAVE_SAMPLES = 13 * SAMPLE_RATE_HZ

        /** Where in the wave the break falls: a slow gather, then most of the wave spent running out. */
        const val SWELL_SHARE = 0.35

        /** How far the wash falls by the end of the wave: `e^-3`, near enough to the floor to meet the next swell. */
        const val WASH_DECAY = 3.0

        /** Measured RMS of the mix above through the loudest second of a wave, the break itself. */
        const val BREAK_RMS = 0.74

        const val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / BREAK_RMS
    }
}
