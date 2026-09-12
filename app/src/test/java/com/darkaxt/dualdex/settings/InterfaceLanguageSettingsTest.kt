package com.darkaxt.dualdex.settings

import com.enrpau.dualscreendex.companion.model.InterfaceLanguage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceLanguageSettingsTest {
    @Test
    fun mapsExplicitSelectionsToAndroidLanguageTagsAndAutoToSystemLocales() {
        assertEquals("", InterfaceLanguageSettings.languageTags(InterfaceLanguage.AUTO))
        assertEquals("en", InterfaceLanguageSettings.languageTags(InterfaceLanguage.EN))
        assertEquals("fr", InterfaceLanguageSettings.languageTags(InterfaceLanguage.FR))
        assertEquals("de", InterfaceLanguageSettings.languageTags(InterfaceLanguage.DE))
        assertEquals("it", InterfaceLanguageSettings.languageTags(InterfaceLanguage.IT))
        assertEquals("es", InterfaceLanguageSettings.languageTags(InterfaceLanguage.ES))
    }

    @Test
    fun everyNativeLocaleDefinesTheCompleteDefaultResourceContract() {
        val defaultStrings = strings("values")

        listOf("values-fr", "values-de", "values-it", "values-es").forEach { directory ->
            assertEquals(defaultStrings.keys, strings(directory).keys)
        }
    }

    @Test
    fun bundledLocalesTranslateRecoveryAndExportFallbacks() {
        val english = strings("values")

        listOf("values-fr", "values-de", "values-it", "values-es").forEach { directory ->
            val localized = strings(directory)
            assertNotEquals(english.getValue("recovery_title"), localized.getValue("recovery_title"))
            assertNotEquals(
                english.getValue("memory_session_export_failed"),
                localized.getValue("memory_session_export_failed"),
            )
        }
        assertTrue(english.getValue("recovery_title").isNotBlank())
    }

    private fun strings(directory: String): Map<String, String> {
        val file = File("src/main/res/$directory/strings.xml")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return buildMap {
            repeat(nodes.length) { index ->
                val node = nodes.item(index)
                put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
            }
        }
    }
}
