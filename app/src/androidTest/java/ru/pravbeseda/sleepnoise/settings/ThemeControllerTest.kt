package ru.pravbeseda.sleepnoise.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.models.AppTheme

@RunWith(AndroidJUnit4::class)
class ThemeControllerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    @Before
    fun startFromAnUntouchedInstall() = forgetTheme()

    /** The test writes real preferences on the device, so it takes them back out again. */
    @After
    fun leaveAnUntouchedInstall() = forgetTheme()

    private fun forgetTheme() = preferences.edit(commit = true) { remove(CURRENT_THEME) }

    @Test
    fun anUntouchedInstallOpensInTheDefaultTheme() {
        val controller = ThemeController(context)

        assertEquals(AppTheme.DEFAULT, controller.theme)
        assertEquals(R.style.Theme_SleepNoise_Purple, controller.style)
        assertEquals(R.drawable.ic_theme_purple, controller.icon)
    }

    @Test
    fun switchingStoresTheNextThemeAndComesBackRound() {
        val controller = ThemeController(context)

        controller.switchToNext()

        assertEquals(AppTheme.DARK.key, preferences.getString(CURRENT_THEME, null))
        assertEquals(R.style.Theme_SleepNoise_Dark, controller.style)
        assertEquals(R.drawable.ic_theme_dark, controller.icon)

        controller.switchToNext()

        assertEquals(AppTheme.PURPLE, controller.theme)
    }
}
