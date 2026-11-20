package app.aure.tv.data

/**
 * How many of the launcher's real grants are still missing. A grant whose
 * system screen or device feature does not exist can never be given, so it is
 * left out instead of counting against the TV forever.
 */
object PermissionSummary {
    /**
     * The Home button route counts only while the service is connected: the
     * stored intent alone does not open anything.
     */
    fun homeReady(
        defaultHome: Boolean,
        homeOverride: Boolean,
        accessibilityConnected: Boolean,
    ): Boolean = defaultHome || (homeOverride && accessibilityConnected)

    fun missing(
        accessibilityConnected: Boolean,
        accessibilityAvailable: Boolean,
        listingsGranted: Boolean,
        powerAllowed: Boolean,
        powerSupported: Boolean,
        defaultHome: Boolean,
        homeOverride: Boolean,
        locationGranted: Boolean,
    ): Int = listOf(
        !accessibilityConnected && accessibilityAvailable,
        !listingsGranted,
        !powerAllowed && powerSupported,
        !homeReady(defaultHome, homeOverride, accessibilityConnected),
        !locationGranted,
    ).count { it }
}
