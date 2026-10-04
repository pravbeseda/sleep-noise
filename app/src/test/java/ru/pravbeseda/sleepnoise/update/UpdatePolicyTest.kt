package ru.pravbeseda.sleepnoise.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun anUpdateNothingDeclinedIsOffered() {
        assertEquals(UpdateAction.OFFER, action(available = 320))
    }

    /** Asked once per version: a dismissed offer waits for the next release. */
    @Test
    fun theDeclinedVersionIsNotOfferedAgain() {
        assertEquals(UpdateAction.NOTHING, action(available = 320, declined = 320))
    }

    @Test
    fun aVersionAboveTheDeclinedOneIsOffered() {
        assertEquals(UpdateAction.OFFER, action(available = 321, declined = 320))
    }

    @Test
    fun nothingIsOfferedWhenPlayHasNoFlexibleUpdate() {
        assertEquals(UpdateAction.NOTHING, action(available = 320, offerable = false))
    }

    @Test
    fun aDownloadUnderWayIsNotOfferedAgain() {
        assertEquals(UpdateAction.NOTHING, action(available = 320, install = InstallProgress.RUNNING))
    }

    /** The user accepted the download, so neither a dismissal on record nor Play's availability hides the restart. */
    @Test
    fun aFinishedDownloadAsksForTheRestart() {
        assertEquals(UpdateAction.RESTART, action(available = 320, declined = 320, offerable = false, install = InstallProgress.DOWNLOADED))
    }

    private fun action(
        available: Int,
        declined: Int = NO_DECLINED_VERSION,
        offerable: Boolean = true,
        install: InstallProgress = InstallProgress.NONE,
    ) = updateAction(offerable, available, declined, install)
}
