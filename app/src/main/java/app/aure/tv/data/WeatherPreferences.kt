package app.aure.tv.data

import android.content.Context

/**
 * Stored weather choices and the last forecast. The engine that turns them
 * into a capsule lives in [Weather]; this object only owns the preferences.
 * The location is resolved from the internet address, so nothing here needs a
 * location permission — the same split the Java launcher used.
 */
object WeatherPreferences {
    const val NAME = "weather"
    const val K_ENABLED = "enabled"
    const val K_MANUAL = "manual"
    const val K_PLACE = "place"
    const val K_LAT = "lat"
    const val K_LON = "lon"
    const val K_UNITS = "units"
    const val K_SOURCE = "source"
    const val K_REFRESH = "refresh_min"
    const val K_CACHE = "cache2"
    const val K_CACHE_AT = "cache_at"
    const val K_CACHE_KEY = "cache_key"
    const val K_GENERATION = "data_generation"

    const val UNIT_AUTO = "auto"
    const val UNIT_C = "c"
    const val UNIT_F = "f"
    const val DEFAULT_REFRESH = 15

    internal fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // Off until the viewer chooses it: automatic location means a request to
    // third-party IP-geolocation services, so it must not be the silent default.
    fun enabled(context: Context): Boolean =
        SafePreferences.boolean(prefs(context), K_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_ENABLED, value).apply()
    }

    /** A city was picked by name; without it the place comes from the connection. */
    fun manual(context: Context): Boolean =
        SafePreferences.boolean(prefs(context), K_MANUAL, false)

    fun place(context: Context): String =
        SafePreferences.string(prefs(context), K_PLACE, "").orEmpty()

    fun latitude(context: Context): Double =
        Double.fromBits(SafePreferences.long(prefs(context), K_LAT, 0L))

    fun longitude(context: Context): Double =
        Double.fromBits(SafePreferences.long(prefs(context), K_LON, 0L))

    /** Choosing a city also turns the weather on: the row is a radio, not a toggle. */
    fun setPlace(context: Context, name: String, lat: Double, lon: Double) {
        prefs(context).edit()
            .putBoolean(K_ENABLED, true)
            .putBoolean(K_MANUAL, true)
            .putString(K_PLACE, name)
            .putLong(K_LAT, lat.toRawBits())
            .putLong(K_LON, lon.toRawBits())
            .remove(K_CACHE_AT)
            .remove(K_CACHE_KEY)
            .apply()
    }

    /** Back to the internet-address location; the stored city is dropped. */
    fun clearPlace(context: Context) {
        prefs(context).edit()
            .putBoolean(K_ENABLED, true)
            .putBoolean(K_MANUAL, false)
            .remove(K_PLACE)
            .remove(K_LAT)
            .remove(K_LON)
            .remove(K_CACHE_AT)
            .remove(K_CACHE_KEY)
            .apply()
    }

    /** Stored as "auto", "c" or "f"; "auto" resolves to Celsius in the engine. */
    fun units(context: Context): String {
        val value = SafePreferences.string(prefs(context), K_UNITS, UNIT_AUTO) ?: UNIT_AUTO
        return if (value == UNIT_C || value == UNIT_F) value else UNIT_AUTO
    }

    fun setUnits(context: Context, value: String) {
        prefs(context).edit().putString(K_UNITS, value).apply()
    }

    fun source(context: Context): String {
        val value = SafePreferences.string(
            prefs(context),
            K_SOURCE,
            Weather.SRC_OPEN_METEO,
        ) ?: Weather.SRC_OPEN_METEO
        return if (value in Weather.SOURCES) value else Weather.SRC_OPEN_METEO
    }

    fun setSource(context: Context, value: String) {
        prefs(context).edit().putString(K_SOURCE, value).apply()
    }

    fun refreshMinutes(context: Context): Int {
        val value = SafePreferences.int(prefs(context), K_REFRESH, DEFAULT_REFRESH)
        return if (value in Weather.REFRESH_CHOICES) value else DEFAULT_REFRESH
    }

    fun setRefresh(context: Context, minutes: Int) {
        prefs(context).edit().putInt(K_REFRESH, minutes).apply()
    }

    fun cachedAt(context: Context): Long =
        SafePreferences.long(prefs(context), K_CACHE_AT, 0L)

    fun cachedJson(context: Context): String? =
        SafePreferences.string(prefs(context), K_CACHE, null)

    fun cacheKey(context: Context): String =
        SafePreferences.string(prefs(context), K_CACHE_KEY, "").orEmpty()

    fun saveCache(context: Context, json: String, fetchedAt: Long, key: String, place: String) {
        prefs(context).edit()
            .putString(K_CACHE, json)
            .putString(K_PLACE, place)
            .putLong(K_CACHE_AT, fetchedAt)
            .putString(K_CACHE_KEY, key)
            .apply()
    }

    /** Settings changed: the next refresh fetches again. */
    fun changed(context: Context) {
        prefs(context).edit().remove(K_CACHE_AT).apply()
    }

    /** Increments whenever saved weather data is discarded. */
    fun generation(context: Context): Int = SafePreferences.int(prefs(context), K_GENERATION, 0)

    /**
     * Removes the saved place, coordinates, forecast and its timestamp, and
     * turns weather off. A fetch already in flight sees a new generation and
     * cannot write the old answer back. Units, source and refresh interval are
     * kept because they are preferences, not personal weather data.
     */
    fun clearData(context: Context) {
        val current = prefs(context)
        val next = SafePreferences.int(current, K_GENERATION, 0) + 1
        current.edit()
            .putBoolean(K_ENABLED, false)
            .putInt(K_GENERATION, next)
            .remove(K_MANUAL)
            .remove(K_PLACE)
            .remove(K_LAT)
            .remove(K_LON)
            .remove(K_CACHE)
            .remove(K_CACHE_AT)
            .remove(K_CACHE_KEY)
            .apply()
    }

    /** What the cache was fetched for; a mismatch means the settings changed. */
    fun key(context: Context): String = units(context) + "|" + source(context) +
        if (manual(context)) "|" + latitude(context) + "," + longitude(context) else "|auto"
}
