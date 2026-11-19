package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The geocoder's parsing without a socket: the dedup that keeps one row per
 * city, the Russian type-word rule the Java launcher applied, and the guards
 * against rows the API fills only partially.
 */
class CitySearchTest {

    @Test
    fun parseReadsNameDetailAndCoordinates() {
        val body = """
            {"results":[
              {"name":"Москва","admin1":"Москва","country":"Россия","latitude":55.7558,"longitude":37.6173},
              {"name":"Сочи","admin1":"Краснодарский край","country":"Россия","latitude":43.5855,"longitude":39.7231}
            ]}
        """.trimIndent()

        val places = CitySearch.parse(body)

        assertEquals(2, places.size)
        assertEquals("Москва", places[0].name)
        assertEquals("Москва, Россия", places[0].detail)
        assertEquals(55.7558, places[0].lat, 1e-9)
        assertEquals(37.6173, places[0].lon, 1e-9)
        // The Java launcher put the region first and never repeated the city name.
        assertEquals("Краснодарский край, Россия", places[1].detail)
    }

    @Test
    fun parseDedupsRowsThatShareNameAndRegion() {
        val body = """
            {"results":[
              {"name":"Москва","admin1":"Москва","country":"Россия","latitude":55.75,"longitude":37.61},
              {"name":"Москва","admin1":"Москва","country":"Россия","latitude":55.80,"longitude":37.60},
              {"name":"Москва","admin1":"Idaho","country":"United States","latitude":46.89,"longitude":-116.09}
            ]}
        """.trimIndent()

        val places = CitySearch.parse(body)

        assertEquals(2, places.size)
        assertEquals("Россия", places[0].detail.substringAfterLast(", "))
        assertEquals("United States", places[1].detail.substringAfterLast(", "))
    }

    @Test
    fun parseSkipsRowsWithoutCoordinates() {
        val body = """
            {"results":[
              {"name":"Москва","admin1":"Москва","country":"Россия"},
              {"name":"Сочи","admin1":"Краснодарский край","country":"Россия","latitude":43.5855,"longitude":39.7231}
            ]}
        """.trimIndent()

        val places = CitySearch.parse(body)

        assertEquals(1, places.size)
        assertEquals("Сочи", places[0].name)
    }

    @Test
    fun parseReturnsEmptyWithoutResults() {
        assertTrue(CitySearch.parse("{}").isEmpty())
        assertTrue(CitySearch.parse("""{"results":[]}""").isEmpty())
    }

    @Test
    fun redirectTargetMustStayOnTheGeocoderHttpsHost() {
        assertTrue(citySearchUrlIsTrusted("https://geocoding-api.open-meteo.com/v1/search?name=Sochi"))
        assertTrue(citySearchUrlIsTrusted("https://geocoding-api.open-meteo.com:443/v1/search"))

        assertFalse(citySearchUrlIsTrusted("http://geocoding-api.open-meteo.com/v1/search"))
        assertFalse(citySearchUrlIsTrusted("https://geocoding-api.open-meteo.com.evil.test/v1/search"))
        assertFalse(citySearchUrlIsTrusted("https://evil.test/v1/search"))
        assertFalse(citySearchUrlIsTrusted("https://geocoding-api.open-meteo.com:444/v1/search"))
        assertFalse(citySearchUrlIsTrusted("not a url"))
    }

    @Test
    fun regionLowersTheTypeWordOnly() {
        assertEquals("Самарская область", CitySearch.region("Самарская Область"))
        assertEquals("Ленинградская область", CitySearch.region("Ленинградская область"))
        assertEquals("Краснодарский край", CitySearch.region("Краснодарский Край"))
        assertEquals(
            "Ханты-Мансийский автономный округ",
            CitySearch.region("Ханты-Мансийский Автономный Округ"),
        )
        // Proper names stay as the geocoder wrote them.
        assertEquals("Иркутская область", CitySearch.region("Иркутская область"))
        assertEquals("Bayern", CitySearch.region("Bayern"))
    }
}
