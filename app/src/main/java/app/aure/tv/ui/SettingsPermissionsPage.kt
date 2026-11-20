package app.aure.tv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import app.aure.tv.data.AccessibilityStatus
import app.aure.tv.data.ContentPreferences
import app.aure.tv.data.DeviceFactsReader
import app.aure.tv.data.PermissionSummary
import app.aure.tv.data.SleepControls
import app.aure.tv.data.isPermanentDenial
import app.aure.tv.data.isWifiNamePermanentDenial

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
