package ru.pravbeseda.sleepnoise.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ru.pravbeseda.sleepnoise.APP_PREFS
import ru.pravbeseda.sleepnoise.BROWN_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.BROWN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.DEFAULT_BROWN_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.DEFAULT_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.DEFAULT_WHITE_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.MainActivity
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.WHITE_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.WHITE_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.media.BrownNoise
import ru.pravbeseda.sleepnoise.media.DEFAULT_LAB_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.media.NOISE_LAB_CANDIDATES
import ru.pravbeseda.sleepnoise.media.NOISE_LAB_ENABLED
import ru.pravbeseda.sleepnoise.media.NoiseChannel
import ru.pravbeseda.sleepnoise.media.NoiseEngine
import ru.pravbeseda.sleepnoise.media.NoiseLabCandidate
import ru.pravbeseda.sleepnoise.media.WhiteNoise
import ru.pravbeseda.sleepnoise.timer.SleepTimer

/**
 * A noise's level as the mix should hear it: a switched-off noise keeps the level its slider shows
 * and contributes nothing. The gate lives here as well as in the Activity because a session started
 * from the notification, or after the Activity is gone, reads the preferences and nothing else.
 */
private fun SharedPreferences.noiseVolume(volumeKey: String, enabledKey: String, defaultVolume: Float): Float =
    if (getBoolean(enabledKey, DEFAULT_NOISE_ENABLED)) getFloat(volumeKey, defaultVolume) else 0f

/**
 * Owns the noise engine and the sleep timer so that both outlive the Activity.
 *
 * Started with [ACTION_START] / [ACTION_STOP] and bound at the same time: the intents drive
 * playback, the binder lets a visible Activity read the state, move the volumes, follow the
 * countdown and hear that playback stopped without it.
 *
 * It also owns the audio focus the noise plays under: playback starts only once focus is granted,
 * and stops, pauses or ducks when the system says something else needs the output.
 */
class PlaybackService : Service() {
    private val whiteChannel = NoiseChannel(WhiteNoise())
    private val brownChannel = NoiseChannel(BrownNoise())

    /**
     * Empty while the lab is switched off, and the engine then mixes exactly the two channels it ships with:
     * a lab volume left in the preferences must not go on playing once its slider is gone.
     */
    private val labCandidates: List<NoiseLabCandidate> = if (NOISE_LAB_ENABLED) NOISE_LAB_CANDIDATES else emptyList()
    private val labChannels: Map<String, NoiseChannel> = labCandidates.associate { it.preferenceKey to NoiseChannel(it.createSource()) }
    private val noiseEngine = NoiseEngine(listOf(whiteChannel, brownChannel) + labChannels.values)
    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())

    // Constant for the service's lifetime, and the notification is rebuilt once a second: building
    // them there would cost two binder round-trips per tick.
    private val contentIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            // Without SINGLE_TOP the tap stacks a second MainActivity on the task the user already has open.
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val stopIntent: PendingIntent by lazy {
        PendingIntent.getService(
            this,
            0,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private var playing = false
    private var sleepTimer: SleepTimer? = null
    private var listener: Listener? = null

    private var pausedByFocusLoss = false

    // Lazy, like the intents: a Service has no context to ask for a system service until it is created.
    private val audioFocus: AudioFocus by lazy {
        AudioFocus(getSystemService(AudioManager::class.java)) { change ->
            when (change) {
                AudioFocus.Change.LOST -> stopPlayback()

                // The session goes on without sound: a phone call must not extend the sleep timer.
                AudioFocus.Change.PAUSE -> {
                    noiseEngine.stop()
                    pausedByFocusLoss = true
                    listener?.onPaused(true)
                }

                AudioFocus.Change.REGAINED -> if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    noiseEngine.start()
                    listener?.onPaused(false)
                }
            }
        }
    }

    /** Registered from code and never in the manifest: a manifest receiver would wake the app when nothing is playing. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopPlayback()
        }
    }
    private var noisyReceiverRegistered = false

    /**
     * The app's own language, which a service does not otherwise get: below API 33
     * `AppCompatDelegate.setApplicationLocales` reaches Activities only, so `getString` here would
     * answer in the device's language while the screen answers in the chosen one.
     */
    private val localized: Context
        get() = ContextCompat.getContextForLanguage(this)

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

    /** What a bound Activity is told: the sleep timer's remaining time, and every change of state. */
    interface Listener {
        fun onTick(remainingMillis: Long)

        /** Another app took the output for a while, or gave it back. Playback is silent meanwhile. */
        fun onPaused(paused: Boolean)

        fun onPlaybackStopped()
    }

    inner class LocalBinder : Binder() {
        val isPlaying: Boolean
            get() = playing

        /** Silent because another app holds the output. An Activity binding mid-pause starts here. */
        val isPaused: Boolean
            get() = pausedByFocusLoss

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

        /** The key comes from the registry both sides read, so an unknown one is a wiring bug and says so. */
        fun setLabVolume(preferenceKey: String, volume: Float) {
            labChannels.getValue(preferenceKey).volume = volume
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.notification_channel_playback),
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
        // The engine's writer thread lives as long as the engine, so the service that owns it ends it.
        noiseEngine.release()
        unregisterNoisyReceiver()
        // Only a session that ran ever took the focus, and a stopped one has already given it back.
        if (playing) audioFocus.abandon()
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
        if (playing) {
            // A start arriving while a transient focus loss holds the engine silent is the user asking
            // for the sound back. Without this the session sits paused for as long as the app that took
            // the focus keeps it, with the notification counting down over silence.
            if (pausedByFocusLoss) {
                pausedByFocusLoss = false
                if (audioFocus.request()) {
                    noiseEngine.start()
                    listener?.onPaused(false)
                } else {
                    stopPlayback()
                }
            }
            return
        }
        // Focus first: a refused request means playback never starts, so stop the way the Stop action
        // does rather than leaving a notification over silence.
        if (!audioFocus.request()) {
            stopPlayback()
            return
        }
        val preferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
        whiteChannel.volume = preferences.noiseVolume(WHITE_NOISE_VOLUME, WHITE_NOISE_ENABLED, DEFAULT_WHITE_NOISE_VOLUME)
        brownChannel.volume = preferences.noiseVolume(BROWN_NOISE_VOLUME, BROWN_NOISE_ENABLED, DEFAULT_BROWN_NOISE_VOLUME)
        labCandidates.forEach { candidate ->
            labChannels.getValue(candidate.preferenceKey).volume =
                preferences.noiseVolume(candidate.preferenceKey, candidate.enabledPreferenceKey, DEFAULT_LAB_NOISE_VOLUME)
        }
        pausedByFocusLoss = false
        // Reached only when playback was stopped, and a stop always unregisters, so this is never a double.
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiverRegistered = true

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
        pausedByFocusLoss = false
        unregisterNoisyReceiver()
        audioFocus.abandon()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        listener?.onPlaybackStopped()
    }

    // Called from both stopPlayback() and onDestroy(), and stopSelf() puts them in that order.
    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        noisyReceiverRegistered = false
        unregisterReceiver(noisyReceiver)
    }

    private fun postNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val strings = localized
        val text = if (sleepTimer == null) {
            strings.getString(R.string.notification_playing)
        } else {
            SleepTimer.formatRemaining(remainingMillis)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_stop, strings.getString(R.string.notification_stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "ru.pravbeseda.sleepnoise.action.START"
        const val ACTION_STOP = "ru.pravbeseda.sleepnoise.action.STOP"
        const val EXTRA_TIMER_MINUTES = "ru.pravbeseda.sleepnoise.extra.TIMER_MINUTES"

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val TICK_INTERVAL_MILLIS = 1_000L

        // Reads no instance state, and lives here to keep the service under detekt's function count.
        // The constant itself is API 29, so lint rejects naming it below that even though ServiceCompat
        // would ignore the argument there.
        private fun foregroundServiceType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
    }
}
