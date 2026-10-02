package ru.pravbeseda.sleepnoise.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** An upgrade from a release that kept the timer in a file of its own, on the device's real files. */
@RunWith(AndroidJUnit4::class)
class LegacyTimerMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val legacyFile = context.dataDir.resolve("shared_prefs/$LEGACY_TIMER_PREFS.xml")
    private val appFile = context.dataDir.resolve("shared_prefs/$APP_PREFS.xml")

    @Before
    fun startFromAReleasedInstall() {
        forgetTimer()
        context.getSharedPreferences(LEGACY_TIMER_PREFS, Context.MODE_PRIVATE)
            .edit(commit = true) { putInt(LEGACY_TIMER_MINUTES, STORED_MINUTES) }
    }

    /** The test writes real preferences on the device, so it takes them back out again. */
    @After
    fun leaveAnUntouchedInstall() = forgetTimer()

    private fun forgetTimer() {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit(commit = true) { remove(TIMER_MINUTES) }
        context.deleteSharedPreferences(LEGACY_TIMER_PREFS)
    }

    @Test
    fun theStoredMinutesMoveAcrossAndTheOldFileIsDeleted() {
        val settings = settingsRepository(context)

        assertEquals(STORED_MINUTES, settings.timerMinutes)
        assertEquals(STORED_MINUTES, context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).getInt(TIMER_MINUTES, 0))
        assertFalse("the old file at $legacyFile", legacyFile.exists())
    }

    /** The old file is gone once the repository is open, so the minutes have to be on disk by then, not queued. */
    @Test
    fun theMovedMinutesAreOnDiskBeforeTheOldFileIsDeleted() {
        settingsRepository(context)

        assertTrue("timerMinutes in $appFile", appFile.readText().contains("name=\"$TIMER_MINUTES\" value=\"$STORED_MINUTES\""))
    }

    private companion object {
        const val STORED_MINUTES = 90
    }
}
