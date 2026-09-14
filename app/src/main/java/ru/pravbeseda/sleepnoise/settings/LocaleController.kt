package ru.pravbeseda.sleepnoise.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.models.Language

/** The stored language, applying it, and the languages the picker offers. */
class LocaleController(context: Context) {
    private val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    /** Every language the app ships, then the entry with no code that asks the user for a translation instead. */
    val languages: List<Language> = listOf(
        Language("ar", R.drawable.ic_arabic, R.string.arabic, "Arabic"),
        Language("en", R.drawable.flag_united_kingdom, R.string.english),
        Language("de", R.drawable.flag_germany, R.string.german, "German"),
        Language("ru", R.drawable.flag_russia, R.string.russian, "Russian"),
        Language("es", R.drawable.flag_spain, R.string.spanish, "Spanish"),
        Language("uk", R.drawable.flag_ukraine, R.string.ukrainian, "Ukrainian"),
        Language("", R.drawable.flag_united_nations, R.string.another_language),
    )

    fun applyStored() = applyLanguage(preferences.getString(CURRENT_LANGUAGE, "en") ?: "en")

    /** Stores [code] and applies it; the screen shows it once it is recreated. */
    fun select(code: String) {
        preferences.edit { putString(CURRENT_LANGUAGE, code) }
        applyLanguage(code)
    }

    private fun applyLanguage(code: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
}
