package com.whatuneed.ovisiontv.kotlin.data

import com.whatuneed.ovisiontv.kotlin.ui.components.clockPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Both languages ship in one APK, so a missing translation or a format string
 * that drifted only shows up on a TV. The XML files are read from the module
 * directory, like [NetworkSecurityConfigTest] reads the network config.
 */
class LocalizationTest {

    private val english = parse("src/main/res/values/strings.xml")
    private val russian = parse("src/main/res/values-ru/strings.xml")

    @Test
    fun bothLanguagesDefineTheSameNames() {
        assertEquals(english.keys, russian.keys)
    }

    @Test
    fun everyFormatPlaceholderMatches() {
        for (name in english.keys) {
            assertEquals("$name placeholders", placeholders(english.getValue(name)),
                placeholders(russian.getValue(name)))
        }
    }

    @Test
    fun russianCallsAerialVideosTheSameWordEverywhere() {
        val offenders = russian.filterValues { text ->
            Regex("клип", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }
        assertEquals("RU uses «ролик»: $offenders", emptyMap<String, String>(), offenders)
    }

    @Test
    fun screenSaverIsWrittenTheSameWay() {
        val offenders = english.filterValues { it.contains("creensaver") }
        assertEquals("EN writes «Screen Saver»: $offenders", emptyMap<String, String>(), offenders)
        assertFalse(russian.values.any { it.contains("Обновлять ролики") })
    }

    @Test
    fun clockFollowsTheInterfaceLanguage() {
        assertEquals("H:mm", clockPattern("ru", minutes = true))
        assertEquals("H", clockPattern("ru", minutes = false))
        assertEquals("h:mm a", clockPattern("en", minutes = true))
        assertEquals("h a", clockPattern("en", minutes = false))
    }

    private fun parse(path: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File(path))
        val result = LinkedHashMap<String, String>()
        val nodes = document.getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val name = element.getAttribute("name")
            if (name.isEmpty()) continue
            val text = if (element.tagName == "plurals") {
                val items = element.getElementsByTagName("item")
                (0 until items.length).joinToString("|") { items.item(it).textContent }
            } else {
                element.textContent
            }
            result[name] = text
        }
        return result
    }

    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[a-zA-Z]").findAll(text).map { it.value }.distinct().sorted().toList()
}
