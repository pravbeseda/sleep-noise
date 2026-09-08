package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pravbeseda.sleepnoise.CURRENT_LANGUAGE
import ru.pravbeseda.sleepnoise.CURRENT_THEME
import kotlin.random.Random

/**
 * The registry the screen builds its rows from and the service its channels: what it says about a noise is
 * the only thing either of them knows, so a key collision or a shared source is a bug neither would notice.
 */
class ShippingNoisesTest {
    @Test
    fun everyNoiseCarriesItsOwnPairOfKeys() {
        val keys = SHIPPING_NOISES.flatMap { listOf(it.volumeKey, it.enabledKey) }

        assertEquals("two noises persist under one key, so one slider would move the other", keys.size, keys.toSet().size)
    }

    @Test
    fun noNoiseReusesAKeyTheRestOfTheAppOwns() {
        // The theme and language keys share the store and hold Strings, so colliding with one of those
        // would not overwrite a volume but throw ClassCastException out of getFloat.
        val taken = setOf(CURRENT_THEME, CURRENT_LANGUAGE) + NOISE_LAB_CANDIDATES.flatMap {
            listOf(it.preferenceKey, it.enabledPreferenceKey)
        }

        SHIPPING_NOISES.forEach { noise ->
            assertTrue("${noise.volumeKey} is already spoken for", noise.volumeKey !in taken)
            assertTrue("${noise.enabledKey} is already spoken for", noise.enabledKey !in taken)
        }
    }

    /**
     * Only brown opens audible. Every other noise arrived after some install did, and a key that did not
     * exist yet must not make a running app louder the day it appears.
     */
    @Test
    fun onlyOneNoiseOpensAboveSilence() {
        val audible = SHIPPING_NOISES.filter { it.defaultVolume > 0f }

        assertEquals("noises that open above silence: ${audible.map { it.volumeKey }}", 1, audible.size)
    }

    @Test
    fun everyCallToTheFactoryBuildsAnotherSource() {
        SHIPPING_NOISES.forEach { noise ->
            assertNotSame(
                "${noise.volumeKey} hands out one shared source, so two channels would drive one filter",
                noise.createSource(Random(SEED)),
                noise.createSource(Random(SEED)),
            )
        }
    }

    /**
     * A source depends on nothing but itself and the generator it was handed — the claim `NoiseLabTest`
     * makes about the candidates, made here about the ones that actually reach a user. State kept anywhere
     * but in the instance is what this fails on, and the service builds one source per channel from this
     * very factory.
     */
    @Test
    fun aSourceBuiltAfterAnotherHasRunProducesWhatTheFirstOneDid() {
        SHIPPING_NOISES.forEach { noise ->
            val first = FloatArray(COMPARED_SAMPLES)
            noise.createSource(Random(SEED)).fill(first)

            noise.createSource(Random(DECOY_SEED)).fill(FloatArray(WARM_UP_SAMPLES))
            val afterTheDecoy = FloatArray(COMPARED_SAMPLES)
            noise.createSource(Random(SEED)).fill(afterTheDecoy)

            assertArrayEquals(
                "${noise.volumeKey} came out differently after another of its own sources had run: its state outlives it",
                first,
                afterTheDecoy,
                0.0f,
            )
        }
    }

    private companion object {
        const val SEED = 20260906

        /** Anything but [SEED], so the decoy cannot accidentally leave the state the comparison expects. */
        const val DECOY_SEED = 767

        /** A wave of surf and more, so every source's slow state is well under way. */
        const val WARM_UP_SAMPLES = 16 * SAMPLE_RATE_HZ

        /** Long enough to cover the opening of the slowest filter here, short enough to stay a cheap comparison. */
        const val COMPARED_SAMPLES = 8192
    }
}
