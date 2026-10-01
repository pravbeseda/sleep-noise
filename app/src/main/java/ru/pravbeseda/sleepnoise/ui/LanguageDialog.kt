package ru.pravbeseda.sleepnoise.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog
import ru.pravbeseda.sleepnoise.R
import ru.pravbeseda.sleepnoise.adapters.LanguagesArrayAdapter
import ru.pravbeseda.sleepnoise.settings.LocaleController
import ru.pravbeseda.sleepnoise.support.FeedbackMail

/**
 * The language picker, preselected on the locale that is actually active. Picking the entry with no code
 * asks the user to write in for a translation instead of applying anything.
 */
object LanguageDialog {
    fun show(context: Context, localeController: LocaleController) {
        val languages = localeController.languages
        var selected = languages.indexOfFirst { it.code == context.getString(R.string.lang) }
        AlertDialog.Builder(context)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(LanguagesArrayAdapter(context, languages.toTypedArray()), selected) { _, i -> selected = i }
            .setPositiveButton(R.string.ok) { _, _ ->
                val code = languages[selected].code
                if (code != "") localeController.select(code) else askForTranslation(context)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun askForTranslation(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(R.string.title_language_need)
            .setMessage(R.string.text_language_need)
            .setPositiveButton(R.string.mail) { _, _ -> context.startActivity(FeedbackMail.chooser(context)) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
