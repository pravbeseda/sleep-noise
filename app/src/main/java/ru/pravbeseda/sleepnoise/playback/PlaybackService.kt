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
import android.os.IBinder
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

/**
 * Owns the noise engine so that playback outlives the Activity.
 *
 * Started with [ACTION_START] / [ACTION_STOP] and bound at the same time: the intents drive
 * playback, the binder lets a visible Activity read the state, move the volumes and hear that
 * playback stopped from the notification.
 */
class PlaybackService : Service() {
    private val whiteChannel = NoiseChannel(WhiteNoise())
    private val brownChannel = NoiseChannel(BrownNoise())
    private val noiseEngine = NoiseEngine(listOf(whiteChannel, brownChannel))
    private val binder = LocalBinder()

    private var playing = false
    private var listener: Listener? = null

    /** What the Activity is told about playback it did not ask for — the notification's Stop action. */
    fun interface Listener {
        fun onPlaybackStopped()
    }

    inner class LocalBinder : Binder() {
        val isPlaying: Boolean
            get() = playing

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
            ACTION_START -> startPlayback()
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
        noiseEngine.stop()
    }

    private fun startPlayback() {
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
    }

    private fun stopPlayback() {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_playing))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "ru.pravbeseda.sleepnoise.action.START"
        const val ACTION_STOP = "ru.pravbeseda.sleepnoise.action.STOP"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
    }
}
