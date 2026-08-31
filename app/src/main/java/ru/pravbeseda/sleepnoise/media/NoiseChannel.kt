package ru.pravbeseda.sleepnoise.media

/**
 * One source of an engine's mix, with the volume the writer thread applies to it.
 *
 * The volume is written from whatever thread owns the UI and read by the writer thread on its next
 * cycle, so it is `@Volatile` and nothing else: the caller never touches the audio track.
 */
class NoiseChannel(internal val source: NoiseSource) {
    @Volatile
    var volume: Float = 0.0f
}
