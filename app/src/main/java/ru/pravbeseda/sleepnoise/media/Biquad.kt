package ru.pravbeseda.sleepnoise.media

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One second-order section, built from Robert Bristow-Johnson's cookbook formulas and run in transposed
 * direct form II — two state variables per section, one multiply-add chain per sample.
 *
 * It exists because [GreyNoise] follows a published curve rather than a taste, and a curve with a peak, a
 * dip and two shelves in it is not something [OnePole] can be talked into: a single pole falls 6 dB per
 * octave and cannot confine a boost, which this package has already paid to learn twice.
 *
 * A section is stateful, so each source builds its own — the same reason `NoiseLabCandidate` hands out a
 * factory rather than a shared instance.
 */
internal class Biquad private constructor(
    private val feedForwardNow: Double,
    private val feedForwardOnce: Double,
    private val feedForwardTwice: Double,
    private val feedBackOnce: Double,
    private val feedBackTwice: Double,
) {
    private var firstState = 0.0
    private var secondState = 0.0

    fun process(input: Double): Double {
        val output = (feedForwardNow * input) + firstState
        firstState = (feedForwardOnce * input) - (feedBackOnce * output) + secondState
        secondState = (feedForwardTwice * input) - (feedBackTwice * output)
        return output
    }

    fun reset() {
        firstState = 0.0
        secondState = 0.0
    }

    companion object {
        /** Lifts or cuts a band around [frequencyHz], leaving the rest of the spectrum where it was. */
        fun peaking(frequencyHz: Double, q: Double, gainDb: Double): Biquad {
            val shape = Shape(frequencyHz, q)
            val amplitude = amplitudeOf(gainDb)
            return normalised(
                1.0 + (shape.alpha * amplitude),
                -(2.0 * shape.cosine),
                1.0 - (shape.alpha * amplitude),
                1.0 + (shape.alpha / amplitude),
                -(2.0 * shape.cosine),
                1.0 - (shape.alpha / amplitude),
            )
        }

        /** Lifts everything under [frequencyHz] by [gainDb], and the corner itself by half of it. */
        fun lowShelf(frequencyHz: Double, q: Double, gainDb: Double): Biquad {
            val shape = Shape(frequencyHz, q)
            val a = amplitudeOf(gainDb)
            val slope = 2.0 * sqrt(a) * shape.alpha
            val cosine = shape.cosine
            return normalised(
                a * ((a + 1.0) - ((a - 1.0) * cosine) + slope),
                2.0 * a * ((a - 1.0) - ((a + 1.0) * cosine)),
                a * ((a + 1.0) - ((a - 1.0) * cosine) - slope),
                (a + 1.0) + ((a - 1.0) * cosine) + slope,
                -(2.0 * ((a - 1.0) + ((a + 1.0) * cosine))),
                (a + 1.0) + ((a - 1.0) * cosine) - slope,
            )
        }

        /** The mirror of [lowShelf]: everything over [frequencyHz]. */
        fun highShelf(frequencyHz: Double, q: Double, gainDb: Double): Biquad {
            val shape = Shape(frequencyHz, q)
            val a = amplitudeOf(gainDb)
            val slope = 2.0 * sqrt(a) * shape.alpha
            val cosine = shape.cosine
            return normalised(
                a * ((a + 1.0) + ((a - 1.0) * cosine) + slope),
                -(2.0 * a * ((a - 1.0) + ((a + 1.0) * cosine))),
                a * ((a + 1.0) + ((a - 1.0) * cosine) - slope),
                (a + 1.0) - ((a - 1.0) * cosine) + slope,
                2.0 * ((a - 1.0) - ((a + 1.0) * cosine)),
                (a + 1.0) - ((a - 1.0) * cosine) - slope,
            )
        }

        /** Two poles, so the fall above [frequencyHz] is 12 dB per octave rather than the 6 a single pole gives. */
        fun lowPass(frequencyHz: Double, q: Double): Biquad {
            val shape = Shape(frequencyHz, q)
            val opening = 1.0 - shape.cosine
            return normalised(
                opening / 2.0,
                opening,
                opening / 2.0,
                1.0 + shape.alpha,
                -(2.0 * shape.cosine),
                1.0 - shape.alpha,
            )
        }

        /** What every formula above shares: where the section sits in the band and how sharply it turns. */
        private class Shape(frequencyHz: Double, q: Double) {
            private val radiansPerSample = RADIANS_PER_CYCLE * frequencyHz / SAMPLE_RATE_HZ
            val cosine = cos(radiansPerSample)
            val alpha = sin(radiansPerSample) / (2.0 * q)
        }

        /** The cookbook's `A`: a gain enters the coefficients as the square root of an amplitude ratio. */
        private fun amplitudeOf(gainDb: Double): Double = DECIBEL_BASE.pow(gainDb / DECIBELS_PER_AMPLITUDE)

        @Suppress("LongParameterList") // Six coefficients are what a second-order section is.
        private fun normalised(
            feedForwardNow: Double,
            feedForwardOnce: Double,
            feedForwardTwice: Double,
            feedBackNow: Double,
            feedBackOnce: Double,
            feedBackTwice: Double,
        ) = Biquad(
            feedForwardNow / feedBackNow,
            feedForwardOnce / feedBackNow,
            feedForwardTwice / feedBackNow,
            feedBackOnce / feedBackNow,
            feedBackTwice / feedBackNow,
        )

        private const val RADIANS_PER_CYCLE = 2.0 * PI

        private const val DECIBEL_BASE = 10.0

        /** Forty and not twenty: `A` is the square root of the amplitude ratio the gain asks for. */
        private const val DECIBELS_PER_AMPLITUDE = 40.0
    }
}
