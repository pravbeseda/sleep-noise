package ru.pravbeseda.sleepnoise

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.widget.ImageButton
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.playback.PlaybackService
import ru.pravbeseda.sleepnoise.settings.LocaleController
import ru.pravbeseda.sleepnoise.settings.settingsRepository
import java.util.Locale

/**
 * Issue #157: a session with no sleep timer has no tick to rebuild its notification on, so a language picked
 * mid-session reached the screen and left the notification in the previous one until playback restarted.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackNotificationLanguageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val settings = settingsRepository(context)

    @Before
    fun prepare() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        settings.timerMinutes = 0
        applyAppLocale(SYSTEM_LOCALE)
    }

    @After
    fun followTheSystemAgain() = applyAppLocale(SYSTEM_LOCALE)

    @Test
    fun aLanguagePickedMidSessionReachesTheNotification() {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            screen.onActivity { it.findViewById<ImageButton>(R.id.playButton).performClick() }
            try {
                awaitNotificationText(inLanguage(DEFAULT_TEXT_LANGUAGE).getString(R.string.notification_playing))

                screen.onActivity { LocaleController(settingsRepository(it)).select(PICKED_LANGUAGE) }
                screen.awaitLanguage(PICKED_LANGUAGE)

                awaitNotificationText(inLanguage(PICKED_LANGUAGE).getString(R.string.notification_playing))
            } finally {
                screen.onActivity { it.startService(Intent(it, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP)) }
                instrumentation.waitForIdleSync()
            }
        }
    }

    /** The system's per-app language page takes the user out of the app, which drops the binding the picker keeps. */
    @Test
    fun aLanguageChangedWhileTheScreenIsAwayReachesTheNotification() {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            screen.onActivity { it.findViewById<ImageButton>(R.id.playButton).performClick() }
            try {
                awaitNotificationText(inLanguage(DEFAULT_TEXT_LANGUAGE).getString(R.string.notification_playing))

                screen.moveToState(Lifecycle.State.CREATED)
                applyAppLocale(PICKED_LANGUAGE)
                screen.moveToState(Lifecycle.State.RESUMED)
                screen.awaitLanguage(PICKED_LANGUAGE)

                awaitNotificationText(inLanguage(PICKED_LANGUAGE).getString(R.string.notification_playing))
            } finally {
                screen.onActivity { it.startService(Intent(it, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP)) }
                instrumentation.waitForIdleSync()
            }
        }
    }

    private fun awaitNotificationText(expected: String) {
        val deadline = SystemClock.elapsedRealtime() + NOTIFICATION_WAIT_MILLIS
        var text: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            text = context.getSystemService(NotificationManager::class.java).activeNotifications
                .singleOrNull()?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            if (text == expected) return
            Thread.sleep(POLL_MILLIS)
        }
        assertEquals("the notification's text", expected, text)
    }

    private fun inLanguage(language: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        },
    )

    private companion object {
        const val DEFAULT_TEXT_LANGUAGE = "en"
        const val PICKED_LANGUAGE = "ru"
        const val NOTIFICATION_WAIT_MILLIS = 3_000L
        const val POLL_MILLIS = 100L
    }
}
