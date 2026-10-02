package ru.pravbeseda.sleepnoise.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.models.Language

/** The language of the default `values` bucket, which a device in a language the app does not ship falls back to. */
const val DEFAULT_LANGUAGE = "en"

/** The languages the picker offers, and handing one to AppCompat, which stores it and applies it. */
class LocaleController(private val settings: SettingsRepository) {
    /** Every language the app ships, then the entry with no code that asks the user for a translation instead. */
    val languages: List<Language> = listOf(
        Language("ar", R.drawable.ic_arabic, R.string.arabic, "Arabic"),
        Language(DEFAULT_LANGUAGE, R.drawable.flag_united_kingdom, R.string.english),
        Language("de", R.drawable.flag_germany, R.string.german, "German"),
        Language("ru", R.drawable.flag_russia, R.string.russian, "Russian"),
        Language("es", R.drawable.flag_spain, R.string.spanish, "Spanish"),
        Language("uk", R.drawable.flag_ukraine, R.string.ukrainian, "Ukrainian"),
        Language("", R.drawable.flag_united_nations, R.string.another_language),
    )

    /**
     * Hands the language an older release stored to AppCompat while AppCompat holds none, and forgets it only
     * once AppCompat does: the hand-over recreates the screen, and by the next `onCreate` AppCompat has the
     * language on disk, so a process killed in between loses neither copy. Called after `super.onCreate`: from
     * API 33 AppCompat reaches the framework through a created Activity only, and before one exists it reads
     * nothing and writes nothing.
     */
    fun adoptLegacyLanguage() {
        val legacy = settings.legacyLanguage ?: return
        if (AppCompatDelegate.getApplicationLocales().isEmpty()) select(legacy) else settings.forgetLegacyLanguage()
    }

    /**
     * The locale change recreates the screen by itself; a `recreate()` on top of it races the change and
     * brings the screen back in the previous language.
     */
    fun select(code: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
}
