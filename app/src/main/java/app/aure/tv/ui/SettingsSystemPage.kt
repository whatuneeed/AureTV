package app.aure.tv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.data.DeviceFacts
import app.aure.tv.data.TvApp
import app.aure.tv.data.AccessibilityStatus
import app.aure.tv.data.StartupPreferences

/**
 * System page: Home-button takeover, startup app, AirPlay. The owner screen
 * keeps navigation and preference writes; this builder keeps the exact row
 * conditions that were in SettingsScreen.
 */
@Composable
internal fun settingsSystemRows(
    facts: DeviceFacts?,
    accessibility: AccessibilityStatus.State,
    homeOverride: Boolean,
    startupLabel: String,
    startupTarget: String,
    startupOnWake: Boolean,
    catalog: List<TvApp>?,
    context: Context,
    onSetHomeOverride: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenStartup: (Int) -> Unit,
    onOpenStartupWhen: (Int) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onAirPlay: () -> Unit,
): List<SettingsRow> {
    val defaultHome = facts?.defaultHome == true
    // Two steps, as in Java: the row turns the preference on, and the next
    // press sends the user to the switch it still needs.
    val homeAction: (() -> Unit)? = when {
        defaultHome -> null
        !homeOverride -> ({ onSetHomeOverride(true) })
        accessibility != AccessibilityStatus.State.CONNECTED -> onOpenAccessibility
        else -> ({ onSetHomeOverride(false) })
    }
    return listOfNotNull(
        SettingsRow(
            title = stringResource(R.string.settings_system_home),
            value = facts?.let {
                stringResource(if (it.defaultHome) R.string.settings_yes else R.string.settings_no)
            } ?: stringResource(R.string.settings_loading),
        ),
        SettingsRow(
            title = stringResource(R.string.settings_system_open),
            chevron = true,
            action = onOpenSystemSettings,
        ),
        SettingsRow(
            title = stringResource(R.string.settings_home_button),
            value = stringResource(
                when {
                    defaultHome -> R.string.settings_home_button_not_needed
                    !homeOverride -> R.string.settings_home_button_off
                    accessibility == AccessibilityStatus.State.CONNECTED ->
                        R.string.settings_home_button_on
                    accessibility == AccessibilityStatus.State.STALE ->
                        R.string.settings_home_button_stale
                    else -> R.string.settings_home_button_allow
                },
            ),
            action = homeAction,
        ),
        SettingsRow(
            title = stringResource(R.string.settings_start_open),
            value = startupLabel,
            chevron = true,
            action = {
                val apps = catalog.orEmpty().filter {
                    it.packageName.isNotEmpty() && it.packageName != context.packageName
                }
                val index = apps.indexOfFirst { it.packageName == StartupPreferences.appPackage(startupTarget) }
                onOpenStartup(if (index < 0) 0 else index + 1)
            },
        ),
        if (startupTarget.isNotEmpty()) {
            SettingsRow(
                title = stringResource(R.string.settings_start_when),
                value = stringResource(
                    if (startupOnWake) {
                        R.string.settings_start_when_wake
                    } else {
                        R.string.settings_start_when_boot
                    },
                ),
                chevron = true,
                action = { onOpenStartupWhen(if (startupOnWake) 1 else 0) },
            )
        } else {
            null
        },
        // A wake launch needs the same switch as the Home override.
        if (startupTarget.isNotEmpty() &&
            startupOnWake &&
            accessibility != AccessibilityStatus.State.CONNECTED
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_start_wake),
                value = stringResource(
                    if (accessibility == AccessibilityStatus.State.STALE) {
                        R.string.settings_home_button_stale
                    } else {
                        R.string.settings_home_button_allow
                    },
                ),
                action = onOpenAccessibility,
            )
        } else {
            null
        },
        SettingsRow(
            title = stringResource(R.string.settings_system_airplay),
            value = facts?.let {
                stringResource(
                    if (it.airplayInstalled) {
                        R.string.settings_airplay_installed
                    } else {
                        R.string.settings_airplay_missing
                    },
                )
            } ?: stringResource(R.string.settings_loading),
            chevron = facts?.airplayInstalled == true,
            action = if (facts?.airplayInstalled == true) onAirPlay else null,
            actionLabel = stringResource(R.string.settings_airplay_open),
        ),
    )
}

