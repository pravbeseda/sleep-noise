package ru.pravbeseda.sleepnoise.support

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import ru.pravbeseda.sleepnoise.BuildConfig
import ru.pravbeseda.sleepnoise.R

/** The mail a user sends the developer, pre-filled with what a report is read against. */
object FeedbackMail {
    private const val ADDRESS = "kalugaman@gmail.com"

    /** A chooser rather than the bare intent, so a user with several mail apps is asked which one. */
    fun chooser(context: Context): Intent {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:$ADDRESS".toUri()
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
            putExtra(
                Intent.EXTRA_TEXT,
                body(Build.DEVICE, Build.MODEL, Build.VERSION.SDK_INT, Build.VERSION.RELEASE, BuildConfig.VERSION_NAME),
            )
        }
        return Intent.createChooser(intent, context.getString(R.string.mail_choose))
    }

    internal fun body(device: String, model: String, sdk: Int, osVersion: String, appVersion: String): String =
        "\ndevice: $device\nmodel: $model\nSDK: $sdk\nOSVer: $osVersion\nAppVer: $appVersion\n\n"
}
