package ru.pravbeseda.sleepnoise.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pravbeseda.sleepnoise.models.AppTheme
import ru.pravbeseda.sleepnoise.review.LONG_SESSION_MILLIS
import ru.pravbeseda.sleepnoise.update.NO_DECLINED_VERSION

class SettingsRepositoryTest {
    private val appStore = InMemoryStore()
    private val legacyTimerStore = InMemoryStore()
    private val settings = SettingsRepository(appStore)

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

    @Test
    fun theTimerIsStoredInTheAppWideStore() {
        settings.timerMinutes = 90

        assertEquals(90, appStore.values[TIMER_MINUTES])
        assertEquals(90, settings.timerMinutes)
    }

    /** Every released version kept the minutes in a file of their own; an upgrade carries them across. */
    @Test
    fun theLegacyTimerIsCopiedWhenTheAppWideStoreHasNone() {
        legacyTimerStore.putInt(LEGACY_TIMER_MINUTES, 90)

        assertTrue(moveLegacyTimer(appStore, legacyTimerStore))

        assertEquals(90, settings.timerMinutes)
    }

    /** A value already in the app-wide store is newer than anything the old file can hold. */
    @Test
    fun theLegacyTimerNeverOverwritesTheAppWideOne() {
        appStore.putInt(TIMER_MINUTES, 30)
        legacyTimerStore.putInt(LEGACY_TIMER_MINUTES, 90)

        assertTrue(moveLegacyTimer(appStore, legacyTimerStore))

        assertEquals(30, settings.timerMinutes)
    }

    /** With no old file there is nothing to copy and nothing to delete. */
    @Test
    fun anInstallWithNoLegacyTimerHasNothingToMove() {
        assertFalse(moveLegacyTimer(appStore, legacyTimerStore))

        assertFalse(appStore.values.containsKey(TIMER_MINUTES))
    }

    /** Every released version stored the language itself; an upgrade hands it to AppCompat once. */
    @Test
    fun theLegacyLanguageIsReadFromItsKey() {
        appStore.putString(LEGACY_LANGUAGE, "de")

        assertEquals("de", settings.legacyLanguage)
    }

    @Test
    fun anInstallThatNeverPickedALanguageHasNoLegacyOne() {
        assertNull(settings.legacyLanguage)
    }

    @Test
    fun aForgottenLegacyLanguageLeavesTheStore() {
        appStore.putString(LEGACY_LANGUAGE, "de")

        settings.forgetLegacyLanguage()

        assertFalse(appStore.contains(LEGACY_LANGUAGE))
        assertNull(settings.legacyLanguage)
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

    @Test
    fun anUntouchedInstallHasNoLongSessionsAndWasNeverAskedForAReview() {
        assertEquals(0, settings.longSessions)
        assertFalse(settings.reviewRequested)
    }

    @Test
    fun aLongSessionIsCountedAndAShortOneIsNot() {
        settings.recordSession(LONG_SESSION_MILLIS)
        settings.recordSession(LONG_SESSION_MILLIS - 1)
        settings.recordSession(LONG_SESSION_MILLIS)

        assertEquals(2, appStore.values[LONG_SESSIONS])
        assertEquals(2, settings.longSessions)
    }

    @Test
    fun theReviewRequestIsStoredUnderItsKey() {
        settings.reviewRequested = true

        assertEquals(true, appStore.values[REVIEW_REQUESTED])
        assertTrue(settings.reviewRequested)
    }

    @Test
    fun anUntouchedInstallHasDeclinedNoUpdate() {
        assertEquals(NO_DECLINED_VERSION, settings.declinedUpdateVersion)
    }

    @Test
    fun theDeclinedUpdateIsStoredUnderItsKey() {
        settings.declinedUpdateVersion = 320

        assertEquals(320, appStore.values[DECLINED_UPDATE_VERSION])
        assertEquals(320, settings.declinedUpdateVersion)
    }

    private companion object {
        const val DEFAULT_VOLUME = 0.3f
    }
}
