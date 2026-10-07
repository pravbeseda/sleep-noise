package ru.pravbeseda.sleepnoise.settings

import android.app.LocaleManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.SYSTEM_LOCALE
import ru.pravbeseda.sleepnoise.applyAppLocale

/**
 * Issue #151: AppCompat's one-time sync, run while its holder service is still disabled, copied an empty
 * locale file over the language the framework held. The service's state is the whole switch, so it is set
 * here as a fresh install has it rather than waited for.
 */
@RunWith(AndroidJUnit4::class)
class KeepFrameworkLocaleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val holder = ComponentName(context, APP_LOCALES_HOLDER)
    private var holderStateBefore = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

    @Before
    fun startAsAFreshInstall() {
        assumeTrue("the framework holds per-app locales from API 33", Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        holderStateBefore = context.packageManager.getComponentEnabledSetting(holder)
        setHolder(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    @After
    fun putTheDeviceBack() {
        applyAppLocale(SYSTEM_LOCALE)
        setHolder(holderStateBefore)
    }

    @Test
    fun aLanguageTheFrameworkHoldsMarksTheSyncDone() {
        applyAppLocale("ru")

        keepFrameworkLocale(context)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, context.packageManager.getComponentEnabledSetting(holder))
        assertEquals("ru", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
    }

    /** Nothing to keep, so AppCompat's sync still runs and can carry a language stored before API 33 across. */
    @Test
    fun noLanguageLeavesTheSyncToAppCompat() {
        applyAppLocale(SYSTEM_LOCALE)

        keepFrameworkLocale(context)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(holder))
        assertEquals(LocaleList.getEmptyLocaleList(), context.getSystemService(LocaleManager::class.java).applicationLocales)
    }

    private fun setHolder(state: Int) = context.packageManager.setComponentEnabledSetting(holder, state, PackageManager.DONT_KILL_APP)
}
