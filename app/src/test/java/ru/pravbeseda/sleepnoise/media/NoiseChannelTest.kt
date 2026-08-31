package ru.pravbeseda.sleepnoise.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NoiseChannelTest {
    @Test
    fun volumeIsClampedToTheAudibleRange() {
        val channel = NoiseChannel(WhiteNoise())

        channel.volume = 1.5f
        assertEquals(1.0f, channel.volume, 0.0f)

        channel.volume = -0.5f
        assertEquals(0.0f, channel.volume, 0.0f)

        channel.volume = 0.25f
        assertEquals(0.25f, channel.volume, 0.0f)
    }
}
