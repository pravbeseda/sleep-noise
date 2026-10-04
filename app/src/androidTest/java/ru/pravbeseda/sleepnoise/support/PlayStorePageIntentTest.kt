package ru.pravbeseda.sleepnoise.support

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayStorePageIntentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun intentViewsThisAppsPageInThePlayStoreApp() {
        val intent = PlayStorePage.intent(context)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://play.google.com/store/apps/details?id=ru.pravbeseda.sleepnoise", intent.dataString)
        assertEquals("com.android.vending", intent.`package`)
    }
}
