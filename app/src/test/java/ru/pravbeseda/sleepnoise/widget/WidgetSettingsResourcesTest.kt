package ru.pravbeseda.sleepnoise.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Each widget's `xml-v31` copy is its base file plus the two attributes that offer the launcher's "Settings"; Android 12
 * reads only the copy, so an edit made to one file alone would change the widget on one side of API 31 and not the other.
 */
class WidgetSettingsResourcesTest {
    @Test
    fun everyV31CopyIsItsBaseFilePlusTheSettings() {
        val base = widgetInfos("src/main/res/xml")
        val v31 = widgetInfos("src/main/res/xml-v31")
        assertTrue("no widget_*_info.xml in src/main/res/xml", base.isNotEmpty())
        assertEquals("xml-v31 copies", base.keys, v31.keys)
        base.forEach { (name, attributes) ->
            val expected = attributes.filterKeys { !it.startsWith("tools:") && it != "xmlns:tools" } + SETTINGS
            assertEquals(name, expected, v31.getValue(name))
        }
    }

    private fun widgetInfos(relative: String): Map<String, Map<String, String>> {
        val directory = File(relative).absoluteFile
        assertTrue("$directory does not exist; a unit test runs with the module directory as its working directory", directory.isDirectory)
        return directory.listFiles().orEmpty()
            .filter { it.name.startsWith("widget_") && it.name.endsWith("_info.xml") }
            .associate { it.name to rootAttributes(it) }
    }

    private fun rootAttributes(file: File): Map<String, String> {
        val attributes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.attributes
        return (0 until attributes.length).map { attributes.item(it) }.associate { it.nodeName to it.nodeValue }
    }

    private companion object {
        val SETTINGS = mapOf(
            "android:configure" to "ru.pravbeseda.sleepnoise.widget.WidgetSettingsActivity",
            "android:widgetFeatures" to "reconfigurable|configuration_optional",
        )
    }
}
