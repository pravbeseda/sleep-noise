package ru.pravbeseda.sleepnoise.media

/**
 * A master gain that rises to full level or falls to silence over [durationSamples], one step per sample.
 *
 * The gain is the square of the progress rather than the progress itself: a linear ramp sounds abrupt at its
 * quiet end. A reversal turns the progress around where it is, so a stop during a fade-in, or a start during a
 * fade-out, never jumps. It starts silent. Not thread-safe: one thread drives it.
 */
class Fade(private val durationSamples: Int) {
    private var position = 0
    private var target = 0

    /** Silent and staying silent: a fade-out has finished, or nothing has asked for sound yet. */
    val isSilent: Boolean
        get() = position == 0 && target == 0

    init {
        require(durationSamples > 0) { "a fade needs at least one sample, got $durationSamples" }
    }

    fun fadeIn() {
        target = durationSamples
    }

    fun fadeOut() {
        target = 0
    }

    /** Silence without a ramp. */
    fun cut() {
        position = 0
        target = 0
    }

    /** Scales [buffer] in place, advancing the fade by one step per sample. */
    fun apply(buffer: FloatArray) {
        if (position == durationSamples && target == durationSamples) return
        for (i in buffer.indices) {
            val progress = position.toFloat() / durationSamples
            buffer[i] *= progress * progress
            if (position < target) {
                position++
            } else if (position > target) {
                position--
            }
        }
    }
}
