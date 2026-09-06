package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pravbeseda.sleepnoise.BROWN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.CURRENT_LANGUAGE
import ru.pravbeseda.sleepnoise.CURRENT_THEME
import ru.pravbeseda.sleepnoise.PINK_NOISE_VOLUME
import kotlin.random.Random

class NoiseLabTest {
    @Test
    fun everyCandidateCarriesItsOwnPreferenceKey() {
        val keys = NOISE_LAB_CANDIDATES.map { it.preferenceKey }

        assertEquals("two candidates persist under one key, so one slider would move the other", keys.size, keys.toSet().size)
    }

    @Test
    fun noCandidateReusesAShippingPreferenceKey() {
        // The theme and language keys share the store and hold Strings, so colliding with one of those
        // would not overwrite a volume but throw ClassCastException out of getFloat.
        val shipping = setOf(PINK_NOISE_VOLUME, BROWN_NOISE_VOLUME, CURRENT_THEME, CURRENT_LANGUAGE)

        NOISE_LAB_CANDIDATES.forEach { candidate ->
            assertTrue(
                "${candidate.label} would collide with the shipping ${candidate.preferenceKey}",
                candidate.preferenceKey !in shipping,
            )
        }
    }

    @Test
    fun everyCallToTheFactoryBuildsAnotherSource() {
        NOISE_LAB_CANDIDATES.forEach { candidate ->
            assertNotSame(
                "${candidate.label} hands out one shared source, so two channels would drive one filter",
                candidate.createSource(Random(SEED)),
                candidate.createSource(Random(SEED)),
            )
        }
    }

    /**
     * A source depends on nothing but itself and the generator it was handed: built after another one of its
     * kind has run for a while, it produces exactly what the first one produced.
     *
     * State kept anywhere but in the instance — a `companion object`, a top-level `var` — is what this fails
     * on, and it is worth failing on: the service builds one source per channel from this very factory, so a
     * filter shared behind their backs would have two channels driving one of them.
     *
     * The comparison is exact rather than statistical because the factory takes the generator. The test this
     * replaced measured the opening against a running level instead, which held only for sources that settle
     * slowly: a source with a high band opens at full level with nothing shared at all, and failed it.
     */
    @Test
    fun aSourceBuiltAfterAnotherHasRunProducesWhatTheFirstOneDid() {
        NOISE_LAB_CANDIDATES.forEach { candidate ->
            val first = FloatArray(COMPARED_SAMPLES)
            candidate.createSource(Random(SEED)).fill(first)

            candidate.createSource(Random(DECOY_SEED)).fill(FloatArray(WARM_UP_SAMPLES))
            val afterTheDecoy = FloatArray(COMPARED_SAMPLES)
            candidate.createSource(Random(SEED)).fill(afterTheDecoy)

            assertArrayEquals(
                "${candidate.label} came out differently after another of its own sources had run: its state outlives it",
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

        /** A wave of surf and several joints of the clatter, so every candidate's slow state is well under way. */
        const val WARM_UP_SAMPLES = 16 * SAMPLE_RATE_HZ

        /** Long enough to cover the opening of the slowest filter here, short enough to stay a cheap comparison. */
        const val COMPARED_SAMPLES = 8192
    }
}
