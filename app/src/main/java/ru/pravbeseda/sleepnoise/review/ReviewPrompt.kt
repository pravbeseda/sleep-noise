package ru.pravbeseda.sleepnoise.review

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch
import ru.pravbeseda.sleepnoise.settings.SettingsRepository
import java.time.LocalTime

/**
 * Asks Google Play for its rating dialog once [isReviewDue] says so. Play alone decides whether the dialog
 * appears, under a quota it does not disclose, and reports nothing either way — which is why the menu
 * links to the store page instead of calling this.
 */
class ReviewPrompt(
    private val activity: ComponentActivity,
    private val settings: SettingsRepository,
    private val manager: ReviewManager = ReviewManagerFactory.create(activity),
    private val hourOfDay: () -> Int = { LocalTime.now().hour },
) {
    fun askIfDue() {
        if (!isReviewDue(settings.longSessions, settings.reviewRequested, hourOfDay())) return
        // Before the request rather than after it, so a failed one is the one ask as well.
        settings.reviewRequested = true
        activity.lifecycleScope.launch {
            try {
                manager.launchReview(activity, manager.requestReview())
            } catch (expected: Exception) {
                // Expected where Play is missing, outdated or unbound — the last fails with a bare RuntimeException
                // rather than a ReviewException — so a log rather than a Crashlytics report.
                Log.w(TAG, "Google Play refused the rating dialog", expected)
            }
        }
    }

    private companion object {
        const val TAG = "ReviewPrompt"
    }
}
