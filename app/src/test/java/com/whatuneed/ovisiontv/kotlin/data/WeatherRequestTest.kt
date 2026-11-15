package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRequestTest {

    @Test
    fun a_forecast_commits_only_for_the_settings_it_was_requested_with() {
        val request = WeatherRequest("c|openmeteo|auto", "ru")
        assertTrue(weatherRequestIsCurrent(request, "c|openmeteo|auto", "ru"))
    }

    @Test
    fun a_city_or_unit_change_during_the_fetch_drops_the_result() {
        val request = WeatherRequest("c|openmeteo|auto", "ru")
        assertFalse(weatherRequestIsCurrent(request, "f|openmeteo|auto", "ru"))
        assertFalse(weatherRequestIsCurrent(request, "c|met|auto", "ru"))
        assertFalse(weatherRequestIsCurrent(request, "c|openmeteo|53.2,50.1", "ru"))
    }

    @Test
    fun a_language_change_during_the_fetch_drops_the_result() {
        val request = WeatherRequest("c|openmeteo|auto", "ru")
        assertFalse(weatherRequestIsCurrent(request, "c|openmeteo|auto", "en"))
    }

    @Test
    fun clearing_saved_weather_during_the_fetch_drops_the_result() {
        val request = WeatherRequest("c|openmeteo|auto", "ru", generation = 4)
        assertTrue(weatherRequestIsCurrent(request, "c|openmeteo|auto", "ru", 4))
        assertFalse(weatherRequestIsCurrent(request, "c|openmeteo|auto", "ru", 5))
    }
}
