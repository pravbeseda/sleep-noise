package ru.pravbeseda.sleepnoise.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** The app's own page on Google Play, where a user rates it on their own initiative. */
object PlayStorePage {
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /**
     * In the Play Store app where it is installed, in a browser where it is not — the https link with the
     * Play package set is the form Google documents for both.
     */
    fun open(context: Context) {
        try {
            context.startActivity(intent(context))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(intent(context).setPackage(null))
        }
    }

    fun intent(context: Context): Intent = Intent(Intent.ACTION_VIEW, url(context.packageName).toUri()).setPackage(PLAY_STORE_PACKAGE)

    internal fun url(applicationId: String): String = "https://play.google.com/store/apps/details?id=$applicationId"
}
