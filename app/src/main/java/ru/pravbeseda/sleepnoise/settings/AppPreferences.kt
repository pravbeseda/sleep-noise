package ru.pravbeseda.sleepnoise.settings

/**
 * The app-wide store: every noise's level and switch, the theme, the timer, what the rating prompt counts and the
 * update offer last dismissed.
 */
const val APP_PREFS = "AppPreferences"
const val CURRENT_THEME = "selectedTheme"
const val TIMER_MINUTES = "timerMinutes"
const val LONG_SESSIONS = "longSessions"
const val REVIEW_REQUESTED = "reviewRequested"
const val DECLINED_UPDATE_VERSION = "declinedUpdateVersion"

/** Where every released version stored the language, read once to hand it to AppCompat, which holds it since. */
const val LEGACY_LANGUAGE = "selectedLanguage"

/** The timer's own file, and the key every released version stored its minutes under, read once to move them across. */
const val LEGACY_TIMER_PREFS = "timer_prefs"
const val LEGACY_TIMER_MINUTES = "timer_value"
