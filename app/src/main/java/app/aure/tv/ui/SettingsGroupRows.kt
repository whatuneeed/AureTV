package app.aure.tv.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.aure.tv.BuildConfig
import app.aure.tv.R
import app.aure.tv.data.AccessibilityStatus
import app.aure.tv.data.Adaptive
import app.aure.tv.data.DeviceFacts
import app.aure.tv.data.DeviceFormat
import app.aure.tv.data.PerformanceProfile
import java.util.Locale

internal data class CacheSizes(
    val stills: Long,
    val posters: Long,
) {
    val total: Long get() = stills + posters
}


@Composable
internal fun updatesRows(
    clipCount: Int,
    busy: Boolean,
    @StringRes status: Int?,
    onRefresh: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_catalogue_size),
        value = pluralStringResource(R.plurals.settings_clips_count, clipCount, clipCount),
    ),
    SettingsRow(
        title = stringResource(R.string.settings_aerial_update),
        value = when {
            busy -> stringResource(R.string.settings_catalog_checking)
            status != null -> stringResource(status)
            else -> null
        },
        action = onRefresh,
    ),
)


@Composable
internal fun aboutInfoRows(appName: String, packageName: String): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_launcher_name),
        value = appName,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_developer),
        value = stringResource(R.string.settings_developer_value),
    ),
    SettingsRow(
        title = stringResource(R.string.settings_version),
        value = BuildConfig.VERSION_NAME,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_build),
        value = BuildConfig.VERSION_CODE.toString(),
    ),
    SettingsRow(
        title = stringResource(R.string.settings_build_channel),
        value = stringResource(
            if (BuildConfig.DEBUG) R.string.settings_build_debug else R.string.settings_build_stable,
        ),
    ),
    SettingsRow(
        title = stringResource(R.string.settings_package),
        value = packageName,
    ),
)


@Composable
internal fun permissionsRows(
    accessibility: AccessibilityStatus.State,
    openAccessibility: () -> Unit,
    listingsAllowed: Boolean,
    listingsPermanent: Boolean,
    requestListings: () -> Unit,
    powerAllowed: Boolean,
    powerSupported: Boolean,
    requestPower: () -> Unit,
    homeReady: Boolean,
    openHome: () -> Unit,
    locationAllowed: Boolean,
    locationPermanent: Boolean,
    requestLocation: () -> Unit,
    openAppInfo: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_perms_home_button),
        value = stringResource(
            when (accessibility) {
                AccessibilityStatus.State.CONNECTED -> R.string.settings_perms_allowed
                AccessibilityStatus.State.STALE -> R.string.settings_home_button_stale
                AccessibilityStatus.State.OFF -> R.string.settings_perms_allow
            },
        ),
        action = openAccessibility,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_perms_listings),
        value = stringResource(
            if (listingsAllowed) {
                R.string.settings_perms_allowed
            } else if (listingsPermanent) {
                R.string.settings_perms_denied
            } else {
                R.string.settings_perms_allow
            },
        ),
        action = if (listingsAllowed || listingsPermanent) openAppInfo else requestListings,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_perms_power),
        value = stringResource(
            when {
                powerAllowed -> R.string.settings_perms_allowed
                powerSupported -> R.string.settings_perms_allow
                else -> R.string.settings_perms_unavailable
            },
        ),
        action = when {
            powerAllowed -> openAppInfo
            powerSupported -> requestPower
            else -> null
        },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_perms_home),
        value = stringResource(
            if (homeReady) R.string.settings_yes else R.string.settings_perms_setup,
        ),
        action = if (homeReady) null else openHome,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_perms_wifi),
        value = stringResource(
            if (locationAllowed) {
                R.string.settings_perms_allowed
            } else if (locationPermanent) {
                R.string.settings_perms_denied
            } else {
                R.string.settings_perms_allow
            },
        ),
        action = if (locationAllowed || locationPermanent) openAppInfo else requestLocation,
    ),
)


@Composable
internal fun deviceRows(
    facts: DeviceFacts?,
    units: List<String>,
    words: Triple<String, String, String>,
    locale: Locale,
): List<SettingsRow> {
    if (facts == null) {
        return listOf(SettingsRow(title = stringResource(R.string.settings_loading)))
    }
    val dash = "—"
    // A display the system did not report is unknown, not a 0 × 0 panel.
    val display = if (facts.screenWidth != null && facts.screenHeight != null) {
        stringResource(
            R.string.settings_device_display_value,
            facts.screenWidth,
            facts.screenHeight,
            DeviceFormat.hertz(facts.refreshHz, locale),
        )
    } else {
        dash
    }
    val memory = if (facts.ramFree != null && facts.ramTotal != null) {
        stringResource(
            R.string.settings_device_memory_value,
            DeviceFormat.size(facts.ramFree, units, locale),
            DeviceFormat.size(facts.ramTotal, units, locale),
        )
    } else {
        dash
    }
    return buildList {
        add(SettingsRow(stringResource(R.string.settings_device_manufacturer), facts.manufacturer))
        add(SettingsRow(stringResource(R.string.settings_device_model), facts.model))
        add(
            SettingsRow(
                stringResource(R.string.settings_device_android),
                stringResource(
                    R.string.settings_device_android_value,
                    facts.android,
                    "API ${facts.api}",
                ),
            ),
        )
        add(SettingsRow(stringResource(R.string.settings_device_patch), facts.securityPatch))
        add(SettingsRow(stringResource(R.string.settings_device_build), facts.buildDisplay))
        add(SettingsRow(stringResource(R.string.settings_device_display), display))
        add(SettingsRow(stringResource(R.string.settings_device_abi), facts.abis))
        add(SettingsRow(stringResource(R.string.settings_device_memory), memory))
        add(
            SettingsRow(
                stringResource(R.string.settings_device_uptime),
                DeviceFormat.uptime(facts.uptimeMs, words.first, words.second, words.third),
            ),
        )
        add(SettingsRow(stringResource(R.string.settings_device_locale), facts.locale))
        add(SettingsRow(stringResource(R.string.settings_device_timezone), facts.timeZone))
        // Read-only: the profile is derived from the heap limit and the panel's
        // thermal state, not a preference the owner could pick well.
        add(
            SettingsRow(
                stringResource(R.string.settings_device_performance),
                stringResource(
                    when (Adaptive.current) {
                        PerformanceProfile.HIGH -> R.string.settings_device_performance_high
                        PerformanceProfile.BALANCED -> R.string.settings_device_performance_balanced
                        PerformanceProfile.LOW -> R.string.settings_device_performance_low
                    },
                ),
            ),
        )
        if (facts.lowRam) {
            add(
                SettingsRow(
                    stringResource(R.string.settings_device_low_ram),
                    stringResource(R.string.settings_yes),
                ),
            )
        }
    }
}


@Composable
internal fun storageRows(
    facts: DeviceFacts?,
    caches: CacheSizes?,
    units: List<String>,
    locale: Locale,
    onClear: () -> Unit,
): List<SettingsRow> {
    if (facts == null) {
        return listOf(SettingsRow(title = stringResource(R.string.settings_loading)))
    }
    val dash = "—"
    val total = facts.storageTotal
    val free = facts.storageFree
    return buildList {
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_total),
                total?.let { DeviceFormat.size(it, units, locale) } ?: dash,
            ),
        )
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_free),
                free?.let { DeviceFormat.size(it, units, locale) } ?: dash,
            ),
        )
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_used),
                if (total != null && free != null) {
                    DeviceFormat.size((total - free).coerceAtLeast(0L), units, locale)
                } else {
                    dash
                },
            ),
        )
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_stills),
                caches?.let { DeviceFormat.size(it.stills, units, locale) } ?: dash,
            ),
        )
        add(
            SettingsRow(
                stringResource(R.string.settings_storage_posters),
                caches?.let { DeviceFormat.size(it.posters, units, locale) } ?: dash,
            ),
        )
        add(
            SettingsRow(
                title = stringResource(R.string.settings_storage_clear),
                value = caches?.let { DeviceFormat.size(it.total, units, locale) },
                destructive = true,
                action = onClear,
            ),
        )
    }
}

