package ru.pravbeseda.sleepnoise.media

/**
 * How a signal's energy divides between three bands, as shares of the total.
 *
 * Cheaper than an FFT and enough for every claim these tests make, which are about where a spectrum's weight
 * sits rather than about its shape. Shared by the sources whose whole point is that weight — grey lifts both
 * ends, green lifts the middle — because three private copies of one splitter is how they drift apart.
 *
 * Each split is **two** [OnePole]s in series, not one. A single pole falls at 6 dB per octave, which leaves a
 * source with a strong tilt pouring its own end of the band into the neighbouring one: measured through a
 * first-order splitter, grey's low shelf leaked far enough past a 300 Hz corner to read as a cut top end.
 * Twelve dB per octave is still no filter bank, but it is enough for a claim about which end carries weight.
 */
fun bandEnergyShares(signal: FloatArray, lowSplitHz: Double, highSplitHz: Double): BandShares {
    val lowFirst = OnePole(lowSplitHz)
    val lowSecond = OnePole(lowSplitHz)
    val midFirst = OnePole(highSplitHz)
    val midSecond = OnePole(highSplitHz)
    var lowEnergy = 0.0
    var midEnergy = 0.0
    var highEnergy = 0.0
    for (sample in signal) {
        val low = lowSecond.low(lowFirst.low(sample.toDouble()))
        val aboveLow = sample - low
        val mid = midSecond.low(midFirst.low(aboveLow))
        lowEnergy += low * low
        midEnergy += mid * mid
        highEnergy += (aboveLow - mid) * (aboveLow - mid)
    }
    val total = lowEnergy + midEnergy + highEnergy
    return BandShares(lowEnergy / total, midEnergy / total, highEnergy / total)
}

data class BandShares(val low: Double, val mid: Double, val high: Double)
