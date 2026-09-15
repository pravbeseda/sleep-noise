package ru.pravbeseda.sleepnoise

import android.content.Context
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.catalog.SHIPPING_NOISES
import ru.pravbeseda.sleepnoise.settings.APP_PREFS
import ru.pravbeseda.sleepnoise.settings.CURRENT_LANGUAGE
import ru.pravbeseda.sleepnoise.settings.LocaleController

/** A language picked in the dialog is the one the screen comes back in, and stays in. */
@RunWith(AndroidJUnit4::class)
class LanguageSelectionUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    @Before
    fun startInRussian() {
        preferences.edit(commit = true) { putString(CURRENT_LANGUAGE, STARTING_LANGUAGE) }
        applyAppLocale(STARTING_LANGUAGE)
    }

    /** The per-app locale belongs to the framework rather than to the preferences, so both are handed back. */
    @After
    fun leaveAnUntouchedInstall() {
        preferences.edit(commit = true) { remove(CURRENT_LANGUAGE) }
        applyAppLocale(SYSTEM_LOCALE)
    }

    @Test
    fun theLanguagePickedInTheDialogIsTheOneTheScreenShows() {
        ActivityScenario.launch(MainActivity::class.java).use { screen ->
            screen.awaitLanguage(STARTING_LANGUAGE)

            PICKED_LANGUAGES.forEach { language ->
                screen.pickInTheDialog(language)

                // Long enough for every recreation the switch sets off to have landed.
                Thread.sleep(SETTLE_MILLIS)
                instrumentation.waitForIdleSync()
                assertEquals(language, preferences.getString(CURRENT_LANGUAGE, null))
                screen.onActivity { activity ->
                    assertEquals("the screen after picking $language", language, activity.getString(R.string.lang))
                    assertEquals(
                        "the version line after picking $language",
                        activity.getString(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        activity.findViewById<TextView>(R.id.version_text).text.toString(),
                    )
                    assertEquals(
                        "the timer label after picking $language",
                        activity.getString(R.string.timer),
                        activity.findViewById<TextView>(R.id.timerLabel).text.toString(),
                    )
                    SHIPPING_NOISES.forEach { noise ->
                        val row = activity.noiseRows.getValue(noise.volumeKey)
                        assertEquals(
                            "the ${noise.volumeKey} row after picking $language",
                            activity.getString(noise.volumeLabelRes, row.findViewById<SeekBar>(R.id.noiseSlider).progress),
                            row.findViewById<TextView>(R.id.noiseLabel).text.toString(),
                        )
                    }
                }
            }
        }
    }

    private fun ActivityScenario<MainActivity>.pickInTheDialog(language: String) {
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(startsWith(read { it.getString(R.string.language) }))).perform(click())
        val position = LocaleController(context).languages.indexOfFirst { it.code == language }
        onData(anything()).inRoot(isDialog()).atPosition(position).perform(click())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }

    private companion object {
        const val STARTING_LANGUAGE = "ru"
        val PICKED_LANGUAGES = listOf("de", "es", "en", "ru")
        const val SETTLE_MILLIS = 2_000L
    }
}
