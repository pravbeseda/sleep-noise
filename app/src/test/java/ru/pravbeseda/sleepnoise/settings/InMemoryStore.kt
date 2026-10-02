package ru.pravbeseda.sleepnoise.settings

/** A preferences file held in a map, typed the way `SharedPreferences` reads it back. */
class InMemoryStore : KeyValueStore {
    val values = mutableMapOf<String, Any>()

    override fun contains(key: String): Boolean = key in values

    override fun getString(key: String, default: String?): String? = values[key] as String? ?: default

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun getInt(key: String, default: Int): Int = values[key] as Int? ?: default

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun getFloat(key: String, default: Float): Float = values[key] as Float? ?: default

    override fun putFloat(key: String, value: Float) {
        values[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as Boolean? ?: default

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
