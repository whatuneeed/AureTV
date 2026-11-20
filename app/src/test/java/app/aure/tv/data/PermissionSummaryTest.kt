package app.aure.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSummaryTest {

    private fun count(
        accessibilityConnected: Boolean = true,
        accessibilityAvailable: Boolean = true,
        listingsGranted: Boolean = true,
        powerAllowed: Boolean = true,
        powerSupported: Boolean = true,
        defaultHome: Boolean = true,
        homeOverride: Boolean = false,
        locationGranted: Boolean = true,
    ): Int = PermissionSummary.missing(
        accessibilityConnected = accessibilityConnected,
        accessibilityAvailable = accessibilityAvailable,
        listingsGranted = listingsGranted,
        powerAllowed = powerAllowed,
        powerSupported = powerSupported,
        defaultHome = defaultHome,
        homeOverride = homeOverride,
        locationGranted = locationGranted,
    )

    @Test
    fun everythingAllowedCountsNothing() {
        assertEquals(0, count())
    }

    @Test
    fun everyMissingGrantCountsOnce() {
        assertEquals(1, count(accessibilityConnected = false))
        assertEquals(1, count(listingsGranted = false))
        assertEquals(1, count(powerAllowed = false))
        assertEquals(1, count(defaultHome = false))
        assertEquals(1, count(locationGranted = false))
        assertEquals(
            5,
            count(
                accessibilityConnected = false,
                listingsGranted = false,
                powerAllowed = false,
                defaultHome = false,
                locationGranted = false,
            ),
        )
    }

    @Test
    fun grantsTheTvCannotOfferAreNotCounted() {
        // No accessibility screen and no device admin: these two can never be
        // given, so they must not stay in "to allow" forever.
        assertEquals(
            0,
            count(
                accessibilityConnected = false,
                accessibilityAvailable = false,
                powerAllowed = false,
                powerSupported = false,
            ),
        )
    }

    @Test
    fun homeButtonRouteCountsOnlyWhileTheServiceRuns() {
        assertTrue(PermissionSummary.homeReady(defaultHome = true, homeOverride = false, accessibilityConnected = false))
        assertTrue(PermissionSummary.homeReady(defaultHome = false, homeOverride = true, accessibilityConnected = true))
        assertFalse(PermissionSummary.homeReady(defaultHome = false, homeOverride = true, accessibilityConnected = false))
        assertFalse(PermissionSummary.homeReady(defaultHome = false, homeOverride = false, accessibilityConnected = true))
    }
}
