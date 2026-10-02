package ru.pravbeseda.sleepnoise.settings

/** The app-wide store: every noise's level and switch, the theme and the language. The timer keeps a file of its own. */
const val APP_PREFS = "AppPreferences"
const val CURRENT_THEME = "selectedTheme"
const val CURRENT_LANGUAGE = "selectedLanguage"

/** The timer's own file, and the key every released version stored its minutes under. */
const val TIMER_PREFS = "timer_prefs"
const val TIMER_MINUTES = "timer_value"
