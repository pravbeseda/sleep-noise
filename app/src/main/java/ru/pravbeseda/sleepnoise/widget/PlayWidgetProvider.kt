package ru.pravbeseda.sleepnoise.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import androidx.annotation.LayoutRes
import ru.pravbeseda.sleepnoise.R

/** One home-screen play button, in the look its [layout] gives it; the behaviour is the same for every style. */
abstract class PlayWidgetProvider(@LayoutRes private val layout: Int) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, PlayWidgets.render(context, layout, PlayWidgets.face(context)))
    }
}

/** The app's own play button: the accent circle on the purple gradient. */
class ButtonWidget : PlayWidgetProvider(R.layout.widget_button)
