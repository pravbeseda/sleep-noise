package ru.pravbeseda.sleepnoise.playback

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.pravbeseda.sleepnoise.timer.TimerPreferences

/**
 * The session as the screen shows it, and the screen's one binding to [PlaybackService].
 *
 * Both outlive a `recreate()`: the binding is kept across it, so the new Activity renders the last state
 * from its first frame rather than a stopped screen until a fresh binding answers. The binding goes
 * through the application context for the same reason, since it must not hold the Activity that made it.
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication()

    private val timerPreferences = TimerPreferences(application)

    private val mutableState = MutableStateFlow(PlaybackState(timerMinutes = timerPreferences.getTimerValue()))
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private var binder: PlaybackService.LocalBinder? = null
    private var bound = false

    private val listener = object : PlaybackService.Listener {
        override fun onTick(remainingMillis: Long) = mutableState.update { it.afterTick(remainingMillis) }

        override fun onPaused(paused: Boolean) = mutableState.update { it.afterPause(paused) }

        /** Every stop: the notification's Stop action, the sleep timer expiring, or the ACTION_STOP sent here. */
        override fun onPlaybackStopped() = mutableState.update { it.afterStop() }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? PlaybackService.LocalBinder ?: return
            this@PlaybackViewModel.binder = binder
            binder.listener = listener
            mutableState.update { it.afterBind(binder.isPlaying, binder.isPaused, binder.remainingMillis) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    /** From the Activity's `onStart`; a binding kept across a `recreate()` is still standing and is left alone. */
    fun connect() {
        if (bound) return
        bound = true
        context.bindService(Intent(context, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    /** Unbound in the background, so the service's lifetime is what it was when the Activity held the binding. */
    fun disconnect() {
        if (!bound) return
        bound = false
        // The service clears the listener in onUnbind, which is the only way out of a binding.
        binder = null
        context.unbindService(connection)
    }

    fun start() {
        mutableState.update { it.afterStart() }
        val startIntent = playbackIntent(PlaybackService.ACTION_START)
            .putExtra(PlaybackService.EXTRA_TIMER_MINUTES, state.value.timerMinutes)
        ContextCompat.startForegroundService(context, startIntent)
    }

    fun stop() {
        mutableState.update { it.afterStop() }
        context.startService(playbackIntent(PlaybackService.ACTION_STOP))
    }

    /** A live change only: the service reads every level from the preferences when a session starts. */
    fun setVolume(volumeKey: String, volume: Float) {
        binder?.setVolume(volumeKey, volume)
    }

    fun setTimerMinutes(minutes: Int) {
        mutableState.update { it.afterTimerChange(minutes) }
        timerPreferences.saveTimerValue(minutes)
    }

    override fun onCleared() = disconnect()

    private fun playbackIntent(action: String): Intent = Intent(context, PlaybackService::class.java).setAction(action)
}
