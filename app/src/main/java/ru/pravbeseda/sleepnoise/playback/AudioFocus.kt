package ru.pravbeseda.sleepnoise.playback

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * The playback focus request, and nothing else: it owns the [AudioFocusRequest] and translates the
 * raw focus-change constants into the four things a caller can do about them.
 *
 * The attributes match the ones [ru.pravbeseda.sleepnoise.media.NoiseEngine] gives its track, so the
 * system judges the focus request and the sound it covers by the same description.
 */
class AudioFocus(private val audioManager: AudioManager, private val onChange: (Change) -> Unit) {
    /** What the caller should do about a focus change. */
    enum class Change {
        /** Focus is gone for good — stop. */
        LOST,

        /** Something else is playing for a while — stop the sound but keep the session. */
        PAUSE,

        /** Play on, quietly, beside the other sound. */
        DUCK,

        /** Focus is back — undo whatever [PAUSE] or [DUCK] did. */
        REGAINED,
    }

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        // False, so the framework ducks this app's track itself — which is what normally happens from
        // API 26 on, and why [Change.DUCK] is a fallback for the systems that hand the job back instead.
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(::dispatch, Handler(Looper.getMainLooper()))
        .build()

    /** True when playback may start; a refused request means someone else owns the output right now. */
    fun request(): Boolean = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun abandon() {
        audioManager.abandonAudioFocusRequest(request)
    }

    private fun dispatch(focusChange: Int) {
        val change = when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> Change.LOST
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Change.PAUSE
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Change.DUCK
            AudioManager.AUDIOFOCUS_GAIN -> Change.REGAINED
            else -> return
        }
        onChange(change)
    }
}
