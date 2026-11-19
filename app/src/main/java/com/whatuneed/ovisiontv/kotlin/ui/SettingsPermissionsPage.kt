package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.whatuneed.ovisiontv.kotlin.data.AccessibilityStatus
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.DeviceFactsReader
import com.whatuneed.ovisiontv.kotlin.data.PermissionSummary
import com.whatuneed.ovisiontv.kotlin.data.SleepControls
import com.whatuneed.ovisiontv.kotlin.data.isPermanentDenial
import com.whatuneed.ovisiontv.kotlin.data.isWifiNamePermanentDenial

/**
 * Permissions page. Row rendering lives in permissionsRows; this builder keeps
 * the same Home-ready rule and the same permanent-denial checks as before.
 */
@Composable
internal fun settingsPermissionsRows(
    accessibility: AccessibilityStatus.State,
    defaultHome: Boolean,
    homeOverride: Boolean,
    permissionsAsked: Set<String>,
    listingsAllowed: Boolean,
    powerAllowed: Boolean,
    locationAllowed: Boolean,
    context: Context,
    onOpenAccessibility: () -> Unit,
    onRequestListings: () -> Unit,
    onRequestPower: () -> Unit,
    onOpenHome: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenAppInfo: () -> Unit,
): List<SettingsRow> {
    val powerSupported = SleepControls.adminSupported(context)
    // The same rule feeds the root counter: taking the Home button over is a
    // real route, so the row must not ask for the system HOME too.
    val homeReady = PermissionSummary.homeReady(
        defaultHome = defaultHome,
        homeOverride = homeOverride,
        accessibilityConnected = accessibility == AccessibilityStatus.State.CONNECTED,
    )
    val listingsAsked = ContentPreferences.READ_TV_LISTINGS in permissionsAsked
    val locationAsked = DeviceFactsReader.wifiNamePermission() in permissionsAsked
    return permissionsRows(
        accessibility = accessibility,
        openAccessibility = onOpenAccessibility,
        listingsAllowed = listingsAllowed,
        listingsPermanent = !listingsAllowed &&
            isPermanentDenial(context, ContentPreferences.READ_TV_LISTINGS, listingsAsked),
        requestListings = onRequestListings,
        powerAllowed = powerAllowed,
        powerSupported = powerSupported,
        requestPower = onRequestPower,
        homeReady = homeReady,
        openHome = onOpenHome,
        locationAllowed = locationAllowed,
        locationPermanent = !locationAllowed &&
            isWifiNamePermanentDenial(context, locationAsked),
        requestLocation = onRequestLocation,
        openAppInfo = onOpenAppInfo,
    )
}
