package ru.pravbeseda.sleepnoise.review

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
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

    /** Issue #145: an unbound Play service fails the request with a RuntimeException, not a ReviewException. */
    @Test
    fun aPlayThatCannotBeReachedStillCountsAsTheOneAsk() {
        recordLongSessions(LONG_SESSIONS_BEFORE_REVIEW)

        askAt(hourOfDay = 12) { activity -> Unreachable(FakeReviewManager(activity)) }

        assertTrue(settings.reviewRequested)
    }

    private fun recordLongSessions(count: Int) = repeat(count) { settings.recordSession(LONG_SESSION_MILLIS) }

    private fun askAt(hourOfDay: Int, play: (Activity) -> ReviewManager = ::FakeReviewManager) {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            screen.onActivity { activity ->
                ReviewPrompt(activity, settings, play(activity)) { hourOfDay }.askIfDue()
            }
            // The request's answer arrives in a later main-thread message, and so does a crash it causes.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    /** Play's review service failing to bind, as it does where the Play Store is missing or disabled. */
    private class Unreachable(fake: FakeReviewManager) : ReviewManager by fake {
        override fun requestReviewFlow(): Task<ReviewInfo> = Tasks.forException(RuntimeException("Failed to bind to the service."))
    }

    private companion object {
        const val TEST_PREFS = "ReviewPromptTest"
    }
}
