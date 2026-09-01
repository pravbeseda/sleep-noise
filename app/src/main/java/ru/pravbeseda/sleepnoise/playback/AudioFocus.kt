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

        /** Focus is back — undo what [PAUSE] did. */
        REGAINED,
    }

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        // False, so the framework ducks this app's own track when something talks over it. That is what
        // API 26 introduced, and it is why no LOSS_TRANSIENT_CAN_DUCK ever reaches the listener below.
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(::dispatch, Handler(Looper.getMainLooper()))
        .build()

    /** True when playback may start; a refused request means someone else owns the output right now. */
    fun request(): Boolean = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun abandon() {
        audioManager.abandonAudioFocusRequest(request)
    }

    private fun dispatch(focusChange: Int) {
        val change = when {
            focusChange == AudioManager.AUDIOFOCUS_LOSS -> Change.LOST

            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Change.PAUSE

            // Every gain is positive and there are four of them; matching only AUDIOFOCUS_GAIN would
            // leave the caller paused for good on a device that answers with one of the transient ones.
            focusChange > 0 -> Change.REGAINED

            // Anything else is a loss the framework handles by ducking the track for us.
            else -> return
        }
        onChange(change)
    }
}
