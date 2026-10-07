package ru.pravbeseda.sleepnoise.settings

import android.app.LocaleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.models.Language

/** The language of the default `values` bucket, which a device in a language the app does not ship falls back to. */
const val DEFAULT_LANGUAGE = "en"

/** The service the manifest declares for `autoStoreLocales`; AppCompat enables it once its one-time sync has run. */
const val APP_LOCALES_HOLDER = "androidx.appcompat.app.AppLocalesMetadataHolderService"

/**
 * Keeps a language the framework already holds from AppCompat's one-time sync (issue #151). While its holder
 * service is still disabled, AppCompat copies its own locale file to the framework if it reads no language
 * itself — and before the first Activity it reads none, while from API 33 that file is never written. So a
 * language picked in the system settings before the first launch — and very likely one restored on a new
 * phone — was replaced by an empty list. With one in place there is nothing to carry across, and the sync is marked as done.
 * Called before AppCompat attaches, which is where the sync starts.
 */
fun keepFrameworkLocale(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val packages = context.packageManager
    val holder = ComponentName(context, APP_LOCALES_HOLDER)
    val syncPending = packages.getComponentEnabledSetting(holder) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    if (syncPending && !context.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty) {
        packages.setComponentEnabledSetting(holder, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }
}

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
