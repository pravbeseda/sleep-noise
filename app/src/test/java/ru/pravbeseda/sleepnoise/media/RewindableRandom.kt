package ru.pravbeseda.sleepnoise.media

import kotlin.random.Random

/**
 * A [Random] the test can rewind, so a reset source and a fresh one are fed the identical white input.
 *
 * Without it the two cannot be compared: `fill` has already advanced the generator by the time `reset()` is
 * called, and the stream a fresh source sees is no longer reproducible.
 */
class RewindableRandom(private val seed: Int) : Random() {
    private var delegate = Random(seed)

    override fun nextBits(bitCount: Int): Int = delegate.nextBits(bitCount)

    fun rewind() {
        delegate = Random(seed)
    }
}
