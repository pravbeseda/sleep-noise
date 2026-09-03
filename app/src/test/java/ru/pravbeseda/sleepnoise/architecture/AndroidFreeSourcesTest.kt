package ru.pravbeseda.sleepnoise.architecture

import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The test-first rule in CLAUDE.md and the Kover denominator both assume these sources import
 * nothing from `android.*`. Nothing checked it before, so the assumption failed silently.
 */
class AndroidFreeSourcesTest {
    // The carve-out of the Kover filter in app/build.gradle.kts, by file rather than by its
    // NoiseEngine* glob: a second class whose name merely starts with NoiseEngine is checked here.
    private val excludedFromMedia = "NoiseEngine.kt"

    // androidx too: an androidx import is as unrunnable on the JVM as an android one.
    private val androidImport = Regex("^import androidx?\\.")

    @Test
    fun theAndroidFreeSourcesImportNothingFromAndroid() {
        val mediaFiles = kotlinFilesIn(root("src/main/java/ru/pravbeseda/sleepnoise/media"))
            .filter { it.fileName.toString() != excludedFromMedia }
        val sleepTimer = root("src/main/java/ru/pravbeseda/sleepnoise/timer/SleepTimer.kt")

        // listOf, not a bare path: a Path is Iterable over its own segments, so `list + path` appends those.
        val violations = (mediaFiles + listOf(sleepTimer)).flatMap { file ->
            Files.readAllLines(file)
                .filter { androidImport.containsMatchIn(it) }
                .map { "${file.fileName}: $it" }
        }

        if (violations.isNotEmpty()) {
            fail("these sources must stay Android-free:\n" + violations.joinToString("\n"))
        }
    }

    /** A path that has to exist: a renamed package would otherwise leave the test inspecting nothing. */
    private fun root(relative: String): Path {
        val path = Paths.get(relative).toAbsolutePath()
        if (!Files.exists(path)) {
            fail("$path does not exist; a unit test runs with the module directory as its working directory")
        }
        return path
    }

    private fun kotlinFilesIn(directory: Path): List<Path> {
        val files = Files.walk(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".kt") }.toList()
        }
        if (files.isEmpty()) {
            fail("$directory holds no Kotlin sources, so this test would pass having inspected nothing")
        }
        return files
    }
}
