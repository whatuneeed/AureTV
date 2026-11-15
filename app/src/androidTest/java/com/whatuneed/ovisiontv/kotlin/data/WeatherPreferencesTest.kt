package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.ui.PrefsSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherPreferencesTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences(WeatherPreferences.NAME, Context.MODE_PRIVATE)
    private lateinit var saved: Map<String, Any?>

    @Before
    fun clearWeatherPrefs() {
        saved = PrefsSnapshot.captureWeather(context)
        prefs.edit().clear().commit()
    }

    @After
    fun restoreWeatherPrefs() {
        PrefsSnapshot.restoreWeather(context, saved)
    }

    @Test
    fun clearingWeatherDataRemovesPlaceForecastAndKeepsDisplayPreferences() {
        prefs.edit()
            .putBoolean(WeatherPreferences.K_ENABLED, true)
            .putBoolean(WeatherPreferences.K_MANUAL, true)
            .putString(WeatherPreferences.K_PLACE, "Moscow")
            .putLong(WeatherPreferences.K_LAT, 55.75.toRawBits())
            .putLong(WeatherPreferences.K_LON, 37.62.toRawBits())
            .putString(WeatherPreferences.K_CACHE, """{"temp":1}""")
            .putLong(WeatherPreferences.K_CACHE_AT, 123L)
            .putString(WeatherPreferences.K_CACHE_KEY, "f|met|55.75,37.62")
            .putInt(WeatherPreferences.K_GENERATION, 7)
            .putString(WeatherPreferences.K_UNITS, WeatherPreferences.UNIT_F)
            .putString(WeatherPreferences.K_SOURCE, Weather.SRC_MET)
            .putInt(WeatherPreferences.K_REFRESH, 30)
            .commit()

        WeatherPreferences.clearData(context)

        assertFalse(WeatherPreferences.enabled(context))
        assertFalse(WeatherPreferences.manual(context))
        assertEquals("", WeatherPreferences.place(context))
        assertEquals(0.0, WeatherPreferences.latitude(context), 0.0)
        assertEquals(0.0, WeatherPreferences.longitude(context), 0.0)
        assertNull(WeatherPreferences.cachedJson(context))
        assertEquals(0L, WeatherPreferences.cachedAt(context))
        assertEquals("", WeatherPreferences.cacheKey(context))
        assertEquals(8, WeatherPreferences.generation(context))
        // These are display choices, not the saved place or forecast.
        assertEquals(WeatherPreferences.UNIT_F, WeatherPreferences.units(context))
        assertEquals(Weather.SRC_MET, WeatherPreferences.source(context))
        assertEquals(30, WeatherPreferences.refreshMinutes(context))
    }
}
