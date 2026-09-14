package ru.pravbeseda.sleepnoise.support

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackMailTest {

    // Blank lines around the block leave the user room to write above it.
    @Test
    fun bodyListsTheDeviceAndTheAppVersionBetweenBlankLines() {
        val body = FeedbackMail.body(
            device = "panther",
            model = "Pixel 7",
            sdk = 34,
            osVersion = "14",
            appVersion = "1.1.0",
        )

        assertEquals("\ndevice: panther\nmodel: Pixel 7\nSDK: 34\nOSVer: 14\nAppVer: 1.1.0\n\n", body)
    }
}
