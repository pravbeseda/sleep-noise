package ru.pravbeseda.sleepnoise.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppThemeTest {

    @Test
    fun fromKeyReadsBackEveryKeyItWrites() {
        AppTheme.entries.forEach { theme ->
            assertEquals(theme, AppTheme.fromKey(theme.key))
        }
    }

    @Test
    fun fromKeyFallsBackToPurpleOnAFreshInstall() {
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey(null))
    }

    // "light" and "system" were the two themes this one replaced: an install that stored either
    // has nothing to apply, and the default is the closest thing to what it asked for.
    @Test
    fun fromKeyFallsBackToPurpleForARetiredTheme() {
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey("light"))
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey("system"))
    }

    @Test
    fun nextCyclesThroughEveryThemeAndReturns() {
        var theme = AppTheme.entries.first()
        repeat(AppTheme.entries.size) { theme = theme.next() }
        assertEquals(AppTheme.entries.first(), theme)
    }

    @Test
    fun nextLeavesTheThemeItStartedFrom() {
        AppTheme.entries.forEach { theme ->
            assertNotEquals(theme, theme.next())
        }
    }
}
