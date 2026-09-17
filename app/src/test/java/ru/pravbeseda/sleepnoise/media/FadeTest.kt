package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FadeTest {
    private val duration = 4

    /** A buffer of ones comes out as the gain applied to each of its samples. */
    private fun Fade.gains(count: Int): FloatArray = FloatArray(count) { 1.0f }.also { apply(it) }

    @Test
    fun aNewFadeIsSilent() {
        val fade = Fade(duration)

        assertTrue(fade.isSilent)
        assertArrayEquals(FloatArray(3), fade.gains(3), 0.0f)
    }

    @Test
    fun aFadeInRisesAlongTheSquareOfItsProgressAndTakesExactlyItsDuration() {
        val fade = Fade(duration).apply { fadeIn() }

        assertArrayEquals(floatArrayOf(0.0f, 0.0625f, 0.25f, 0.5625f, 1.0f, 1.0f), fade.gains(6), 1e-6f)
    }

    @Test
    fun aFadeOutFallsAlongTheSameCurveAndEndsSilent() {
        val fade = Fade(duration).apply { fadeIn() }
        fade.gains(duration)

        fade.fadeOut()

        assertFalse("silent before the fade-out has run", fade.isSilent)
        assertArrayEquals(floatArrayOf(1.0f, 0.5625f, 0.25f, 0.0625f, 0.0f, 0.0f), fade.gains(6), 1e-6f)
        assertTrue(fade.isSilent)
    }

    @Test
    fun aReversalMidFadeTurnsAroundFromTheCurrentLevel() {
        val fade = Fade(duration).apply { fadeIn() }
        fade.gains(3)

        fade.fadeOut()

        // Progress reached 3/4 and is not reset: the fade-out starts where the fade-in left off.
        assertArrayEquals(floatArrayOf(0.5625f, 0.25f, 0.0625f, 0.0f), fade.gains(4), 1e-6f)
    }

    @Test
    fun aSilentFadeStopsBeingSilentOnceAFadeInIsAskedFor() {
        val fade = Fade(duration)

        fade.fadeIn()

        assertFalse(fade.isSilent)
    }

    @Test
    fun anOpenFadeLeavesSamplesUntouched() {
        val fade = Fade(duration).apply { fadeIn() }
        fade.gains(duration)
        val samples = floatArrayOf(0.3f, -0.7f, 1.0f)

        fade.apply(samples)

        assertArrayEquals(floatArrayOf(0.3f, -0.7f, 1.0f), samples, 0.0f)
    }

    @Test
    fun cutSilencesAtOnce() {
        val fade = Fade(duration).apply { fadeIn() }
        fade.gains(duration)

        fade.cut()

        assertTrue(fade.isSilent)
        assertEquals(0.0f, fade.gains(1)[0], 0.0f)
    }
}
