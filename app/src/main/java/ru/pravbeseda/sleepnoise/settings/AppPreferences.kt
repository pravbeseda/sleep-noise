package ru.pravbeseda.sleepnoise.settings

/** The app-wide store: every noise's level and switch, the theme, the language and the timer. */
const val APP_PREFS = "AppPreferences"
const val CURRENT_THEME = "selectedTheme"
const val CURRENT_LANGUAGE = "selectedLanguage"
const val TIMER_MINUTES = "timerMinutes"

/** The timer's own file, and the key every released version stored its minutes under, read once to move them across. */
const val LEGACY_TIMER_PREFS = "timer_prefs"
const val LEGACY_TIMER_MINUTES = "timer_value"
