package app.aure.tv.data

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

    @Test
    fun a_delayed_boot_launch_does_not_depend_on_the_wake_choice() {
        assertTrue(shouldStart(StartupReason.BOOT, "app:com.example.tv", onWake = false, kidsAllowed = true))
        assertFalse(shouldStart(StartupReason.BOOT, StartupPreferences.TARGET_HOME, onWake = true, kidsAllowed = true))
    }

    @Test
    fun a_delayed_wake_launch_rechecks_the_wake_choice() {
        assertTrue(shouldStart(StartupReason.WAKE, "app:com.example.tv", onWake = true, kidsAllowed = true))
        assertFalse(shouldStart(StartupReason.WAKE, "app:com.example.tv", onWake = false, kidsAllowed = true))
    }

    @Test
    fun a_kids_profile_blocks_a_target_outside_its_allowlist() {
        assertFalse(shouldStart(StartupReason.BOOT, "app:com.example.tv", onWake = true, kidsAllowed = false))
        assertFalse(shouldStart(StartupReason.WAKE, "app:com.example.tv", onWake = true, kidsAllowed = false))
    }
}
