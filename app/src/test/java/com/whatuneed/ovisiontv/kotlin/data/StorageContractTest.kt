package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * On-disk names are a compatibility contract: renaming any of these keys or
 * files makes an upgrade silently forget the user's layout, profiles, PIN or
 * weather city. The values may only change with an explicit migration.
 */
class StorageContractTest {

    @Test
    fun preferenceFilesKeepTheirNames() {
        assertEquals("ovisiontv_launcher", LauncherPrefs.NAME)
        assertEquals("profiles", ProfileStore.FILE)
        assertEquals("kids_mode", KidsStore.FILE)
        assertEquals("weather", WeatherPreferences.NAME)
        assertEquals("startup", StartupPreferences.NAME)
    }

    @Test
    fun launcherKeysStayStable() {
        assertEquals("focused_package", LauncherPrefs.KEY_FOCUS_PACKAGE)
        assertEquals("shelf_package", LauncherPrefs.KEY_SHELF_PACKAGE)
        assertEquals("home_order", LauncherPrefs.KEY_HOME_ORDER)
    }

    @Test
    fun weatherKeysStayStable() {
        assertEquals("enabled", WeatherPreferences.K_ENABLED)
        assertEquals("manual", WeatherPreferences.K_MANUAL)
        assertEquals("place", WeatherPreferences.K_PLACE)
        assertEquals("lat", WeatherPreferences.K_LAT)
        assertEquals("lon", WeatherPreferences.K_LON)
        assertEquals("units", WeatherPreferences.K_UNITS)
        assertEquals("source", WeatherPreferences.K_SOURCE)
        assertEquals("refresh_min", WeatherPreferences.K_REFRESH)
        assertEquals("cache2", WeatherPreferences.K_CACHE)
        assertEquals("cache_at", WeatherPreferences.K_CACHE_AT)
        assertEquals("cache_key", WeatherPreferences.K_CACHE_KEY)
    }

    @Test
    fun startupKeysStayStable() {
        assertEquals("target", StartupPreferences.KEY_TARGET)
        assertEquals("label", StartupPreferences.KEY_LABEL)
        assertEquals("on_wake", StartupPreferences.KEY_ON_WAKE)
        assertEquals("home_override", StartupPreferences.KEY_HOME_OVERRIDE)
    }

    @Test
    fun profileIdentityStaysStable() {
        assertEquals("p1", ProfileCatalog.DEFAULT_ID)
        assertEquals(3, ProfileCatalog.MAX)
        assertEquals("p1", ProfileCatalog.nextId(emptyList()))
    }

    @Test
    fun saverThemeKeysStayStable() {
        assertEquals(
            setOf("landscapes", "cities", "underwater", "earth"),
            SaverTheme.entries.map { it.key }.toSet(),
        )
    }
}
