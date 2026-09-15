package ru.pravbeseda.sleepnoise

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail

/** What both locale APIs read as "follow the system", and what a test leaves the device holding. */
const val SYSTEM_LOCALE = ""

private const val LANGUAGE_ATTEMPTS = 5
private const val LANGUAGE_SETTLE_MILLIS = 500L

/**
 * Who owns a per-app locale changes at API 33, and so does the way in. Below it AppCompat holds the
 * locale itself and its own call is the only door. From 33 the framework holds it, and AppCompat's call
 * reaches it through a context it picks up from a running Activity: asked before the first launch it
 * stored nothing at all — measured on an API 36 emulator, where `getApplicationLocales` came back empty
 * and every locale was photographed in English. So the framework is asked directly.
 */
fun applyAppLocale(language: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        instrumentation.targetContext.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
    } else {
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)) }
    }
}

/**
 * The locale is applied asynchronously either way, so the screen is asked what language it came up in and
 * given [LANGUAGE_ATTEMPTS] recreations to come up in the right one.
 */
fun ActivityScenario<MainActivity>.awaitLanguage(language: String) {
    repeat(LANGUAGE_ATTEMPTS) {
        if (read { it.getString(R.string.lang) } == language) return
        Thread.sleep(LANGUAGE_SETTLE_MILLIS)
        recreate()
    }
    fail(
        "the screen is in ${read { it.getString(R.string.lang) }} and was asked for $language; " +
            "its own configuration says ${read { it.resources.configuration.locales.toLanguageTags() }}",
    )
}

fun <T : Any> ActivityScenario<MainActivity>.read(of: (MainActivity) -> T): T {
    var value: T? = null
    onActivity { value = of(it) }
    return requireNotNull(value) { "the screen answered with nothing" }
}
