package ru.pravbeseda.sleepnoise.store

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The store texts under `src/main/play` are uploaded verbatim, and Play answers a text over its
 * limit with a rejected edit rather than with a warning. These are the rules that rejection would
 * otherwise teach, checked here instead.
 */
class PlayMetadataTest {
    @Test
    fun everyShippedLocaleHasAListing() {
        val missing = shippedPlayLocales().filterNot { listingDir(it).isDirectory }
        if (missing.isNotEmpty()) {
            fail("$PLAY_PATH/listings has no directory for: ${missing.joinToString()}")
        }
    }

    @Test
    fun noListingExistsForALocaleTheAppDoesNotShip() {
        val shipped = shippedPlayLocales()
        val extra = subdirectoryNames(existingDir("$PLAY_PATH/listings")) - shipped.toSet()
        if (extra.isNotEmpty()) {
            fail("$PLAY_PATH/listings holds a listing the app has no locale for: ${extra.joinToString()}")
        }
    }

    @Test
    fun everyListingHasItsThreeTextsAndAReleaseNote() {
        val failures = shippedPlayLocales().flatMap { locale ->
            (LISTING_FILES.map { File(listingDir(locale), it) } + releaseNote(locale))
                .filterNot { it.isFile && it.readText().isNotBlank() }
                .map { "${it.path} is missing or empty" }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }

    @Test
    fun noReleaseNoteExistsForALocaleWithNoListing() {
        val listed = subdirectoryNames(existingDir("$PLAY_PATH/listings"))
        val extra = subdirectoryNames(existingDir("$PLAY_PATH/release-notes")) - listed.toSet()
        if (extra.isNotEmpty()) {
            fail("$PLAY_PATH/release-notes holds a note for an unlisted locale: ${extra.joinToString()}")
        }
    }

    @Test
    fun noTextExceedsPlaysLimit() {
        val failures = shippedPlayLocales().flatMap { locale ->
            val listing = listingDir(locale)
            listOf(
                File(listing, TITLE) to TITLE_LIMIT,
                File(listing, SHORT_DESCRIPTION) to SHORT_DESCRIPTION_LIMIT,
                File(listing, FULL_DESCRIPTION) to FULL_DESCRIPTION_LIMIT,
                releaseNote(locale) to RELEASE_NOTE_LIMIT,
            ).mapNotNull { (file, limit) -> overLimit(file, limit) }
        }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }

    @Test
    fun theAccountWideFilesArePresent() {
        val failures = listOf("contact-email.txt", "default-language.txt")
            .map { File(existingDir(PLAY_PATH), it) }
            .filterNot { it.isFile && it.readText().isNotBlank() }
            .map { "${it.path} is missing or empty" }
        if (failures.isNotEmpty()) {
            fail(failures.joinToString("\n"))
        }
    }

    /** The locales the app itself ships, read from the `lang` string each of them declares. */
    private fun shippedPlayLocales(): List<String> {
        val declarations = existingDir("src/main/res").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values") }
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
            .map { it to LANG_STRING.find(it.readText())?.groupValues?.get(1) }

        val undeclared = declarations.filter { (_, lang) -> lang == null }.map { (file, _) -> file.path }
        if (undeclared.isNotEmpty()) {
            fail("no <string name=\"lang\"> in: ${undeclared.joinToString()}")
        }
        val languages = declarations.mapNotNull { (_, lang) -> lang }
        if (languages.isEmpty()) {
            fail("src/main/res declares no locale, so this test would pass having checked nothing")
        }
        val unmapped = languages.filterNot { PLAY_LOCALES.containsKey(it) }
        if (unmapped.isNotEmpty()) {
            fail("no Play code known for ${unmapped.joinToString()}: add it to PLAY_LOCALES and give it a listing")
        }
        return languages.mapNotNull { PLAY_LOCALES[it] }.sorted()
    }

    private fun listingDir(locale: String) = File(existingDir("$PLAY_PATH/listings"), locale)

    private fun releaseNote(locale: String) = File(existingDir("$PLAY_PATH/release-notes"), "$locale/default.txt")

    private fun subdirectoryNames(directory: File) = directory.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()

    /** Null when the file fits or is missing; a missing one is named by the completeness test. */
    private fun overLimit(file: File, limit: Int): String? {
        if (!file.isFile) return null
        val length = file.readText().trimEnd('\n').length
        return if (length > limit) "${file.path}: $length characters, Play allows $limit" else null
    }

    /** A directory that has to exist: a moved tree would otherwise leave this test inspecting nothing. */
    private fun existingDir(relative: String): File {
        val directory = File(relative).absoluteFile
        if (!directory.isDirectory) {
            fail("$directory does not exist; a unit test runs with the module directory as its working directory")
        }
        return directory
    }

    private companion object {
        const val PLAY_PATH = "src/main/play"

        const val TITLE = "title.txt"
        const val SHORT_DESCRIPTION = "short-description.txt"
        const val FULL_DESCRIPTION = "full-description.txt"
        val LISTING_FILES = listOf(TITLE, SHORT_DESCRIPTION, FULL_DESCRIPTION)

        // Play's own limits, in characters.
        const val TITLE_LIMIT = 30
        const val SHORT_DESCRIPTION_LIMIT = 80
        const val FULL_DESCRIPTION_LIMIT = 4000
        const val RELEASE_NOTE_LIMIT = 500

        // The app names a locale by its `lang` string; Play names the same locale by these codes.
        val PLAY_LOCALES = mapOf(
            "en" to "en-US",
            "ar" to "ar",
            "de" to "de-DE",
            "es" to "es-ES",
            "ru" to "ru-RU",
            "uk" to "uk",
        )

        val LANG_STRING = Regex("<string name=\"lang\">([^<]+)</string>")
    }
}
