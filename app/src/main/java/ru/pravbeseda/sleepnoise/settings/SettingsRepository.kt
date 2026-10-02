package ru.pravbeseda.sleepnoise.settings

import ru.pravbeseda.sleepnoise.models.AppTheme

/** A noise ships switched on, so an install made before the toggles existed sounds exactly as it did. */
const val DEFAULT_NOISE_ENABLED = true

/** Where one noise keeps its level and its switch, and the level it opens at when nobody has touched it. */
class NoiseSetting(val volumeKey: String, val enabledKey: String, val defaultVolume: Float)

/**
 * What the mix hears of a noise: its level while it is switched on, silence while it is not. Switching a
 * noise off never writes over its level, so switching it back on brings back what it was.
 */
fun heardVolume(volume: Float, enabled: Boolean): Float = if (enabled) volume else 0f

/**
 * Copies the minutes from the timer's old file into [app] unless [app] already holds some, and answers whether
 * the old file held any — that is, whether it is left to be deleted.
 */
fun moveLegacyTimer(app: KeyValueStore, legacy: KeyValueStore): Boolean {
    if (!legacy.contains(LEGACY_TIMER_MINUTES)) return false
    if (!app.contains(TIMER_MINUTES)) app.putInt(TIMER_MINUTES, legacy.getInt(LEGACY_TIMER_MINUTES, 0))
    return true
}

/** Every setting the app keeps, over the app-wide store. */
class SettingsRepository(private val app: KeyValueStore) {
    var theme: AppTheme
        get() = AppTheme.fromKey(app.getString(CURRENT_THEME, null))
        set(value) = app.putString(CURRENT_THEME, value.key)

    var timerMinutes: Int
        get() = app.getInt(TIMER_MINUTES, 0)
        set(value) = app.putInt(TIMER_MINUTES, value)

    fun volume(noise: NoiseSetting): Float = app.getFloat(noise.volumeKey, noise.defaultVolume)

    fun setVolume(noise: NoiseSetting, volume: Float) = app.putFloat(noise.volumeKey, volume)

    fun isEnabled(noise: NoiseSetting): Boolean = app.getBoolean(noise.enabledKey, DEFAULT_NOISE_ENABLED)

    fun setEnabled(noise: NoiseSetting, enabled: Boolean) = app.putBoolean(noise.enabledKey, enabled)

    /** For a session started with no screen in sight, which has nothing but the store to read. */
    fun heardVolume(noise: NoiseSetting): Float = heardVolume(volume(noise), isEnabled(noise))
}
