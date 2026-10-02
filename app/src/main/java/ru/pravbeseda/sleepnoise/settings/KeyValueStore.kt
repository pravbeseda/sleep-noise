package ru.pravbeseda.sleepnoise.settings

/**
 * The few things [SettingsRepository] asks of a preferences file. Its own interface rather than
 * `SharedPreferences`, so the repository and the migrations it will carry are tested on the JVM; one
 * adapter, [SharedPreferencesStore], is the only code that touches the real thing.
 */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?

    fun putString(key: String, value: String)

    fun getInt(key: String, default: Int): Int

    fun putInt(key: String, value: Int)

    fun getFloat(key: String, default: Float): Float

    fun putFloat(key: String, value: Float)

    fun getBoolean(key: String, default: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)
}
