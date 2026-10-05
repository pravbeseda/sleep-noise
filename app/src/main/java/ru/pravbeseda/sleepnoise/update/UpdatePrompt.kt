package ru.pravbeseda.sleepnoise.update

import android.app.Activity
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.settings.SettingsRepository

/**
 * Offers a newer version through Google Play's flexible flow, and the restart that installs it once downloaded.
 * Both wait until nothing plays: [AppUpdateManager.completeUpdate] restarts the process, which would cut the noise.
 */
class UpdatePrompt(
    private val activity: ComponentActivity,
    private val settings: SettingsRepository,
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity),
) : DefaultLifecycleObserver {
    private var idle = false
    private var offered = false
    private var restartOffer: Snackbar? = null

    /** The update check in flight, cancelled by every later transition, so that only the latest answer acts. */
    private var check: Job? = null

    private val downloadListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED && idle) offerRestart()
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) = manager.registerListener(downloadListener)

    // Left idle, the next start would see no change and skip the check that every screen open is owed.
    override fun onStop(owner: LifecycleOwner) {
        manager.unregisterListener(downloadListener)
        idle = false
        check?.cancel()
    }

    /**
     * Fed with every state the screen renders, and acts only as [nothingPlays] turns true. [otherwise] runs
     * when there is nothing to do about an update, so a second Play dialog never follows the first.
     */
    fun onPlaybackChanged(nothingPlays: Boolean, otherwise: () -> Unit) {
        if (nothingPlays == idle) return
        idle = nothingPlays
        check?.cancel()
        if (!idle) {
            restartOffer?.dismiss()
            return
        }
        check = activity.lifecycleScope.launch {
            val info = requestInfo()
            if (info == null) {
                nothingToOffer(otherwise)
                return@launch
            }
            when (actionFor(info)) {
                UpdateAction.OFFER -> offer(info)
                UpdateAction.RESTART -> offerRestart()
                UpdateAction.NOTHING -> nothingToOffer(otherwise)
            }
        }
    }

    // Not right after an offer either, answered or not: that would be the second dialog in a row.
    private fun nothingToOffer(otherwise: () -> Unit) {
        if (!offered) otherwise()
    }

    private suspend fun requestInfo(): AppUpdateInfo? = try {
        manager.requestAppUpdateInfo()
    } catch (e: InstallException) {
        // Expected where Play is missing or outdated, so a log rather than a Crashlytics report.
        Log.w(TAG, "Google Play reported no update information", e)
        null
    }

    private fun actionFor(info: AppUpdateInfo): UpdateAction {
        val availability = info.updateAvailability()
        val flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
        val declined = settings.declinedUpdateVersion
        // Play's answer is all there is to go on when a device shows no offer, so it is logged as it arrives.
        Log.i(
            TAG,
            "Play reports availability=$availability, flexible=$flexibleAllowed, status=${info.installStatus()}, " +
                "code=${info.availableVersionCode()}, declined=$declined",
        )
        return updateAction(
            offerable = availability == UpdateAvailability.UPDATE_AVAILABLE && flexibleAllowed,
            availableVersionCode = info.availableVersionCode(),
            declinedVersionCode = declined,
            install = when (info.installStatus()) {
                InstallStatus.DOWNLOADED -> InstallProgress.DOWNLOADED
                InstallStatus.PENDING, InstallStatus.DOWNLOADING, InstallStatus.INSTALLING -> InstallProgress.RUNNING
                else -> InstallProgress.NONE
            },
        )
    }

    private fun offer(info: AppUpdateInfo) {
        offered = true
        // Not scoped to the Activity: Play's dialog stops it, and a scoped listener would be gone by the answer.
        manager.startUpdateFlow(info, activity, AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE))
            .addOnSuccessListener { resultCode ->
                // Only a dismissal is the user's answer; a flow that failed is offered again on the next open.
                if (resultCode == Activity.RESULT_CANCELED) settings.declinedUpdateVersion = info.availableVersionCode()
            }
    }

    private fun offerRestart() {
        if (restartOffer?.isShownOrQueued == true) return
        val content = activity.findViewById<View>(android.R.id.content)
        restartOffer = Snackbar.make(content, R.string.update_downloaded, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.update_restart) { manager.completeUpdate() }
            .also { offer ->
                // AppCompat passes insets to the content only when they change, so below API 30 a view added later
                // never hears of the navigation bar and is drawn under it. Material keeps what it is handed here.
                ViewCompat.getRootWindowInsets(content)?.let { ViewCompat.dispatchApplyWindowInsets(offer.view, it) }
                offer.show()
            }
    }

    private companion object {
        const val TAG = "UpdatePrompt"
    }
}
