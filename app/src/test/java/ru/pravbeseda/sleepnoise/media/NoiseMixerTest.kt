package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NoiseMixerTest {
    private val bufferSize = 8

    /** A source whose samples are known in advance, so a mixed buffer can be predicted exactly. */
    private class ConstantNoise(private val value: Float) : NoiseSource {
        var fillCount = 0
            private set

        override fun fill(buffer: FloatArray) {
            fillCount++
            buffer.fill(value)
        }

        override fun reset() = Unit
    }

    private fun expected(sample: Int) = ShortArray(bufferSize) { sample.toShort() }

    private fun mixerOf(sources: List<NoiseSource>) = NoiseMixer(sources, openFade())

    @Test
    fun sourcesAreSummedScaledByTheirVolumes() {
        val mixer = mixerOf(listOf(ConstantNoise(1.0f), ConstantNoise(0.5f)))
        val out = ShortArray(bufferSize)

        // 1.0 * 0.5 + 0.5 * 0.25 = 0.625, and 0.625 * Short.MAX_VALUE truncates to 20479.
        mixer.mix(floatArrayOf(0.5f, 0.25f), out)

        assertArrayEquals(expected(20479), out)
    }

    @Test
    fun aSumAboveOneClampsInsteadOfWrappingTheShortConversion() {
        val mixer = mixerOf(listOf(ConstantNoise(1.0f), ConstantNoise(0.8f)))
        val out = ShortArray(bufferSize)

        mixer.mix(floatArrayOf(1.0f, 1.0f), out)

        assertArrayEquals(expected(32767), out)
    }

    @Test
    fun aSumBelowMinusOneClampsInsteadOfWrappingTheShortConversion() {
        val mixer = mixerOf(listOf(ConstantNoise(-1.0f), ConstantNoise(-0.8f)))
        val out = ShortArray(bufferSize)

        mixer.mix(floatArrayOf(1.0f, 1.0f), out)

        assertArrayEquals(expected(-32767), out)
    }

    @Test
    fun aChannelAtVolumeZeroIsNotGenerated() {
        val silent = ConstantNoise(1.0f)
        val audible = ConstantNoise(0.5f)
        val mixer = mixerOf(listOf(silent, audible))
        val out = ShortArray(bufferSize)

        mixer.mix(floatArrayOf(0.0f, 1.0f), out)

        assertEquals("the muted channel was generated anyway", 0, silent.fillCount)
        assertEquals(1, audible.fillCount)
        // 0.5 * Short.MAX_VALUE truncates to 16383: the muted channel contributed nothing.
        assertArrayEquals(expected(16383), out)
    }

    @Test
    fun repeatedMixesDoNotAccumulateThePreviousResult() {
        val mixer = mixerOf(listOf(ConstantNoise(0.5f), ConstantNoise(0.25f)))
        val volumes = floatArrayOf(1.0f, 1.0f)
        val out = ShortArray(bufferSize)

        mixer.mix(volumes, out)
        val first = out.copyOf()
        mixer.mix(volumes, out)

        assertArrayEquals(first, out)
    }

    @Test
    fun theFadeScalesTheSumBeforeTheClamp() {
        val fade = Fade(4).apply { fadeIn() }
        val mixer = NoiseMixer(listOf(ConstantNoise(1.0f), ConstantNoise(0.6f)), fade)
        val out = ShortArray(bufferSize)

        mixer.mix(floatArrayOf(1.0f, 1.0f), out)

        // 1.6 scaled by 0, 1/16, 1/4 and 9/16 before clamping; a fade after the clamp would scale 1.0 instead.
        val rising = shortArrayOf(0, 3276, 13106, 29490)
        assertArrayEquals(rising + ShortArray(bufferSize - rising.size) { Short.MAX_VALUE }, out)
    }
}
