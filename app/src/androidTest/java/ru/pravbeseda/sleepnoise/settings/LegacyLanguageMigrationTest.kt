package ru.pravbeseda.sleepnoise.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.MainActivity
import ru.pravbeseda.sleepnoise.SYSTEM_LOCALE
import ru.pravbeseda.sleepnoise.applyAppLocale
import ru.pravbeseda.sleepnoise.awaitLanguage
import ru.pravbeseda.sleepnoise.read

/** An upgrade from a release that stored the language itself: AppCompat is handed it once, and the key goes. */
@RunWith(AndroidJUnit4::class)
class LegacyLanguageMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    @Before
    fun startFromAReleasedInstall() {
        awaitAppCompatsFrameworkSync()
        applyAppLocale(SYSTEM_LOCALE)
        preferences.edit(commit = true) { putString(LEGACY_LANGUAGE, STORED_LANGUAGE) }
    }

    @After
    fun leaveAnUntouchedInstall() {
        preferences.edit(commit = true) { remove(LEGACY_LANGUAGE) }
        applyAppLocale(SYSTEM_LOCALE)
    }

    @Test
    fun theStoredLanguageIsHandedToAppCompatWhenItHoldsNone() {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            assertEquals(STORED_LANGUAGE, screen.read { AppCompatDelegate.getApplicationLocales().toLanguageTags() })
            screen.awaitLanguage(STORED_LANGUAGE)
        }
        assertFalse("the legacy key is still stored", preferences.contains(LEGACY_LANGUAGE))
    }

    /** What AppCompat holds was picked later than anything an older release stored. */
    @Test
    fun theStoredLanguageNeverOverridesTheOneAppCompatHolds() {
        applyAppLocale(HELD_LANGUAGE)

        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            assertEquals(HELD_LANGUAGE, screen.read { AppCompatDelegate.getApplicationLocales().toLanguageTags() })
        }
        assertFalse("the legacy key is still stored", preferences.contains(LEGACY_LANGUAGE))
    }

    /**
     * From API 33 the first Activity of an install starts a one-time copy of AppCompat's own storage onto the
     * framework, on a thread of its own; it finds nothing to copy and writes that nothing over whatever the
     * framework holds by then. A launch that migrates while it runs would race it, so the copy is let finish
     * first. It marks itself done by enabling AppCompat's holder service.
     */
    private fun awaitAppCompatsFrameworkSync() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        ActivityScenario.launch(MainActivity::class.java).close()
        val holder = ComponentName(context, "androidx.appcompat.app.AppLocalesMetadataHolderService")
        val deadline = System.currentTimeMillis() + SYNC_TIMEOUT_MILLIS
        while (context.packageManager.getComponentEnabledSetting(holder) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            assertTrue("AppCompat never finished copying its locales onto the framework", System.currentTimeMillis() < deadline)
            Thread.sleep(SYNC_POLL_MILLIS)
        }
    }

    private companion object {
        const val STORED_LANGUAGE = "de"
        const val HELD_LANGUAGE = "ru"
        const val SYNC_TIMEOUT_MILLIS = 5_000L
        const val SYNC_POLL_MILLIS = 50L
    }
}
