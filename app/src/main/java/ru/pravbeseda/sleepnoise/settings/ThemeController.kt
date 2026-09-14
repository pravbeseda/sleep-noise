package ru.pravbeseda.sleepnoise.settings

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.core.content.edit
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.models.AppTheme

/** The stored theme, and the style and the action-bar icon that stand for it. */
class ThemeController(context: Context) {
    private val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    val theme: AppTheme get() = AppTheme.fromKey(preferences.getString(CURRENT_THEME, null))

    @get:StyleRes
    val style: Int
        get() = when (theme) {
            AppTheme.PURPLE -> R.style.Theme_SleepNoise_Purple
            AppTheme.DARK -> R.style.Theme_SleepNoise_Dark
        }

    /** The icon names the theme in force, so the button says where the last press landed. */
    @get:DrawableRes
    val icon: Int
        get() = when (theme) {
            AppTheme.PURPLE -> R.drawable.ic_theme_purple
            AppTheme.DARK -> R.drawable.ic_theme_dark
        }

    /** Stores the theme after the current one; the screen shows it once it is recreated. */
    fun switchToNext() = preferences.edit { putString(CURRENT_THEME, theme.next().key) }
}
