package ru.pravbeseda.sleepnoise.media

/** A fade that has already reached full level, for tests about the mix rather than the ramp. */
fun openFade(): Fade = Fade(1).apply {
    fadeIn()
    apply(FloatArray(1))
}
