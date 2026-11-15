package com.whatuneed.ovisiontv.kotlin.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestPermissionTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun theWifiNameUsesTheNarrowModernGrant() {
        assertTrue(manifest.contains("android.permission.NEARBY_WIFI_DEVICES"))
        assertTrue(manifest.contains("android:usesPermissionFlags=\"neverForLocation\""))
        assertTrue(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertTrue(manifest.contains("android:maxSdkVersion=\"32\""))
    }

    @Test
    fun coarseLocationIsLimitedToTheReleasesThatRequireIt() {
        val coarse = manifest.lines().indexOfFirst {
            it.contains("android.permission.ACCESS_COARSE_LOCATION")
        }
        assertTrue(coarse >= 0)
        assertTrue(manifest.lines().drop(coarse).take(2).any { it.contains("maxSdkVersion=\"32\"") })
        assertFalse(manifest.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
    }
}
