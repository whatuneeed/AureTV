package com.whatuneed.ovisiontv.kotlin.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped policy must name every host the code can contact. This catches
 * the regression where the old text claimed Apple was the only destination.
 */
class PrivacyPolicyTextTest {

    private val en = File("src/main/res/raw/privacy_en.txt").readText()
    private val ru = File("src/main/res/raw/privacy_ru.txt").readText()

    @Test
    fun thePolicyCarriesAVersionAndDate() {
        assertTrue(en.contains("Policy version"))
        assertTrue(ru.contains("Версия политики"))
        assertTrue(en.contains("2026-11-15"))
        assertTrue(ru.contains("15.11.2026"))
    }

    @Test
    fun everyNetworkHostIsNamedInBothLanguages() {
        val hosts = listOf(
            "sylvan.apple.com",
            "api.open-meteo.com",
            "geocoding-api.open-meteo.com",
            "api.met.no",
            "wttr.in",
            "ipapi.co",
            "ipwho.is",
            "get.geojs.io",
            "nominatim.openstreetmap.org",
        )
        hosts.forEach { host ->
            assertTrue("EN policy omits $host", en.contains(host))
            assertTrue("RU policy omits $host", ru.contains(host))
        }
    }

    @Test
    fun thePolicyDescribesHttpsAndTheLocalCrashTrace() {
        assertTrue(en.contains("HTTPS"))
        assertTrue(ru.contains("HTTPS"))
        assertTrue(en.contains("crash trace"))
        assertTrue(ru.contains("след сбоя"))
        assertFalse(en.contains("does not request location"))
        assertFalse(ru.contains("не запрашивает местоположение"))
    }

    @Test
    fun weatherIsDisclosedAsOffUntilChosenAndHasADeletePath() {
        assertTrue(en.contains("Weather is off until you choose it"))
        assertTrue(ru.contains("Погода выключена, пока вы её не включите"))
        assertTrue(en.contains("Clear Weather Data"))
        assertTrue(ru.contains("Очистить данные погоды"))
    }
}
