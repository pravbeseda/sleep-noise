package ru.pravbeseda.sleepnoise.media

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Flat noise: every frequency carries the same power, which uniform draws give for nothing — the distribution
 * a sample is drawn from sets the level, not the spectrum.
 *
 * The gain is what makes it a source among the others rather than a generator on its own terms. Raw uniform
 * draws on `[-1, 1]` come out at an RMS of `1/sqrt(3)`, which is 2.3 times [NORMALISED_SOURCE_RMS]: it was the
 * one source here not held to the shared level, harmless while it only ever fed a test that measures ratios,
 * and wrong the moment a slider reached it. At the shared level its peak is 0.43 of full scale, so like
 * [VioletNoise] it cannot clip and carries no clamp.
 */
class WhiteNoise(private val random: Random = Random.Default) : NoiseSource {
    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            buffer[i] = (random.nextDouble(-1.0, 1.0) * OUTPUT_GAIN).toFloat()
        }
    }

    override fun reset() {
        // Memoryless source: nothing carries over between buffers.
    }

    private companion object {
        /** Derived, not measured: uniform noise on `[-1, 1]` has variance `1/3`. */
        val UNIFORM_RMS = sqrt(1.0 / 3.0)

        val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / UNIFORM_RMS
    }
}
