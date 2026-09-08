package ru.pravbeseda.sleepnoise.media

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The impulse response of [process], which is the whole of what a linear filter does: every claim these
 * tests make about a filter's shape is read off one of these rather than out of a noise sample, so no
 * assertion depends on a seed.
 *
 * The default length covers a second of decay at the engine's rate, which is far past the settling of the
 * slowest pole in this package.
 */
fun impulseResponse(length: Int = 1 shl 15, process: (Double) -> Double): DoubleArray =
    DoubleArray(length) { process(if (it == 0) 1.0 else 0.0) }

/**
 * The magnitude of [impulseResponse] at [frequencyHz], in dB — one bin of a DFT, evaluated where the
 * assertion needs it instead of everywhere an FFT would put it.
 */
fun responseDb(impulseResponse: DoubleArray, frequencyHz: Double): Double {
    val radiansPerSample = 2.0 * PI * frequencyHz / SAMPLE_RATE_HZ
    var real = 0.0
    var imaginary = 0.0
    impulseResponse.forEachIndexed { sample, value ->
        real += value * cos(radiansPerSample * sample)
        imaginary -= value * sin(radiansPerSample * sample)
    }
    return 20.0 * log10(sqrt((real * real) + (imaginary * imaginary)))
}
