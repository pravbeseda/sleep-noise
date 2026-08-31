package ru.pravbeseda.sleepnoise.media

/**
 * Sums its channels by volume and converts the result to PCM 16-bit. Holds the mixing law only:
 * no audio platform, no threading — the caller owns both.
 */
class NoiseMixer(private val sources: List<NoiseSource>) {
    private var scratch = FloatArray(0)
    private var mixed = FloatArray(0)

    /**
     * Writes one buffer of mixed samples into [out]. Volumes are positional, one per source, in `[0, 1]`,
     * and are read on every call so the caller can change them between calls.
     */
    fun mix(volumes: FloatArray, out: ShortArray) {
        resizeBuffers(out.size)
        mixed.fill(0.0f)
        sources.forEachIndexed { index, source ->
            val volume = volumes[index]
            // A muted channel is not generated at all: that is the point of mixing in software.
            if (volume == 0.0f) return@forEachIndexed
            source.fill(scratch)
            for (i in out.indices) {
                mixed[i] += scratch[i] * volume
            }
        }
        for (i in out.indices) {
            // Without the clamp the sum of two loud channels wraps the Short conversion into an audible crack.
            out[i] = (mixed[i].coerceIn(-1.0f, 1.0f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun resizeBuffers(size: Int) {
        if (scratch.size == size) return
        scratch = FloatArray(size)
        mixed = FloatArray(size)
    }
}
