package ru.pravbeseda.sleepnoise.settings

import android.app.LocaleConfig
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
class LocaleConfigTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun systemSettingsOfferTheLanguagesThePickerDoes() {
        val config = LocaleConfig(context)
        val picker = LocaleController(settingsRepository(context)).languages.map { it.code }.filter { it.isNotEmpty() }

        assertEquals(LocaleConfig.STATUS_SUCCESS, config.status)
        val supported = requireNotNull(config.supportedLocales) { "the manifest declares no localeConfig" }
        val declared = (0 until supported.size()).map { supported.get(it).toLanguageTag() }
        assertEquals(picker.toSet(), declared.toSet())
    }
}
