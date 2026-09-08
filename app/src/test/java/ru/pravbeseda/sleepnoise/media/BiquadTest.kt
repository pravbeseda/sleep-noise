package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each section is asserted by the shape it puts on the band, not by its coefficients: a transcription slip
 * in the cookbook formulas shows up here as a filter that boosts where it should cut or passes what it
 * should stop, which is what [GreyNoise] depends on and what reading five coefficients back would not catch.
 */
class BiquadTest {
    @Test
    fun aPeakLiftsItsOwnFrequencyAndLeavesTheRestOfTheBandAlone() {
        val response = responseOf(Biquad.peaking(CENTRE_HZ, MODERATE_Q, BOOST_DB))

        assertEquals("the peak's own frequency", BOOST_DB, responseDb(response, CENTRE_HZ), TOLERANCE_DB)
        assertEquals("a decade below the peak", 0.0, responseDb(response, CENTRE_HZ / DECADE), TOLERANCE_DB)
        assertEquals("a decade above the peak", 0.0, responseDb(response, CENTRE_HZ * DECADE), TOLERANCE_DB)
    }

    @Test
    fun aLowShelfLiftsEverythingUnderItsCornerAndNothingOverIt() {
        val response = responseOf(Biquad.lowShelf(CENTRE_HZ, MODERATE_Q, BOOST_DB))

        assertEquals("under the corner", BOOST_DB, responseDb(response, CENTRE_HZ / DECADE), TOLERANCE_DB)
        assertEquals("at the corner, which is half the lift", BOOST_DB / 2, responseDb(response, CENTRE_HZ), TOLERANCE_DB)
        assertEquals("over the corner", 0.0, responseDb(response, CENTRE_HZ * DECADE), TOLERANCE_DB)
    }

    @Test
    fun aHighShelfLiftsEverythingOverItsCornerAndNothingUnderIt() {
        val response = responseOf(Biquad.highShelf(CENTRE_HZ, MODERATE_Q, BOOST_DB))

        assertEquals("over the corner", BOOST_DB, responseDb(response, CENTRE_HZ * DECADE), TOLERANCE_DB)
        assertEquals("at the corner, which is half the lift", BOOST_DB / 2, responseDb(response, CENTRE_HZ), TOLERANCE_DB)
        assertEquals("under the corner", 0.0, responseDb(response, CENTRE_HZ / DECADE), TOLERANCE_DB)
    }

    @Test
    fun aLowPassPassesItsBandAndFallsTwelveDecibelsAnOctaveAboveIt() {
        val response = responseOf(Biquad.lowPass(CENTRE_HZ, BUTTERWORTH_Q))

        assertEquals("well inside the passband", 0.0, responseDb(response, CENTRE_HZ / DECADE), TOLERANCE_DB)
        assertEquals("at the corner, which is where a Butterworth is 3 dB down", -THREE_DB, responseDb(response, CENTRE_HZ), TOLERANCE_DB)
        val octaveAbove = responseDb(response, CENTRE_HZ * 2)
        val twoOctavesAbove = responseDb(response, CENTRE_HZ * 4)
        assertTrue(
            "a second-order fall measured $octaveAbove dB and $twoOctavesAbove dB an octave apart",
            octaveAbove - twoOctavesAbove > SECOND_ORDER_FALL_DB,
        )
    }

    @Test
    fun resetReturnsTheSectionToItsInitialState() {
        val section = Biquad.peaking(CENTRE_HZ, MODERATE_Q, BOOST_DB)
        impulseResponse(SETTLED_SAMPLES) { section.process(it) }

        section.reset()

        val afterReset = impulseResponse(SETTLED_SAMPLES) { section.process(it) }
        val untouched = Biquad.peaking(CENTRE_HZ, MODERATE_Q, BOOST_DB)
        val fresh = impulseResponse(SETTLED_SAMPLES) { untouched.process(it) }
        assertEquals("a reset section does not start where a fresh one does", fresh.toList(), afterReset.toList())
    }

    private fun responseOf(section: Biquad): DoubleArray = impulseResponse { section.process(it) }

    private companion object {
        const val CENTRE_HZ = 1000.0

        /** Wide enough that the shape is what the formula says and not a resonance's ringing. */
        const val MODERATE_Q = 1.0

        /** The one Q with a name: a second-order Butterworth, which is 3 dB down at its own corner. */
        const val BUTTERWORTH_Q = 0.7071
        const val BOOST_DB = 6.0
        const val THREE_DB = 3.0
        const val DECADE = 10.0

        /** Generous next to the 0.01 dB these formulas actually land on, and still far inside any transcription slip. */
        const val TOLERANCE_DB = 0.3

        /** Two poles fall 12 dB per octave; the bound leaves room for the corner's own shoulder. */
        const val SECOND_ORDER_FALL_DB = 10.0

        const val SETTLED_SAMPLES = 4096
    }
}
