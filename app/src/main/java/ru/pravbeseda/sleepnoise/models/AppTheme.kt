package ru.pravbeseda.sleepnoise.models

/**
 * The themes the action-bar button cycles through, in the order it cycles them.
 *
 * [key] is what lands in preferences, so renaming one silently resets every install that stored it —
 * which is what [fromKey] does deliberately for the two themes this pair replaced.
 */
enum class AppTheme(val key: String) {
    PURPLE("purple"),
    DARK("dark"),
    ;

    fun next(): AppTheme = entries[(ordinal + 1) % entries.size]

    companion object {
        /** What a fresh install gets, and what an unreadable stored value falls back to. */
        val DEFAULT = PURPLE

        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
