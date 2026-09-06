package ru.pravbeseda.sleepnoise.media

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * Scales the signal to a peak of exactly 1 — the level the mixer's headroom actually constrains — then splits it
 * with a one-pole low-pass at [splitHz] and its complementary high-pass and reports the mean energy of the high
 * half. Cheaper than an FFT and enough for a claim about how much of a spectrum is audible at all.
 *
 * Shared because two tests make that claim: one about the cutoff as a knob, one about the value that ships.
 */
fun highBandEnergyAtUnitPeak(signal: FloatArray, splitHz: Double): Double {
    val scale = 1.0 / signal.maxOf { abs(it) }
    val smoothing = exp(-2.0 * PI * splitHz / SAMPLE_RATE_HZ)
    var low = 0.0
    var highEnergy = 0.0
    for (sample in signal) {
        val scaled = sample * scale
        low = (smoothing * low) + ((1.0 - smoothing) * scaled)
        val high = scaled - low
        highEnergy += high * high
    }
    return highEnergy / signal.size
}
