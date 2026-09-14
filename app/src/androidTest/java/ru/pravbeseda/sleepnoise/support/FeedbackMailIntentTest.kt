package ru.pravbeseda.sleepnoise.support

import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.BuildConfig
import ru.pravbeseda.sleepnoise.R

@RunWith(AndroidJUnit4::class)
class FeedbackMailIntentTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun chooserWrapsAMailToTheDeveloperCarryingTheAppVersion() {
        val chooser = FeedbackMail.chooser(context)
        val mail = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_SENDTO, mail?.action)
        assertEquals("mailto:kalugaman@gmail.com", mail?.dataString)
        assertEquals(context.getString(R.string.app_name), mail?.getStringExtra(Intent.EXTRA_SUBJECT))
        val body = mail?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(body, body.contains("\nAppVer: ${BuildConfig.VERSION_NAME}\n"))
    }
}
