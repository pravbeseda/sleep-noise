package ru.pravbeseda.sleepnoise.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pravbeseda.sleepnoise.models.AppTheme

class SettingsRepositoryTest {
    private val appStore = InMemoryStore()
    private val timerStore = InMemoryStore()
    private val settings = SettingsRepository(appStore, timerStore)

    private val noise = NoiseSetting("pinkNoiseVolume", "pinkNoiseEnabled", DEFAULT_VOLUME)

    @Test
    fun anUntouchedInstallHasTheDefaultThemeAndNoTimer() {
        assertEquals(AppTheme.DEFAULT, settings.theme)
        assertEquals(0, settings.timerMinutes)
    }

    @Test
    fun theThemeIsStoredUnderItsKey() {
        settings.theme = AppTheme.DARK

        assertEquals(AppTheme.DARK.key, appStore.values[CURRENT_THEME])
        assertEquals(AppTheme.DARK, settings.theme)
    }

    /** A theme an older release stored, `light` or `system`, reads as the default rather than failing. */
    @Test
    fun aRetiredThemeReadsAsTheDefault() {
        appStore.putString(CURRENT_THEME, "light")

        assertEquals(AppTheme.DEFAULT, settings.theme)
    }

    /** The timer keeps the file and the key every released version wrote it under. */
    @Test
    fun theTimerIsStoredInItsOwnFile() {
        settings.timerMinutes = 90

        assertEquals(90, timerStore.values[TIMER_MINUTES])
        assertFalse(appStore.values.containsKey(TIMER_MINUTES))
        assertEquals(90, settings.timerMinutes)
    }

    @Test
    fun anUntouchedNoiseHasItsDefaultLevelAndIsSwitchedOn() {
        assertEquals(DEFAULT_VOLUME, settings.volume(noise), 0f)
        assertTrue(settings.isEnabled(noise))
    }

    @Test
    fun aNoiseKeepsItsLevelAndSwitchUnderItsOwnKeys() {
        settings.setVolume(noise, 0.7f)
        settings.setEnabled(noise, false)

        assertEquals(0.7f, appStore.values[noise.volumeKey])
        assertEquals(false, appStore.values[noise.enabledKey])
        assertEquals(0.7f, settings.volume(noise), 0f)
        assertFalse(settings.isEnabled(noise))
    }

    @Test
    fun aSwitchedOnNoiseIsHeardAtItsLevel() {
        settings.setVolume(noise, 0.7f)

        assertEquals(0.7f, settings.heardVolume(noise), 0f)
    }

    /** Switching a noise off silences it and leaves its level alone, so switching it on brings that level back. */
    @Test
    fun aSwitchedOffNoiseIsSilentAndKeepsItsLevel() {
        settings.setVolume(noise, 0.7f)
        settings.setEnabled(noise, false)

        assertEquals(0f, settings.heardVolume(noise), 0f)
        assertEquals(0.7f, settings.volume(noise), 0f)
    }

    @Test
    fun theGateHearsTheLevelOnlyWhileTheNoiseIsOn() {
        assertEquals(0.4f, heardVolume(0.4f, enabled = true), 0f)
        assertEquals(0f, heardVolume(0.4f, enabled = false), 0f)
    }

    private companion object {
        const val DEFAULT_VOLUME = 0.3f
    }
}
