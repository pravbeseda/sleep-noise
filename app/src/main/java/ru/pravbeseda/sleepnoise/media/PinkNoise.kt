package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * 1/f noise: Paul Kellett's "economy" filter bank — six one-pole sections plus a direct and a
 * one-sample-delayed path — fed with uniform white noise.
 */
class PinkNoise(private val random: Random = Random.Default) : NoiseSource {
    private var section0 = 0.0
    private var section1 = 0.0
    private var section2 = 0.0
    private var section3 = 0.0
    private var section4 = 0.0
    private var section5 = 0.0
    private var delayed = 0.0

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            val white = random.nextDouble(-1.0, 1.0)
            section0 = (POLE_0 * section0) + (white * GAIN_0)
            section1 = (POLE_1 * section1) + (white * GAIN_1)
            section2 = (POLE_2 * section2) + (white * GAIN_2)
            section3 = (POLE_3 * section3) + (white * GAIN_3)
            section4 = (POLE_4 * section4) + (white * GAIN_4)
            section5 = (POLE_5 * section5) + (white * GAIN_5)
            val pink = section0 + section1 + section2 + section3 + section4 + section5 + delayed + (white * DIRECT_GAIN)
            delayed = white * DELAYED_GAIN
            buffer[i] = (pink * OUTPUT_GAIN).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        section0 = 0.0
        section1 = 0.0
        section2 = 0.0
        section3 = 0.0
        section4 = 0.0
        section5 = 0.0
        delayed = 0.0
    }

    private companion object {
        // Kellett's published coefficients: one pole and one input gain per section. The negative
        // GAIN_5 folds the minus sign of his `b5 = -0.7616 * b5 - white * 0.0168980` into the gain,
        // so every section reads the same way.
        const val POLE_0 = 0.99886
        const val POLE_1 = 0.99332
        const val POLE_2 = 0.96900
        const val POLE_3 = 0.86650
        const val POLE_4 = 0.55000
        const val POLE_5 = -0.7616
        const val GAIN_0 = 0.0555179
        const val GAIN_1 = 0.0750759
        const val GAIN_2 = 0.1538520
        const val GAIN_3 = 0.3104856
        const val GAIN_4 = 0.5329522
        const val GAIN_5 = -0.0168980
        const val DIRECT_GAIN = 0.5362
        const val DELAYED_GAIN = 0.115926

        /** RMS the bank puts out when fed uniform white in `[-1, 1]`; measured over 2^20 samples. */
        const val BANK_RMS = 1.75

        /** Peaks run about 4.6x the RMS at that level, so `fill`'s clamp bites on ~30 samples per million. */
        const val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / BANK_RMS
    }
}
