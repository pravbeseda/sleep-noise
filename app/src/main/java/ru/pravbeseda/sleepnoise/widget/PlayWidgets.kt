package ru.pravbeseda.sleepnoise.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.playback.PlaybackService
import ru.pravbeseda.sleepnoise.settings.settingsRepository
import kotlin.math.roundToInt

/**
 * The session as the play widgets draw it, and the one place they are drawn.
 *
 * Held in memory on purpose. The service that owns the session runs in this process, so a process that died
 * took the session with it, and a fresh one reading "stopped" reads the truth. A copy on disk would outlive
 * the session and draw "playing" over silence after a reboot.
 */
object PlayWidgets {
    // Null while stopped.
    @Volatile
    private var playingFace: WidgetFace? = null

    /** Both null when the session runs without a timer. */
    fun onPlaying(context: Context, deadlineMillis: Long?, durationMillis: Long?) {
        playingFace = WidgetFace.playing(deadlineMillis, durationMillis)
        refresh(context)
    }

    fun onStopped(context: Context) {
        playingFace = null
        refresh(context)
    }

    /**
     * Redraws every placed play widget, through the provider's own update, so each draws its own layout. The
     * providers are the manifest's own receivers, so a new style cannot be registered there and missed here.
     */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getInstalledProvidersForPackage(context.packageName, null).forEach { info ->
            val ids = manager.getAppWidgetIds(info.provider)
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent()
                        .setComponent(info.provider)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }
    }

    fun face(context: Context): WidgetFace = playingFace ?: WidgetFace.stopped(settingsRepository(context).timerMinutes)

    /** Draws [face] in [style]; a layout without the timer or the ring has those actions skipped. */
    fun render(context: Context, style: PlayWidgetStyle, face: WidgetFace): RemoteViews {
        // Below API 33 the per-app language reaches Activities only, as the service's notification knows.
        val strings = ContextCompat.getContextForLanguage(context)
        return RemoteViews(context.packageName, style.layout).apply {
            setImageViewResource(R.id.widget_glyph, if (face.playing) style.pauseImage else style.playImage)
            setContentDescription(
                android.R.id.background,
                strings.getString(if (face.playing) R.string.notification_stop else R.string.play_button),
            )
            showTimer(face)
            setInt(R.id.widget_ring, "setImageLevel", (face.ringShare(SystemClock.elapsedRealtime()) * MAX_LEVEL).roundToInt())
            setOnClickPendingIntent(android.R.id.background, tapIntent(context, face.playing))
        }
    }

    private fun RemoteViews.showTimer(face: WidgetFace) {
        setViewVisibility(R.id.widget_planned, if (face.plannedTimer != null) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_planned, face.plannedTimer.orEmpty())

        val deadline = face.countdownDeadlineMillis
        setViewVisibility(R.id.widget_countdown, if (deadline != null) View.VISIBLE else View.GONE)
        if (deadline != null) {
            setChronometer(R.id.widget_countdown, deadline, null, true)
            setChronometerCountDown(R.id.widget_countdown, true)
        }
    }

    /**
     * Both ways through `startForegroundService()`: a widget is the user's own tap, which Android lets start a
     * foreground service from the background, while a plain start from there is refused.
     */
    private fun tapIntent(context: Context, playing: Boolean): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
        if (playing) {
            intent.setAction(PlaybackService.ACTION_STOP).putExtra(PlaybackService.EXTRA_FOREGROUND_START, true)
        } else {
            intent.setAction(PlaybackService.ACTION_START)
        }
        return PendingIntent.getForegroundService(
            context,
            if (playing) STOP_REQUEST else START_REQUEST,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private const val START_REQUEST = 0
    private const val STOP_REQUEST = 1

    // A drawable's level runs from 0 to 10 000; a ring drawn with useLevel sweeps that share of its circle.
    private const val MAX_LEVEL = 10_000
}
