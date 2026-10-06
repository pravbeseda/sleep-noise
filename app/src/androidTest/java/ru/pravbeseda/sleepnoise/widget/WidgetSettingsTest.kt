package ru.pravbeseda.sleepnoise.widget

import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.MainActivity
import java.util.concurrent.atomic.AtomicReference

/** The "Settings" a launcher offers on a placed widget, and the app it opens. */
@RunWith(AndroidJUnit4::class)
class WidgetSettingsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val started = AtomicReference<Intent>()

    // Blocks the start it records, so the test reads the intent without the app's screen opening over it.
    private val mainActivityMonitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.component?.className != MainActivity::class.java.name) return null
            started.set(intent)
            return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        }
    }

    @After
    fun removeMonitor() {
        instrumentation.removeMonitor(mainActivityMonitor)
    }

    /**
     * Below Android 12 a launcher runs the settings when the widget is placed, and drops the widget unless they
     * answer, so they are offered from 12 on only, where they can be marked optional.
     */
    @Test
    fun everyStyleOffersItsSettingsFromAndroid12On() {
        val providers = AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null)
        assertTrue("no widget styles in the manifest", providers.isNotEmpty())
        providers.forEach { info ->
            val name = info.provider.shortClassName
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                assertEquals(name, ComponentName(context, WidgetSettingsActivity::class.java), info.configure)
                val features = AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE or
                    AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
                assertEquals("$name features", features, info.widgetFeatures and features)
            } else {
                assertNull(name, info.configure)
            }
        }
    }

    @Test
    fun theSettingsAcceptTheWidgetAndOpenTheAppInItsOwnTask() {
        instrumentation.addMonitor(mainActivityMonitor)
        val configure = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setClass(context, WidgetSettingsActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WIDGET_ID)

        val result = ActivityScenario.launchActivityForResult<WidgetSettingsActivity>(configure).use { it.result }

        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertEquals(WIDGET_ID, result.resultData?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        val opened = requireNotNull(started.get()) { "the app was not opened" }
        assertTrue("the app opened inside the launcher's task", opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    private companion object {
        const val WIDGET_ID = 42
    }
}
