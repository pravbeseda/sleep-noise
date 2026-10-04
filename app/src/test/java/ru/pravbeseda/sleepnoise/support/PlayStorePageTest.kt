package ru.pravbeseda.sleepnoise.support

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayStorePageTest {

    @Test
    fun urlIsTheAppsDetailsPageOnGooglePlay() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=ru.pravbeseda.sleepnoise",
            PlayStorePage.url("ru.pravbeseda.sleepnoise"),
        )
    }
}
