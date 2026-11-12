package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/** The settings a forecast was requested for; a commit must match them. */
internal data class WeatherRequest(val key: String, val language: String)

internal fun weatherRequestIsCurrent(
    snapshot: WeatherRequest,
    currentKey: String,
    currentLanguage: String,
): Boolean = snapshot.key == currentKey && snapshot.language == currentLanguage

/**
 * Current weather and a 5-day forecast from free services that need no key:
 * Open-Meteo, MET Norway (Yr) and wttr.in, in that order of preference but
 * always trying the chosen one first. The location comes from the internet
 * address unless a city is chosen; nothing here asks for a location
 * permission. The result is cached in [WeatherPreferences] and refreshed on a
 * schedule (15 minutes by default).
 */
object Weather {
    private const val TAG = "Weather"
    // A failed fetch leaves the cache due; the retry must not spin.
    private const val RETRY_MS = 60_000L
    const val SRC_OPEN_METEO = "openmeteo"
    const val SRC_MET = "met"
    const val SRC_WTTR = "wttr"
    val SOURCES = listOf(SRC_OPEN_METEO, SRC_MET, SRC_WTTR)
    val REFRESH_CHOICES = listOf(10, 15, 30, 60)
    private const val UA = "OVisionTVLauncher/6.2 (Android TV launcher)"
    private const val CONNECT_MS = 8_000
    private const val READ_MS = 10_000
    private const val MAX_BYTES = 2 * 1024 * 1024

    private val busy = AtomicBoolean(false)
    private val pending = AtomicBoolean(false)

    // ---------------------------------------------------------------- data

    data class Hour(val time: Long, val temp: Int, val code: Int, val day: Boolean)

    data class Day(var date: String, var code: Int, var max: Int, var min: Int, var rain: Int = -1)

    /** One forecast in the shape every source is normalised to. */
    class Data(
        var place: String = "",
        var source: String = SRC_OPEN_METEO,
        var windUnit: String = "km/h",
        var fetchedAt: Long = 0,
        var temp: Int = 0,
        var feels: Int = 0,
        var code: Int = 0,
        var humidity: Int = 0,
        var wind: Int = 0,
        var day: Boolean = true,
        var max: Int = 0,
        var min: Int = 0,
        val hours: MutableList<Hour> = ArrayList(),
        val days: MutableList<Day> = ArrayList(),
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("place", place)
            .put("source", source)
            .put("windUnit", windUnit)
            .put("fetchedAt", fetchedAt)
            .put("temp", temp)
            .put("feels", feels)
            .put("code", code)
            .put("humidity", humidity)
            .put("wind", wind)
            .put("day", day)
            .put("hours", JSONArray().apply {
                for (x in hours) put(JSONObject().put("t", x.time).put("temp", x.temp).put("code", x.code).put("day", x.day))
            })
            .put("days", JSONArray().apply {
                for (x in days) {
                    put(
                        JSONObject().put("date", x.date).put("code", x.code).put("max", x.max)
                            .put("min", x.min).put("rain", x.rain),
                    )
                }
            })

        fun deepCopy(): Data {
            val copy = Data(
                place, source, windUnit, fetchedAt, temp, feels, code, humidity, wind, day, max, min,
            )
            copy.hours.addAll(hours)
            copy.days.addAll(days)
            return copy
        }

        companion object {
            fun fromJson(j: JSONObject): Data {
                val d = Data(
                    place = j.optString("place"),
                    source = j.optString("source", SRC_OPEN_METEO),
                    windUnit = j.optString("windUnit", "km/h"),
                    fetchedAt = j.optLong("fetchedAt"),
                    temp = j.getInt("temp"),
                    feels = j.optInt("feels", j.getInt("temp")),
                    code = j.getInt("code"),
                    humidity = j.optInt("humidity"),
                    wind = j.optInt("wind"),
                    day = j.optBoolean("day", true),
                )
                val h = j.getJSONArray("hours")
                for (i in 0 until h.length()) {
                    val o = h.getJSONObject(i)
                    d.hours.add(
                        Hour(
                            time = o.getLong("t"),
                            temp = o.getInt("temp"),
                            code = o.getInt("code"),
                            day = o.optBoolean("day", true),
                        ),
                    )
                }
                val a = j.getJSONArray("days")
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    d.days.add(
                        Day(
                            date = o.getString("date"),
                            code = o.getInt("code"),
                            max = o.getInt("max"),
                            min = o.getInt("min"),
                            rain = o.optInt("rain", -1),
                        ),
                    )
                }
                return d
            }
        }
    }

    // ---------------------------------------------------------------- settings

    /** "c" or "f"; automatic is Celsius because the launcher speaks Russian first. */
    fun units(context: Context): String =
        if (WeatherPreferences.units(context) == WeatherPreferences.UNIT_F) "f" else "c"

    fun sourceName(source: String): String = when (source) {
        SRC_MET -> "MET Norway"
        SRC_WTTR -> "wttr.in"
        else -> "Open-Meteo"
    }

    fun windUnitName(unit: String, russian: Boolean): String =
        if (unit.contains("mp")) "mph" else if (russian) "км/ч" else "km/h"

    /** A stored name in the other language's script ("Samara" in Russian). */
    fun foreignName(name: String, english: Boolean): Boolean {
        if (name.isEmpty()) return false
        return if (english) name.any { it in '\u0400'..'\u04FF' } else name.any { it in 'A'..'Z' || it in 'a'..'z' }
    }

    /** The cache is due: too old, settings changed, or the city is in the wrong script. */
    fun stale(context: Context): Boolean {
        val place = WeatherPreferences.place(context)
        val english = language(context) == "en"
        val age = System.currentTimeMillis() - WeatherPreferences.cachedAt(context)
        return foreignName(place, english) ||
            age > WeatherPreferences.refreshMinutes(context) * 60_000L ||
            WeatherPreferences.key(context) != WeatherPreferences.cacheKey(context)
    }

    /**
     * Milliseconds until the shown forecast can change: the refresh schedule,
     * the next local hour (the first hour and the day list roll there) or a
     * one-minute retry after a failed fetch. The Home timer sleeps that long
     * instead of waking every minute.
     */
    fun refreshDelay(context: Context): Long {
        val nextHourStart = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return updateDelay(
            now = System.currentTimeMillis(),
            cachedAt = WeatherPreferences.cachedAt(context),
            refreshMinutes = WeatherPreferences.refreshMinutes(context),
            stale = stale(context),
            nextHourStart = nextHourStart,
        )
    }

    internal fun updateDelay(
        now: Long,
        cachedAt: Long,
        refreshMinutes: Int,
        stale: Boolean,
        nextHourStart: Long,
    ): Long {
        if (stale) return RETRY_MS
        val due = refreshMinutes * 60_000L - (now - cachedAt)
        val nextHour = nextHourStart - now
        return minOf(due.coerceAtLeast(RETRY_MS), nextHour.coerceAtLeast(1_000L))
    }

    /**
     * The cached forecast ready to show: hours from the current hour and days
     * from today. Null until something has loaded. A place saved in the other
     * script is hidden until the background re-ask lands.
     */
    fun cached(context: Context): Data? {
        val raw = WeatherPreferences.cachedJson(context) ?: return null
        return runCatching {
            val data = present(Data.fromJson(JSONObject(raw)))
            if (foreignName(data.place, language(context) == "en")) data.place = ""
            data
        }.onFailure { Log.w(TAG, "cached forecast parse failed", it) }.getOrNull()
    }

    /** Fetches in the background when the cache is due; [force] ignores the schedule. */
    suspend fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!WeatherPreferences.enabled(app)) return
        if (!force && !stale(app)) return
        if (!busy.compareAndSet(false, true)) {
            // A forced refresh must not be lost just because another fetch is
            // in flight; it runs once that one finishes (AQ-09).
            if (force) pending.set(true)
            return
        }
        try {
            do {
                pending.set(false)
                withContext(Dispatchers.IO) { runFetch(app) }
            } while (pending.get() && WeatherPreferences.enabled(app))
        } finally {
            busy.set(false)
        }
        if (pending.get() && WeatherPreferences.enabled(app)) refresh(app, force = true)
    }

    private fun runFetch(context: Context) {
        try {
            val manual = WeatherPreferences.manual(context)
            val language = language(context)
            // The request is a snapshot: a city/unit/language change while the
            // network is busy must not be committed under the new key (AQ-09).
            val request = WeatherRequest(WeatherPreferences.key(context), language)
            val lat: Double
            val lon: Double
            var place: String
            if (manual) {
                lat = WeatherPreferences.latitude(context)
                lon = WeatherPreferences.longitude(context)
                place = WeatherPreferences.place(context)
                // A city chosen before a language switch: ask for its name again.
                if (foreignName(place, language == "en")) place = localize(lat, lon, place, language)
            } else {
                val located = locate(language)
                place = located.place
                lat = located.lat
                lon = located.lon
            }
            val fahrenheit = units(context) == "f"
            val order = ArrayList<String>()
            order.add(WeatherPreferences.source(context))
            for (s in SOURCES) if (s !in order) order.add(s)
            var data: Data? = null
            for (s in order) {
                data = runCatching {
                    when (s) {
                        SRC_MET -> fetchMet(lat, lon, fahrenheit)
                        SRC_WTTR -> fetchWttr(lat, lon, fahrenheit)
                        else -> fetchOpenMeteo(lat, lon, fahrenheit)
                    }
                }.onFailure { Log.w(TAG, "source $s failed", it) }.getOrNull()
                if (data != null) {
                    data.source = s
                    break
                }
            }
            if (data != null) {
                if (!weatherRequestIsCurrent(
                        request,
                        WeatherPreferences.key(context),
                        language(context),
                    )
                ) {
                    Log.d(TAG, "forecast dropped: settings changed during fetch")
                    return
                }
                if (place.isNotEmpty() || data.place.isEmpty()) data.place = place
                val fetchedAt = System.currentTimeMillis()
                data.fetchedAt = fetchedAt
                WeatherPreferences.saveCache(
                    context,
                    data.toJson().toString(),
                    fetchedAt,
                    request.key,
                    data.place,
                )
            }
        } catch (error: Exception) {
            // Keep showing the last forecast.
            Log.w(TAG, "forecast refresh failed", error)
        }
    }

    // ---------------------------------------------------------------- sources

    private fun fetchOpenMeteo(lat: Double, lon: Double, fahrenheit: Boolean): Data =
        parseOpenMeteo(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,weather_code,is_day,relative_humidity_2m,wind_speed_10m" +
                    "&hourly=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=6&forecast_hours=24" +
                    if (fahrenheit) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "",
            ),
            fahrenheit,
        )

    /** Open-Meteo: hourly and daily forecast in the place's own time zone. */
    internal fun parseOpenMeteo(body: String, fahrenheit: Boolean): Data {
        val j = JSONObject(body)
        val d = Data(windUnit = if (fahrenheit) "mph" else "km/h")
        val current = j.getJSONObject("current")
        d.temp = round(current.getDouble("temperature_2m"))
        d.feels = round(current.getDouble("apparent_temperature"))
        d.code = current.getInt("weather_code")
        d.day = current.optInt("is_day", 1) == 1
        d.humidity = current.optInt("relative_humidity_2m")
        d.wind = round(current.optDouble("wind_speed_10m", 0.0))
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        iso.timeZone = TimeZone.getTimeZone(j.optString("timezone", "UTC"))
        val h = j.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code")
        val daylight = h.getJSONArray("is_day")
        for (i in 0 until times.length()) {
            d.hours.add(
                Hour(
                    time = iso.parse(times.getString(i))?.time ?: continue,
                    temp = round(temps.getDouble(i)),
                    code = codes.getInt(i),
                    day = daylight.getInt(i) == 1,
                ),
            )
        }
        val daily = j.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val max = daily.getJSONArray("temperature_2m_max")
        val min = daily.getJSONArray("temperature_2m_min")
        val dailyCodes = daily.getJSONArray("weather_code")
        for (i in 0 until dates.length()) {
            d.days.add(
                Day(
                    date = dates.getString(i),
                    code = dailyCodes.getInt(i),
                    max = round(max.getDouble(i)),
                    min = round(min.getDouble(i)),
                    rain = if (rain == null || rain.isNull(i)) -1 else rain.getInt(i),
                ),
            )
        }
        return d
    }

    private fun fetchMet(lat: Double, lon: Double, fahrenheit: Boolean): Data = parseMet(
        get(
            String.format(
                Locale.US,
                "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
                lat,
                lon,
            ),
        ),
        fahrenheit,
        System.currentTimeMillis(),
    )

    /** MET Norway (Yr) Locationforecast: UTC series in °C and m/s; days grouped locally. */
    internal fun parseMet(body: String, fahrenheit: Boolean, now: Long): Data {
        val j = JSONObject(body)
        val series = j.getJSONObject("properties").getJSONArray("timeseries")
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        iso.timeZone = TimeZone.getTimeZone("UTC")
        val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val d = Data(windUnit = if (fahrenheit) "mph" else "km/h")
        val days = LinkedHashMap<String, Day>()
        val noonGap = HashMap<String, Int>()
        var first = true
        for (i in 0 until series.length()) {
            val e = series.getJSONObject(i)
            val t = iso.parse(e.getString("time"))?.time ?: continue
            val data = e.getJSONObject("data")
            val inst = data.getJSONObject("instant").getJSONObject("details")
            val celsius = inst.getDouble("air_temperature")
            var symbol = symbol(data, "next_1_hours") ?: symbol(data, "next_6_hours")
            if (first && t <= now + 3_600_000L) {
                val wind = inst.optDouble("wind_speed", 0.0)
                val humidity = inst.optDouble("relative_humidity", 50.0)
                d.temp = temp(celsius, fahrenheit)
                d.feels = temp(apparent(celsius, humidity, wind), fahrenheit)
                d.humidity = Math.round(humidity).toInt()
                d.wind = Math.round(if (fahrenheit) wind * 2.237 else wind * 3.6).toInt()
                d.code = metCode(symbol)
                d.day = symbol == null || !symbol.contains("night")
                first = false
            }
            if (t >= now - 3_600_000L && t <= now + 24 * 3_600_000L && data.has("next_1_hours")) {
                d.hours.add(
                    Hour(
                        time = t,
                        temp = temp(celsius, fahrenheit),
                        code = metCode(symbol),
                        day = symbol == null || !symbol.contains("night"),
                    ),
                )
            }
            val date = ymd.format(Date(t))
            val day = days.getOrPut(date) { Day(date = date, code = 3, max = Int.MIN_VALUE, min = Int.MAX_VALUE) }
            day.max = maxOf(day.max, temp(celsius, fahrenheit))
            day.min = minOf(day.min, temp(celsius, fahrenheit))
            // The day's weather: the symbol nearest midday.
            val calendar = Calendar.getInstance().apply { timeInMillis = t }
            val gap = Math.abs(calendar.get(Calendar.HOUR_OF_DAY) - 12)
            val daySymbol = symbol(data, "next_6_hours") ?: symbol
            val best = noonGap[date]
            if (daySymbol != null && (best == null || gap < best)) {
                noonGap[date] = gap
                day.code = metCode(daySymbol)
            }
        }
        if (first) error("No current data")
        d.days.addAll(days.values)
        return d
    }

    private fun fetchWttr(lat: Double, lon: Double, fahrenheit: Boolean): Data = parseWttr(
        get(String.format(Locale.US, "https://wttr.in/%.4f,%.4f?format=j1", lat, lon)),
        fahrenheit,
        System.currentTimeMillis(),
    )

    /** wttr.in JSON: current conditions and 3 days in 3-hour steps, local time. */
    internal fun parseWttr(body: String, fahrenheit: Boolean, now: Long): Data {
        val j = JSONObject(body)
        val current = j.getJSONArray("current_condition").getJSONObject(0)
        val d = Data(windUnit = if (fahrenheit) "mph" else "km/h")
        d.temp = current.getInt(if (fahrenheit) "temp_F" else "temp_C")
        d.feels = current.optInt(if (fahrenheit) "FeelsLikeF" else "FeelsLikeC", d.temp)
        d.humidity = current.optInt("humidity")
        d.wind = current.optInt(if (fahrenheit) "windspeedMiles" else "windspeedKmph")
        d.code = wwoCode(current.optInt("weatherCode", 116))
        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        d.day = hourNow in 6..17
        runCatching {
            d.place = j.getJSONArray("nearest_area").getJSONObject(0)
                .getJSONArray("areaName").getJSONObject(0).getString("value")
        }
        val local = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US)
        val weather = j.getJSONArray("weather")
        for (i in 0 until weather.length()) {
            val o = weather.getJSONObject(i)
            val date = o.getString("date")
            val day = Day(
                date = date,
                code = 3,
                max = o.getInt(if (fahrenheit) "maxtempF" else "maxtempC"),
                min = o.getInt(if (fahrenheit) "mintempF" else "mintempC"),
                rain = 0,
            )
            val hourly = o.getJSONArray("hourly")
            for (k in 0 until hourly.length()) {
                val h = hourly.getJSONObject(k)
                val hhmm = h.getInt("time")
                val code = wwoCode(h.optInt("weatherCode", 116))
                if (hhmm == 1200) day.code = code
                day.rain = maxOf(day.rain, h.optInt("chanceofrain", 0))
                d.hours.add(
                    Hour(
                        time = local.parse("$date " + String.format(Locale.US, "%04d", hhmm))?.time ?: continue,
                        temp = h.getInt(if (fahrenheit) "tempF" else "tempC"),
                        code = code,
                        day = hhmm in 600..1799,
                    ),
                )
            }
            d.days.add(day)
        }
        // 3-hour steps: keep the step containing now as the first hour.
        val keep = d.hours.filter { it.time + 3 * 3_600_000L > now }.toMutableList()
        if (keep.isNotEmpty()) {
            keep[0] = keep[0].copy(time = maxOf(keep[0].time, localHourStart()))
        }
        d.hours.clear()
        d.hours.addAll(keep)
        return d
    }

    private fun symbol(data: JSONObject, period: String): String? {
        val p = data.optJSONObject(period) ?: return null
        val summary = p.optJSONObject("summary") ?: return null
        return summary.optString("symbol_code").takeIf { it.isNotEmpty() }
    }

    /** MET Norway symbol to a WMO weather code. */
    internal fun metCode(symbol: String?): Int {
        if (symbol == null) return 3
        val s = symbol.replace("_day", "").replace("_night", "").replace("_polartwilight", "")
        if (s.contains("thunder")) return 95
        if (s.contains("snow")) return if (s.startsWith("heavy")) 75 else if (s.startsWith("light")) 71 else 73
        if (s.contains("sleet")) return 66
        if (s.contains("rain")) {
            if (s.contains("showers")) return if (s.startsWith("heavy")) 82 else 80
            return if (s.startsWith("heavy")) 65 else if (s.startsWith("light")) 61 else 63
        }
        return when (s) {
            "clearsky" -> 0
            "fair" -> 1
            "partlycloudy" -> 2
            "fog" -> 45
            else -> 3
        }
    }

    /** World Weather Online code (used by wttr.in) to a WMO weather code. */
    internal fun wwoCode(code: Int): Int = when (code) {
        113 -> 0
        116 -> 2
        143, 248, 260 -> 45
        176, 263, 266, 293, 296, 353 -> 61
        299, 302, 356 -> 63
        305, 308, 359 -> 65
        179, 227, 323, 326, 368 -> 71
        329, 332, 335, 371 -> 73
        230, 338, 392, 395 -> 75
        182, 185, 281, 284, 311, 314, 317, 320, 350, 362, 365, 374, 377 -> 66
        200, 386, 389 -> 95
        else -> 3
    }

    /** Apparent temperature (Australian BoM formula), °C from °C, % humidity and m/s wind. */
    internal fun apparent(celsius: Double, humidity: Double, wind: Double): Double {
        val e = humidity / 100 * 6.105 * Math.exp(17.27 * celsius / (237.7 + celsius))
        return celsius + 0.33 * e - 0.70 * wind - 4.00
    }

    internal fun temp(celsius: Double, fahrenheit: Boolean): Int =
        round(if (fahrenheit) celsius * 9 / 5 + 32 else celsius)

    private fun round(value: Double): Int = Math.round(value).toInt()

    /** Start of the current hour in local time (half-hour time zones included). */
    internal fun localHourStart(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * Starts Today, keeps at most six hours and five days, and combines today's
     * forecast with the current reading for the high and low.
     */
    internal fun present(source: Data, now: Long = System.currentTimeMillis()): Data {
        val d = source.deepCopy()
        d.hours.clear()
        d.days.clear()
        val hourStart = localHourStart(now)
        for (h in source.hours) {
            if (h.time < hourStart || d.hours.size >= 6) continue
            d.hours.add(
                if (d.hours.isEmpty()) h.copy(temp = d.temp, code = d.code, day = d.day) else h,
            )
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (x in source.days) {
            if (x.date < today || d.days.size >= 5) continue
            d.days.add(x)
        }
        if (d.days.isNotEmpty()) {
            d.max = maxOf(d.days[0].max, d.temp)
            d.min = minOf(d.days[0].min, d.temp)
        } else {
            d.max = d.temp
            d.min = d.temp
        }
        return d
    }

    // ---------------------------------------------------------------- location

    private data class Located(val place: String, val lat: Double, val lon: Double)

    /** Approximate location from the internet address, with the city in the UI language. */
    private fun locate(language: String): Located {
        val services = listOf(
            "https://ipapi.co/json/",
            "https://ipwho.is/",
            "https://get.geojs.io/v1/ip/geo.json",
        )
        var last: Exception? = null
        for (url in services) {
            try {
                val j = JSONObject(get(url))
                if (!j.has("latitude") || j.optBoolean("error") || !j.optBoolean("success", true)) continue
                val lat = j.getString("latitude").toDouble()
                val lon = j.getString("longitude").toDouble()
                return Located(localize(lat, lon, j.optString("city", ""), language), lat, lon)
            } catch (error: Exception) {
                last = error
            }
        }
        throw last ?: IllegalStateException("No location")
    }

    /**
     * The IP services answer with Latin city names whatever the language; the
     * capsule and Settings show the name in the current language.
     */
    private fun localize(lat: Double, lon: Double, fallback: String, language: String): String {
        if (!foreignName(fallback, language == "en")) return fallback
        // Open-Meteo geocoding knows the name; take the result nearest the coordinates.
        runCatching {
            var best: CityPlace? = null
            var bestDistance = Double.MAX_VALUE
            for (place in CitySearch.search(fallback, language).orEmpty()) {
                val distance = Math.hypot(place.lat - lat, (place.lon - lon) * Math.cos(Math.toRadians(lat)))
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = place
                }
            }
            if (best != null && bestDistance < 2) return best.name
        }
        runCatching {
            val connection = URL(
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&accept-language=$language" +
                    "&lat=$lat&lon=$lon",
            ).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("User-Agent", UA)
            val body = try {
                connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } finally {
                connection.disconnect()
            }
            val j = JSONObject(body)
            val address = j.optJSONObject("address")
            if (address != null) {
                for (key in listOf("city", "town", "village", "municipality")) {
                    val value = address.optString(key, "")
                    if (value.isNotEmpty()) return value
                }
            }
            val name = j.optString("name", "")
            if (name.isNotEmpty()) return name
        }
        return fallback
    }

    /** The interface language as a two-letter tag, for geocoding and descriptions. */
    internal fun language(context: Context): String = when (LocalePreference.get(context)) {
        LocalePreference.RUSSIAN -> "ru"
        LocalePreference.ENGLISH -> "en"
        else -> context.resources.configuration.locales[0]?.language ?: "ru"
    }

    // ---------------------------------------------------------------- http

    internal fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_MS
        connection.readTimeout = READ_MS
        connection.setRequestProperty("User-Agent", UA)
        connection.setRequestProperty("Accept", "application/json")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${connection.responseCode}")
            }
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input: InputStream ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) error("Response too large")
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }
}
