package ru.pravbeseda.sleepnoise.update

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.MainActivity
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.settings.SettingsRepository
import ru.pravbeseda.sleepnoise.settings.SharedPreferencesStore

/**
 * The prompt against Play's own fake, which plays the dialog and the download out on command. What the real
 * Play shows, and whether its restart really installs, is checked by hand through internal app sharing.
 *
 * The settings live in a file of the test's own, as in `ReviewPromptTest`: the Activity runs a prompt of its
 * own over the app's store and the real Play, which offers nothing to a build not installed from it.
 */
@RunWith(AndroidJUnit4::class)
class UpdatePromptTest {
    private val preferences = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)
    private val settings = SettingsRepository(SharedPreferencesStore(preferences, commit = true))
    private lateinit var activity: MainActivity

    @After
    fun forgetTheTestsSettings() {
        preferences.edit().clear().commit()
    }

    @Test
    fun anUpdateIsOfferedOnceNothingPlays() = onScreen { play, prompt ->
        onMain {
            play.setUpdateAvailable(AVAILABLE_VERSION, AppUpdateType.FLEXIBLE)
            prompt.onPlaybackChanged(nothingPlays = false) {}
        }
        assertFalse(play.isConfirmationDialogVisible)

        onMain { prompt.onPlaybackChanged(nothingPlays = true) {} }
        assertTrue(play.isConfirmationDialogVisible)
    }

    @Test
    fun aDismissedVersionIsNotOfferedAgainAndTheRatingPromptGetsItsTurn() = onScreen { fake, _ ->
        val play = DismissedDialog(fake)
        onMain {
            fake.setUpdateAvailable(AVAILABLE_VERSION, AppUpdateType.FLEXIBLE)
            UpdatePrompt(activity, settings, play).onPlaybackChanged(nothingPlays = true) {}
        }
        assertEquals(AVAILABLE_VERSION, settings.declinedUpdateVersion)

        // The next open is a new screen, and a new prompt with it.
        var askedForReview = false
        onMain { UpdatePrompt(activity, settings, play).onPlaybackChanged(nothingPlays = true) { askedForReview = true } }
        assertEquals(1, play.flowsStarted)
        assertTrue(askedForReview)
    }

    @Test
    fun noRatingPromptFollowsAnOfferOnTheSameScreen() = onScreen { play, prompt ->
        var askedForReview = false
        onMain {
            play.setUpdateAvailable(AVAILABLE_VERSION, AppUpdateType.FLEXIBLE)
            prompt.onPlaybackChanged(nothingPlays = true) { askedForReview = true }
            play.userRejectsUpdate()
            prompt.onPlaybackChanged(nothingPlays = false) {}
            prompt.onPlaybackChanged(nothingPlays = true) { askedForReview = true }
        }
        assertFalse(askedForReview)
    }

    @Test
    fun aFinishedDownloadOffersTheRestartThatInstallsIt() = onScreen { play, prompt ->
        downloadToTheEnd(play, prompt)
        onView(withText(R.string.update_downloaded)).check(matches(isDisplayed()))

        // Sliding in, the button is not yet whole enough for Espresso to press.
        eventually { onView(withText(R.string.update_restart)).check(matches(isCompletelyDisplayed())) }
        onView(withText(R.string.update_restart)).perform(click())

        assertTrue(play.isInstallSplashScreenVisible)
    }

    @Test
    fun aSessionStartedWithdrawsTheRestart() = onScreen { play, prompt ->
        downloadToTheEnd(play, prompt)
        onView(withText(R.string.update_downloaded)).check(matches(isDisplayed()))

        onMain { prompt.onPlaybackChanged(nothingPlays = false) {} }

        eventually { onView(withText(R.string.update_downloaded)).check(doesNotExist()) }
    }

    @Test
    fun aDownloadFinishingDuringASessionWaitsForItsEnd() = onScreen { play, prompt ->
        onMain {
            play.setUpdateAvailable(AVAILABLE_VERSION, AppUpdateType.FLEXIBLE)
            prompt.onPlaybackChanged(nothingPlays = true) {}
            play.userAcceptsUpdate()
            play.downloadStarts()
            prompt.onPlaybackChanged(nothingPlays = false) {}
            play.downloadCompletes()
        }
        onView(withText(R.string.update_downloaded)).check(doesNotExist())

        onMain { prompt.onPlaybackChanged(nothingPlays = true) {} }

        onView(withText(R.string.update_downloaded)).check(matches(isDisplayed()))
    }

    /** Both reviews of PR #132: a check still in flight must not offer alongside the one that replaced it. */
    @Test
    fun aCheckOvertakenByAnotherOffersOnce() = onScreen { fake, _ ->
        val play = SlowAnswer(fake)
        onMain {
            fake.setUpdateAvailable(AVAILABLE_VERSION, AppUpdateType.FLEXIBLE)
            val prompt = UpdatePrompt(activity, settings, play)
            prompt.onPlaybackChanged(nothingPlays = true) {}
            prompt.onPlaybackChanged(nothingPlays = false) {}
            prompt.onPlaybackChanged(nothingPlays = true) {}
        }

        onMain { play.answerAll() }

        assertEquals(1, play.flowsStarted)
    }

    private fun downloadToTheEnd(play: FakeAppUpdateManager, prompt: UpdatePrompt) = onMain {
        play.setUpdateAvailable(AVAILABLE_VERSION, AppUpdateType.FLEXIBLE)
        prompt.onPlaybackChanged(nothingPlays = true) {}
        play.userAcceptsUpdate()
        play.downloadStarts()
        play.downloadCompletes()
    }

    /** For a Snackbar, which animates in and out on the Choreographer, where Espresso does not wait. */
    private fun eventually(check: () -> Unit) {
        val deadline = SystemClock.uptimeMillis() + ANIMATION_TIMEOUT_MS
        while (true) {
            try {
                return check()
            } catch (e: AssertionError) {
                if (SystemClock.uptimeMillis() > deadline) throw e
                SystemClock.sleep(POLL_MS)
            }
        }
    }

    /** And waits out what [block] posted, such as the listeners of a Play task that has already completed. */
    private fun onMain(block: () -> Unit) = with(InstrumentationRegistry.getInstrumentation()) {
        runOnMainSync(block)
        waitForIdleSync()
    }

    private fun onScreen(test: (FakeAppUpdateManager, UpdatePrompt) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            lateinit var play: FakeAppUpdateManager
            lateinit var prompt: UpdatePrompt
            screen.onActivity {
                activity = it
                play = FakeAppUpdateManager(it)
                prompt = UpdatePrompt(it, settings, play)
            }
            test(play, prompt)
        }
    }

    /**
     * Play's fake as a user who closes the dialog. The fake's own `startUpdateFlow` answers `RESULT_OK` before the
     * dialog is touched, so a dismissal cannot be played out on it.
     */
    private class DismissedDialog(fake: FakeAppUpdateManager) : AppUpdateManager by fake {
        var flowsStarted = 0
            private set

        override fun startUpdateFlow(info: AppUpdateInfo, activity: Activity, options: AppUpdateOptions): Task<Int> {
            flowsStarted++
            return Tasks.forResult(Activity.RESULT_CANCELED)
        }
    }

    /** Play's fake answering update checks only when told to, so that two of them can be in flight at once. */
    private class SlowAnswer(private val fake: FakeAppUpdateManager) : AppUpdateManager by fake {
        private val pending = mutableListOf<TaskCompletionSource<AppUpdateInfo>>()
        var flowsStarted = 0
            private set

        override fun getAppUpdateInfo(): Task<AppUpdateInfo> = TaskCompletionSource<AppUpdateInfo>().also { pending += it }.task

        override fun startUpdateFlow(info: AppUpdateInfo, activity: Activity, options: AppUpdateOptions): Task<Int> {
            flowsStarted++
            return fake.startUpdateFlow(info, activity, options)
        }

        fun answerAll() = pending.forEach { it.setResult(fake.appUpdateInfo.result) }
    }

    private companion object {
        const val TEST_PREFS = "UpdatePromptTest"
        const val AVAILABLE_VERSION = 320
        const val ANIMATION_TIMEOUT_MS = 2_000L
        const val POLL_MS = 50L
    }
}
