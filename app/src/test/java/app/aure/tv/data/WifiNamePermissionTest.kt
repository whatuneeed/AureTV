package app.aure.tv.data

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class WifiNamePermissionTest {

    @Test
    fun android13UsesTheNarrowWifiGrant() {
        assertEquals(Manifest.permission.NEARBY_WIFI_DEVICES, wifiNamePermissionFor(33))
        assertEquals(Manifest.permission.NEARBY_WIFI_DEVICES, wifiNamePermissionFor(34))
        assertEquals(Manifest.permission.NEARBY_WIFI_DEVICES, wifiNamePermissionFor(35))
    }

    @Test
    fun olderReleasesKeepTheLegacyFineLocationGrant() {
        assertEquals(Manifest.permission.ACCESS_FINE_LOCATION, wifiNamePermissionFor(28))
        assertEquals(Manifest.permission.ACCESS_FINE_LOCATION, wifiNamePermissionFor(32))
    }

    @Test
    fun android12AlsoSendsCoarseBecauseThePlatformRequiresIt() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            wifiNamePermissionsFor(28).toList(),
        )
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            wifiNamePermissionsFor(31).toList(),
        )
        assertEquals(
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES),
            wifiNamePermissionsFor(33).toList(),
        )
    }
}
