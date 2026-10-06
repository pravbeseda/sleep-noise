package ru.pravbeseda.sleepnoise.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import ru.pravbeseda.sleepnoise.MainActivity

/**
 * What a launcher's "Settings" on a placed widget opens: the app itself, in its own task rather than the launcher's.
 * A widget has nothing of its own to set, so the launcher's question is always answered yes.
 */
class WidgetSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        // The launcher icon's own intent, so an app already open is brought forward rather than opened twice.
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
