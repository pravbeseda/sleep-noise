package ru.pravbeseda.sleepnoise.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** The one place [SettingsRepository] meets `SharedPreferences`. */
class SharedPreferencesStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String?): String? = preferences.getString(key, default)

    override fun putString(key: String, value: String) = preferences.edit { putString(key, value) }

    override fun getInt(key: String, default: Int): Int = preferences.getInt(key, default)

    override fun putInt(key: String, value: Int) = preferences.edit { putInt(key, value) }

    override fun getFloat(key: String, default: Float): Float = preferences.getFloat(key, default)

    override fun putFloat(key: String, value: Float) = preferences.edit { putFloat(key, value) }

    override fun getBoolean(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) = preferences.edit { putBoolean(key, value) }
}

/** The repository over the app's real preference files. */
fun settingsRepository(context: Context): SettingsRepository {
    fun store(name: String) = SharedPreferencesStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    return SettingsRepository(store(APP_PREFS), store(TIMER_PREFS))
}
