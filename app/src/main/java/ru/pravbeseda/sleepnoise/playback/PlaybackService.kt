package ru.pravbeseda.sleepnoise.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ru.pravbeseda.sleepnoise.APP_PREFS
import ru.pravbeseda.sleepnoise.BROWN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.DEFAULT_BROWN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.DEFAULT_WHITE_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.MainActivity
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.WHITE_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.media.BrownNoise
import ru.pravbeseda.sleepnoise.media.NoiseChannel
import ru.pravbeseda.sleepnoise.media.NoiseEngine
import ru.pravbeseda.sleepnoise.media.WhiteNoise
import ru.pravbeseda.sleepnoise.timer.SleepTimer

/**
 * Owns the noise engine and the sleep timer so that both outlive the Activity.
 *
 * Started with [ACTION_START] / [ACTION_STOP] and bound at the same time: the intents drive
 * playback, the binder lets a visible Activity read the state, move the volumes, follow the
 * countdown and hear that playback stopped without it.
 */
class PlaybackService : Service() {
    private val whiteChannel = NoiseChannel(WhiteNoise())
    private val brownChannel = NoiseChannel(BrownNoise())
    private val noiseEngine = NoiseEngine(listOf(whiteChannel, brownChannel))
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())

    private var playing = false
    private var sleepTimer: SleepTimer? = null
    private var listener: Listener? = null

    private val remainingMillis: Long
        get() = sleepTimer?.remaining(SystemClock.elapsedRealtime()) ?: 0

    private val tick = object : Runnable {
        override fun run() {
            val timer = sleepTimer ?: return
            val now = SystemClock.elapsedRealtime()
            if (timer.hasExpired(now)) {
                stopPlayback()
                return
            }
            val remaining = timer.remaining(now)
            postNotification()
            listener?.onTick(remaining)
            handler.postDelayed(this, TICK_INTERVAL_MILLIS)
        }
    }

    /** What a bound Activity is told: how long the sleep timer has left, and that playback stopped. */
    interface Listener {
        fun onTick(remainingMillis: Long)

        fun onPlaybackStopped()
    }

    inner class LocalBinder : Binder() {
        val isPlaying: Boolean
            get() = playing

        /** Milliseconds left on the sleep timer, 0 when there is none — an Activity binding mid-session starts here. */
        val remainingMillis: Long
            get() = this@PlaybackService.remainingMillis

        var listener: Listener?
            get() = this@PlaybackService.listener
            set(value) {
                this@PlaybackService.listener = value
            }

        fun setWhiteVolume(volume: Float) {
            whiteChannel.volume = volume
        }

        fun setBrownVolume(volume: Float) {
            brownChannel.volume = volume
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startPlayback(intent.getIntExtra(EXTRA_TIMER_MINUTES, 0))
            ACTION_STOP -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        // A destroyed Activity must not be reachable from a service that outlives it.
        listener = null
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
        noiseEngine.stop()
    }

    private fun startPlayback(timerMinutes: Int) {
        // The deadline is set before the notification is built, so the first one already carries
        // the countdown instead of showing "Playing" for a second.
        if (!playing) {
            sleepTimer = if (timerMinutes > 0) SleepTimer.forDuration(SystemClock.elapsedRealtime(), timerMinutes) else null
        }
        // Every startForegroundService() has to be answered with a startForeground(), including the
        // one that arrives while playback is already running: an unanswered start crashes the app
        // five seconds later.
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundServiceType())
        if (playing) return
        val preferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
        whiteChannel.volume = preferences.getFloat(WHITE_NOISE_VOLUME, DEFAULT_WHITE_NOISE_VOLUME)
        brownChannel.volume = preferences.getFloat(BROWN_NOISE_VOLUME, DEFAULT_BROWN_NOISE_VOLUME)

        noiseEngine.start()
        playing = true
        if (sleepTimer != null) {
            handler.postDelayed(tick, TICK_INTERVAL_MILLIS)
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacks(tick)
        sleepTimer = null
        noiseEngine.stop()
        playing = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        listener?.onPlaybackStopped()
    }

    // The constant itself is API 29, so lint rejects naming it below that even though ServiceCompat
    // would ignore the argument there.
    private fun foregroundServiceType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    } else {
        0
    }

    private fun postNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            // Without SINGLE_TOP the tap stacks a second MainActivity on the task the user already has open.
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (sleepTimer == null) {
            getString(R.string.notification_playing)
        } else {
            SleepTimer.formatRemaining(remainingMillis)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "ru.pravbeseda.sleepnoise.action.START"
        const val ACTION_STOP = "ru.pravbeseda.sleepnoise.action.STOP"
        const val EXTRA_TIMER_MINUTES = "ru.pravbeseda.sleepnoise.extra.TIMER_MINUTES"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val TICK_INTERVAL_MILLIS = 1_000L
    }
}
