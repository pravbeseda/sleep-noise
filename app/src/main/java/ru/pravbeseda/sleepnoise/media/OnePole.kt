package ru.pravbeseda.sleepnoise.media

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The one-pole low-pass the lab's three own sources are built from — surf, rain and the clatter — and the
 * high-pass that is whatever it leaves behind. [LeakyBrownNoise], which ships, still carries its own copy of
 * the same pole: folding it onto this is a change to a shipping source and belongs in its own PR.
 *
 * It carries its own normalising gains rather than leaving them to the caller: a band's level follows from
 * its cutoff, so a source that mixes two of them can only weigh them against each other once both are back
 * at the level of the white they were fed. Both gains are derived from the pole, not measured, so they stay
 * right at any cutoff — [LeakyBrownNoise] states the same algebra for the low half.
 */
internal class OnePole(cutoffHz: Double) {
    private val smoothing = exp(-RADIANS_PER_CYCLE * cutoffHz / SAMPLE_RATE_HZ)

    private var state = 0.0

    /** Brings the low half back to the RMS of the white input: its variance is `varWhite * (1 - a) / (1 + a)`. */
    val lowGain = sqrt((1.0 + smoothing) / (1.0 - smoothing))

    /**
     * The same for the high half. `white - low` has variance `varWhite * (1 + (1-a)/(1+a) - 2(1-a))`, since the
     * low half's only correlation with the input is the `(1 - a)` share of the current sample it just took.
     */
    val highGain = 1.0 / sqrt(1.0 + ((1.0 - smoothing) / (1.0 + smoothing)) - (2.0 * (1.0 - smoothing)))

    /** The low half of [white]; the high half is `white - low(white)`. */
    fun low(white: Double): Double {
        state = (smoothing * state) + ((1.0 - smoothing) * white)
        return state
    }

    fun reset() {
        state = 0.0
    }

    private companion object {
        const val RADIANS_PER_CYCLE = 2.0 * PI
    }
}
