package ru.pravbeseda.sleepnoise.review

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {

    @Test
    fun aSessionOfThirtyMinutesIsLong() {
        assertTrue(isLongSession(30 * 60_000L))
    }

    @Test
    fun aSessionJustShortOfThirtyMinutesIsNot() {
        assertFalse(isLongSession(30 * 60_000L - 1))
    }

    @Test
    fun theTenthLongSessionMakesAReviewDueInTheDaytime() {
        assertTrue(isReviewDue(longSessions = 10, alreadyRequested = false, hourOfDay = 12))
    }

    @Test
    fun nineLongSessionsAreNotEnough() {
        assertFalse(isReviewDue(longSessions = 9, alreadyRequested = false, hourOfDay = 12))
    }

    /** Asked once, never again: a user who let it pass has answered. */
    @Test
    fun aReviewAlreadyRequestedIsNeverDueAgain() {
        assertFalse(isReviewDue(longSessions = 50, alreadyRequested = true, hourOfDay = 12))
    }

    @Test
    fun theDaytimeRunsFromNineToEightInTheEvening() {
        assertFalse(isReviewDue(longSessions = 10, alreadyRequested = false, hourOfDay = 8))
        assertTrue(isReviewDue(longSessions = 10, alreadyRequested = false, hourOfDay = 9))
        assertTrue(isReviewDue(longSessions = 10, alreadyRequested = false, hourOfDay = 19))
        assertFalse(isReviewDue(longSessions = 10, alreadyRequested = false, hourOfDay = 20))
    }
}
