package ru.pravbeseda.sleepnoise.review

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.review.testing.FakeReviewManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.MainActivity
import ru.pravbeseda.sleepnoise.settings.SettingsRepository
import ru.pravbeseda.sleepnoise.settings.SharedPreferencesStore

/**
 * The prompt against Play's own fake, which answers every request and shows nothing. What the real Play
 * makes of the request, the quota included, is not something a test can see — see the PR description.
 *
 * The settings live in a file of the test's own: the Activity hosting the prompt runs its own one over
 * the app's store, and at a daytime hour it would answer a due review before the test could.
 */
@RunWith(AndroidJUnit4::class)
class ReviewPromptTest {
    private val preferences = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)
    private val settings = SettingsRepository(SharedPreferencesStore(preferences, commit = true))

    @After
    fun forgetTheTestsSettings() {
        preferences.edit().clear().commit()
    }

    @Test
    fun aDueReviewIsRequestedAndRemembered() {
        recordLongSessions(LONG_SESSIONS_BEFORE_REVIEW)

        askAt(hourOfDay = 12)

        assertTrue(settings.reviewRequested)
    }

    @Test
    fun anEveningOpeningAsksForNothing() {
        recordLongSessions(LONG_SESSIONS_BEFORE_REVIEW)

        askAt(hourOfDay = 22)

        assertFalse(settings.reviewRequested)
    }

    private fun recordLongSessions(count: Int) = repeat(count) { settings.recordSession(LONG_SESSION_MILLIS) }

    private fun askAt(hourOfDay: Int) {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                ReviewPrompt(activity, settings, FakeReviewManager(activity)) { hourOfDay }.askIfDue()
            }
        }
    }

    private companion object {
        const val TEST_PREFS = "ReviewPromptTest"
    }
}
