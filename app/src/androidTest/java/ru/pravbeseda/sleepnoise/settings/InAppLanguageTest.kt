package ru.pravbeseda.sleepnoise.settings

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.MainActivity
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.SYSTEM_LOCALE
import ru.pravbeseda.sleepnoise.applyAppLocale
import ru.pravbeseda.sleepnoise.awaitLanguage

/**
 * Issue #157: from API 33 the framework recreates the open screens in a newly picked language and leaves the
 * application's configuration in the old one until the process restarts, so the service's notification and the
 * widgets, which have no screen to read it from, kept the previous language.
 */
@RunWith(AndroidJUnit4::class)
class InAppLanguageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun followTheSystemAgain() = applyAppLocale(SYSTEM_LOCALE)

    @Test
    fun aLanguagePickedWithTheScreenOpenReachesTheApplicationContext() {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            screen.onActivity { LocaleController(settingsRepository(it)).select(PICKED_LANGUAGE) }
            screen.awaitLanguage(PICKED_LANGUAGE)
        }

        assertEquals(PICKED_LANGUAGE, inAppLanguage(context.applicationContext).getString(R.string.lang))
    }

    private companion object {
        const val PICKED_LANGUAGE = "ru"
    }
}
