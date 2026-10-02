package ru.pravbeseda.sleepnoise.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** The one place [SettingsRepository] meets `SharedPreferences`; [commit] writes to disk before a put returns. */
class SharedPreferencesStore(private val preferences: SharedPreferences, private val commit: Boolean = false) : KeyValueStore {
    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun getString(key: String, default: String?): String? = preferences.getString(key, default)

    override fun putString(key: String, value: String) = preferences.edit(commit) { putString(key, value) }

    override fun getInt(key: String, default: Int): Int = preferences.getInt(key, default)

    override fun putInt(key: String, value: Int) = preferences.edit(commit) { putInt(key, value) }

    override fun getFloat(key: String, default: Float): Float = preferences.getFloat(key, default)

    override fun putFloat(key: String, value: Float) = preferences.edit(commit) { putFloat(key, value) }

    override fun getBoolean(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) = preferences.edit(commit) { putBoolean(key, value) }

    override fun remove(key: String) = preferences.edit(commit) { remove(key) }
}

/** The repository over the app's real preferences, with the timer's old file moved into them and deleted. */
fun settingsRepository(context: Context): SettingsRepository {
    fun preferences(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    val app = preferences(APP_PREFS)
    // Committed, since the old file is deleted straight after and a queued write would leave neither copy on disk.
    if (moveLegacyTimer(SharedPreferencesStore(app, commit = true), SharedPreferencesStore(preferences(LEGACY_TIMER_PREFS)))) {
        context.deleteSharedPreferences(LEGACY_TIMER_PREFS)
    }
    return SettingsRepository(SharedPreferencesStore(app))
}
