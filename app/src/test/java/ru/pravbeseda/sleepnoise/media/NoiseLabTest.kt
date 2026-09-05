package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pravbeseda.sleepnoise.BROWN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.WHITE_NOISE_VOLUME

class NoiseLabTest {
    @Test
    fun theLabRegistersItsCandidates() {
        assertTrue("the lab registers nothing, so every other test here inspects an empty list", NOISE_LAB_CANDIDATES.isNotEmpty())
    }

    @Test
    fun everyCandidateCarriesItsOwnPreferenceKey() {
        val keys = NOISE_LAB_CANDIDATES.map { it.preferenceKey }

        assertEquals("two candidates persist under one key, so one slider would move the other", keys.size, keys.toSet().size)
    }

    @Test
    fun noCandidateReusesAShippingPreferenceKey() {
        val shipping = setOf(WHITE_NOISE_VOLUME, BROWN_NOISE_VOLUME)

        NOISE_LAB_CANDIDATES.forEach { candidate ->
            assertTrue(
                "${candidate.label} would overwrite the shipping ${candidate.preferenceKey}",
                candidate.preferenceKey !in shipping,
            )
        }
    }

    @Test
    fun theCandidatesAreThePinkAndTheLeakyBrownSource() {
        val sources = NOISE_LAB_CANDIDATES.map { it.createSource() }

        assertEquals(listOf(PinkNoise::class, LeakyBrownNoise::class), sources.map { it::class })
    }

    @Test
    fun everyCallToTheFactoryBuildsAnotherSource() {
        NOISE_LAB_CANDIDATES.forEach { candidate ->
            assertNotSame(
                "${candidate.label} hands out one shared source, so two channels would drive one filter",
                candidate.createSource(),
                candidate.createSource(),
            )
        }
    }

    /**
     * A filter that has just been built is still settling, so its first sample carries far less energy than a
     * running one's. Averaged over enough sources the comparison is about their state and not about the noise:
     * a source holding its state anywhere but in itself would hand the next one a filter already at full level.
     */
    @Test
    fun aNewSourceStartsFromSilenceRatherThanFromTheLastOnesState() {
        NOISE_LAB_CANDIDATES.forEach { candidate ->
            val cold = FloatArray(TRIALS)
            val warm = FloatArray(TRIALS)
            val warmUp = FloatArray(WARM_UP_SAMPLES)
            val sample = FloatArray(1)

            repeat(TRIALS) { trial ->
                val source = candidate.createSource()
                source.fill(sample)
                cold[trial] = sample[0]
                source.fill(warmUp)
                source.fill(sample)
                warm[trial] = sample[0]
            }

            val coldEnergy = meanSquare(cold)
            val warmEnergy = meanSquare(warm)
            assertTrue(
                "${candidate.label} starts at $coldEnergy against a running $warmEnergy: its state outlives the source",
                coldEnergy * SETTLING_MARGIN < warmEnergy,
            )
        }
    }

    private fun meanSquare(samples: FloatArray): Double = samples.sumOf { (it * it).toDouble() } / samples.size

    private companion object {
        /** Sources built per candidate. Enough that the energy comparison below clears its margin by six sigma. */
        const val TRIALS = 512

        /** Several time constants of the slowest section in either candidate, so "warm" means fully settled. */
        const val WARM_UP_SAMPLES = 4096

        /** Both candidates start below half the running energy; the factor is the room left for the noise. */
        const val SETTLING_MARGIN = 2.0
    }
}
