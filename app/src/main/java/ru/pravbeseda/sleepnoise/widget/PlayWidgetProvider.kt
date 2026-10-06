package ru.pravbeseda.sleepnoise.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import ru.pravbeseda.sleepnoise.R

/**
 * How one style draws: its layout and the two images `widget_glyph` swaps between. The timer's two views and the
 * ring are the layout's to carry or leave out: RemoteViews skips an action whose view a layout does not have.
 */
class PlayWidgetStyle(
    @LayoutRes val layout: Int,
    @DrawableRes val playImage: Int = R.drawable.ic_widget_play,
    @DrawableRes val pauseImage: Int = R.drawable.ic_widget_pause,
)

/** One home-screen play button in the look its [style] gives it; the behaviour is the same for every style. */
abstract class PlayWidgetProvider(val style: PlayWidgetStyle) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, PlayWidgets.render(context, style, PlayWidgets.face(context)))
    }
}

/** The app's own play button: the accent circle on the purple gradient. */
class ButtonWidget : PlayWidgetProvider(PlayWidgetStyle(R.layout.widget_button))

/** The dark theme's button: the ivory circle on black. */
class DarkButtonWidget : PlayWidgetProvider(PlayWidgetStyle(R.layout.widget_dark_button))

/** A ring that empties with the sleep timer around the glyph and the countdown. */
class TimerRingWidget : PlayWidgetProvider(PlayWidgetStyle(R.layout.widget_timer_ring))

/** The launcher icon's crescent, with the glyph in its shadow and stars out while the noise plays. */
class MoonWidget :
    PlayWidgetProvider(
        PlayWidgetStyle(R.layout.widget_moon, R.drawable.widget_moon_play, R.drawable.widget_moon_pause),
    )

/** The launcher icon itself, with a play badge in its corner. */
class IconWidget :
    PlayWidgetProvider(
        PlayWidgetStyle(R.layout.widget_icon, R.drawable.widget_icon_play, R.drawable.widget_icon_pause),
    )

/** A translucent tile over the wallpaper; Android cannot blur what lies behind a widget, so it is only see-through. */
class GlassWidget : PlayWidgetProvider(PlayWidgetStyle(R.layout.widget_glass))
