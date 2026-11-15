package com.whatuneed.ovisiontv.kotlin.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The weather engine without a socket: each provider's answer is normalised to
 * one shape, "Now" takes the current reading, Today starts the day list, and
 * codes and units follow the Java launcher.
 */
class WeatherTest {

    @Test
    fun openMeteoReadsCurrentHourlyAndDaily() {
        val body = """
            {"timezone":"UTC",
             "current":{"temperature_2m":21.4,"apparent_temperature":20.6,"weather_code":1,
                        "is_day":1,"relative_humidity_2m":55,"wind_speed_10m":12.3},
             "hourly":{"time":["2026-11-07T09:00","2026-11-07T10:00"],
                       "temperature_2m":[19.6,20.5],"weather_code":[2,1],"is_day":[1,1]},
             "daily":{"time":["2026-11-07","2026-11-08"],
                      "weather_code":[1,61],
                      "temperature_2m_max":[5.4,7.6],"temperature_2m_min":[1.2,3.5],
                      "precipitation_probability_max":[10,null]}}
        """.trimIndent()

        val data = Weather.parseOpenMeteo(body, fahrenheit = false)

        assertEquals(21, data.temp)
        assertEquals(21, data.feels)
        assertEquals(1, data.code)
        assertEquals(55, data.humidity)
        assertEquals(12, data.wind)
        assertEquals("km/h", data.windUnit)
        assertEquals(2, data.hours.size)
        assertEquals(20, data.hours[0].temp)
        assertEquals(2, data.days.size)
        assertEquals(5, data.days[0].max)
        assertEquals(1, data.days[0].min)
        assertEquals(10, data.days[0].rain)
        assertEquals(-1, data.days[1].rain)
    }

    @Test
    fun openMeteoAsksForFahrenheitAndMiles() {
        val body = """
            {"timezone":"UTC",
             "current":{"temperature_2m":68.0,"apparent_temperature":66.2,"weather_code":0,
                        "is_day":1,"relative_humidity_2m":40,"wind_speed_10m":7.0},
             "hourly":{"time":["2026-11-07T10:00"],"temperature_2m":[70.0],"weather_code":[0],"is_day":[1]},
             "daily":{"time":["2026-11-07"],"weather_code":[0],
                      "temperature_2m_max":[75.0],"temperature_2m_min":[50.0],
                      "precipitation_probability_max":[0]}}
        """.trimIndent()

        val data = Weather.parseOpenMeteo(body, fahrenheit = true)

        assertEquals("mph", data.windUnit)
        assertEquals(68, data.temp)
        assertEquals(7, data.wind)
    }

    @Test
    fun metNorwayReadsCurrentAndGroupsDays() {
        val body = """
            {"properties":{"timeseries":[
              {"time":"2026-11-07T12:00:00Z","data":{
                "instant":{"details":{"air_temperature":10.0,"wind_speed":2.0,"relative_humidity":60}},
                "next_1_hours":{"summary":{"symbol_code":"clearsky_day"}},
                "next_6_hours":{"summary":{"symbol_code":"partlycloudy_day"}}}},
              {"time":"2026-11-07T13:00:00Z","data":{
                "instant":{"details":{"air_temperature":13.0,"wind_speed":2.0,"relative_humidity":60}},
                "next_1_hours":{"summary":{"symbol_code":"rain"}}}},
              {"time":"2026-11-08T12:00:00Z","data":{
                "instant":{"details":{"air_temperature":6.0,"wind_speed":2.0,"relative_humidity":60}},
                "next_1_hours":{"summary":{"symbol_code":"snow"}}}}
            ]}}
        """.trimIndent()
        val now = utc("2026-11-07T11:30:00Z")

        val data = Weather.parseMet(body, fahrenheit = false, now = now)

        assertEquals(10, data.temp)
        assertEquals(0, data.code)
        assertTrue(data.day)
        assertEquals(2, data.hours.size)
        assertEquals(2, data.days.size)
        assertEquals(13, data.days[0].max)
        assertEquals(10, data.days[0].min)
    }

    @Test
    fun wttrReadsCurrentAndKeepsStepsAhead() {
        val tomorrow = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() + 86_400_000L))
        val body = """
            {"current_condition":[{"temp_C":4,"FeelsLikeC":1,"humidity":80,
                                   "windspeedKmph":11,"weatherCode":116}],
             "nearest_area":[{"areaName":[{"value":"Самара"}]}],
             "weather":[{"date":"$tomorrow","maxtempC":7,"mintempC":-2,
               "hourly":[{"time":900,"tempC":2,"weatherCode":113,"chanceofrain":0},
                         {"time":1200,"tempC":6,"weatherCode":116,"chanceofrain":20},
                         {"time":1500,"tempC":5,"weatherCode":119,"chanceofrain":10}]}]}
        """.trimIndent()

        val data = Weather.parseWttr(body, fahrenheit = false, now = System.currentTimeMillis())

        assertEquals(4, data.temp)
        assertEquals(1, data.feels)
        assertEquals(11, data.wind)
        assertEquals(2, data.code)
        assertEquals("Самара", data.place)
        assertEquals(1, data.days.size)
        assertEquals(7, data.days[0].max)
        assertEquals(20, data.days[0].rain)
        assertEquals(3, data.hours.size)
        assertEquals(2, data.hours[0].temp)
        assertTrue(data.hours[0].day)
    }

    @Test
    fun presentStartsAtNowAndToday() {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val start = Weather.localHourStart(now)
        val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = ymd.format(Date(now))
        val source = Weather.Data(temp = 21, code = 1, day = true, feels = 20).apply {
            for (i in -1..7) hours.add(Weather.Hour(start + i * hour, 10 + i, 3, true))
            for (i in 0..6) {
                days.add(
                    Weather.Day(
                        ymd.format(Date(now + i * 86_400_000L)),
                        3,
                        max = 10 + i,
                        min = 1 + i,
                    ),
                )
            }
        }

        val shown = Weather.present(source, now)

        assertEquals(6, shown.hours.size)
        // "Now" is the current reading, not the hour's own point.
        assertEquals(21, shown.hours[0].temp)
        assertEquals(1, shown.hours[0].code)
        assertEquals(start + hour, shown.hours[1].time)
        assertEquals(5, shown.days.size)
        assertEquals(today, shown.days[0].date)
        // The current 21° raises today's high above its forecast.
        assertEquals(21, shown.max)
        assertEquals(1, shown.min)
    }

    @Test
    fun codesUnitsAndNamesFollowTheJavaLauncher() {
        assertEquals(0, Weather.metCode("clearsky_day"))
        assertEquals(2, Weather.metCode("partlycloudy_night"))
        assertEquals(45, Weather.metCode("fog"))
        assertEquals(61, Weather.metCode("lightrain"))
        assertEquals(65, Weather.metCode("heavyrain"))
        assertEquals(80, Weather.metCode("lightrainshowers"))
        assertEquals(71, Weather.metCode("lightsnow"))
        assertEquals(75, Weather.metCode("heavysnow"))
        assertEquals(66, Weather.metCode("sleet"))
        assertEquals(95, Weather.metCode("thunderstorm"))
        assertEquals(3, Weather.metCode(null))

        assertEquals(0, Weather.wwoCode(113))
        assertEquals(45, Weather.wwoCode(248))
        assertEquals(61, Weather.wwoCode(296))
        assertEquals(71, Weather.wwoCode(326))
        assertEquals(95, Weather.wwoCode(389))
        assertEquals(3, Weather.wwoCode(42))

        assertEquals(0, Weather.temp(0.0, fahrenheit = false))
        assertEquals(32, Weather.temp(0.0, fahrenheit = true))
        assertEquals(68, Weather.temp(20.0, fahrenheit = true))

        assertTrue(Weather.foreignName("Samara", english = false))
        assertFalse(Weather.foreignName("Самара", english = false))
        assertTrue(Weather.foreignName("Самара", english = true))
        assertFalse(Weather.foreignName("Samara", english = true))
    }

    @Test
    fun apparentTemperatureUsesTheBomFormula() {
        // Calm, saturated air is warmer than the thermometer; dry air is cooler.
        assertTrue(Weather.apparent(20.0, 100.0, 0.0) > 20.0)
        assertTrue(Weather.apparent(20.0, 20.0, 5.0) < 20.0)
        assertEquals(-1.98, Weather.apparent(10.0, 50.0, 0.0) - 10.0, 0.02)
    }

    @Test
    fun cachedShapeSurvivesJson() {
        val data = Weather.Data(
            place = "Самара",
            source = Weather.SRC_MET,
            windUnit = "mph",
            fetchedAt = 1_760_000_000_000L,
            temp = 21,
            feels = 20,
            code = 1,
            humidity = 55,
            wind = 8,
            day = false,
            max = 22,
            min = 9,
        ).apply {
            hours.add(Weather.Hour(1_760_000_000_000L, 20, 2, true))
            days.add(Weather.Day("2026-11-07", 3, 22, 9, 40))
        }

        val restored = Weather.Data.fromJson(JSONObject(data.toJson().toString()))

        assertEquals("Самара", restored.place)
        assertEquals(Weather.SRC_MET, restored.source)
        assertEquals(1_760_000_000_000L, restored.fetchedAt)
        assertEquals(21, restored.temp)
        assertFalse(restored.day)
        assertEquals(1, restored.hours.size)
        assertEquals(2, restored.hours[0].code)
        assertEquals(40, restored.days[0].rain)
    }

    @Test
    fun updateDelayFollowsTheRefreshSchedule() {
        val now = 1_760_000_000_000L

        // Fresh cache, refresh every 15 minutes: the next fetch is 15 minutes out.
        assertEquals(
            15 * 60_000L,
            Weather.updateDelay(
                now = now,
                cachedAt = now,
                refreshMinutes = 15,
                stale = false,
                nextHourStart = now + 40 * 60_000L,
            ),
        )
    }

    @Test
    fun updateDelayWakesAtTheHourBoundary() {
        val now = 1_760_000_000_000L

        // The hour list and the day list roll on the hour, so a close boundary
        // wins over a longer refresh interval.
        assertEquals(
            4 * 60_000L,
            Weather.updateDelay(
                now = now,
                cachedAt = now,
                refreshMinutes = 15,
                stale = false,
                nextHourStart = now + 4 * 60_000L,
            ),
        )
    }

    @Test
    fun updateDelayRetriesAStaleCacheAfterAMinute() {
        val now = 1_760_000_000_000L

        // A stale cache means the fetch just failed: one minute later, not a spin.
        assertEquals(
            60_000L,
            Weather.updateDelay(
                now = now,
                cachedAt = now,
                refreshMinutes = 15,
                stale = true,
                nextHourStart = now + 40 * 60_000L,
            ),
        )
        // An over-age cache that still reports fresh also cannot go below it.
        assertEquals(
            60_000L,
            Weather.updateDelay(
                now = now,
                cachedAt = now - 60 * 60_000L,
                refreshMinutes = 15,
                stale = false,
                nextHourStart = now + 40 * 60_000L,
            ),
        )
    }

    @Test
    fun updateDelayBacksOffAfterRepeatedFailures() {
        val now = 1_760_000_000_000L

        assertEquals(60_000L, Weather.retryDelay(0))
        assertEquals(120_000L, Weather.retryDelay(1))
        assertEquals(240_000L, Weather.retryDelay(2))
        assertEquals(480_000L, Weather.retryDelay(3))
        assertEquals(900_000L, Weather.retryDelay(4))
        assertEquals(900_000L, Weather.retryDelay(20))
        assertEquals(
            240_000L,
            Weather.updateDelay(
                now = now,
                cachedAt = now,
                refreshMinutes = 15,
                stale = true,
                nextHourStart = now + 40 * 60_000L,
                failures = 2,
            ),
        )
    }

    private fun utc(value: String): Long =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(value)!!.time
}
