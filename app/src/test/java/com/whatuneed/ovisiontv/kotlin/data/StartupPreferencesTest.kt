package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPreferencesTest {

    @Test
    fun appTargetsRoundTrip() {
        assertEquals("app:com.example.tv", StartupPreferences.targetFor("com.example.tv"))
        assertEquals(
            "com.example.tv",
            StartupPreferences.appPackage("app:com.example.tv"),
        )
    }

    @Test
    fun onlyAppTargetsCanLaunch() {
        assertNull(StartupPreferences.appPackage(StartupPreferences.TARGET_HOME))
        assertNull(StartupPreferences.appPackage("app:"))
        assertNull(StartupPreferences.appPackage("input:hdmi1"))
    }

    @Test
    fun wakeLaunchNeedsBothAnAppAndTheWakeChoice() {
        assertFalse(StartupPreferences.opensOnWake(StartupPreferences.TARGET_HOME, true))
        assertFalse(StartupPreferences.opensOnWake("app:com.example.tv", false))
        assertTrue(StartupPreferences.opensOnWake("app:com.example.tv", true))
    }
}
