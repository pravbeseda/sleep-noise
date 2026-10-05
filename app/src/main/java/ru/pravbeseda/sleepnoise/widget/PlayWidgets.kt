package ru.pravbeseda.sleepnoise.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.playback.PlaybackService
import ru.pravbeseda.sleepnoise.settings.settingsRepository

/**
 * The session as the play widgets draw it, and the one place they are drawn.
 *
 * Held in memory on purpose. The service that owns the session runs in this process, so a process that died
 * took the session with it, and a fresh one reading "stopped" reads the truth. A copy on disk would outlive
 * the session and draw "playing" over silence after a reboot.
 */
object PlayWidgets {
    /** Every play widget the app ships. A new style is one provider here and one receiver in the manifest. */
    private val providers: List<Class<out PlayWidgetProvider>> = listOf(ButtonWidget::class.java)

    // Null while stopped; a session's deadline is null when it runs without a timer.
    @Volatile
    private var session: Session? = null

    private class Session(val deadlineMillis: Long?)

    fun onPlaying(context: Context, deadlineMillis: Long?) {
        session = Session(deadlineMillis)
        refresh(context)
    }

    fun onStopped(context: Context) {
        session = null
        refresh(context)
    }

    /** Redraws every placed play widget, through the provider's own update, so each draws its own layout. */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        providers.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, provider)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }
    }

    fun face(context: Context): WidgetFace =
        session?.let { WidgetFace.playing(it.deadlineMillis) } ?: WidgetFace.stopped(settingsRepository(context).timerMinutes)

    /** Every layout carries the same four ids; what they look like is the layout's own business. */
    fun render(context: Context, @LayoutRes layout: Int, face: WidgetFace): RemoteViews {
        // Below API 33 the per-app language reaches Activities only, as the service's notification knows.
        val strings = ContextCompat.getContextForLanguage(context)
        return RemoteViews(context.packageName, layout).apply {
            setImageViewResource(R.id.widget_glyph, if (face.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            setContentDescription(
                android.R.id.background,
                strings.getString(if (face.playing) R.string.notification_stop else R.string.play_button),
            )

            setViewVisibility(R.id.widget_planned, if (face.plannedTimer != null) View.VISIBLE else View.GONE)
            setTextViewText(R.id.widget_planned, face.plannedTimer.orEmpty())

            val deadline = face.countdownDeadlineMillis
            setViewVisibility(R.id.widget_countdown, if (deadline != null) View.VISIBLE else View.GONE)
            if (deadline != null) {
                setChronometer(R.id.widget_countdown, deadline, null, true)
                setChronometerCountDown(R.id.widget_countdown, true)
            }

            setOnClickPendingIntent(android.R.id.background, tapIntent(context, face.playing))
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
}
