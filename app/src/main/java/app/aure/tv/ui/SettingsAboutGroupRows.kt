package app.aure.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.aure.tv.R

// The page builders below are separate composables on purpose: SettingsScreen
// already sits close to the JVM 64 KB method limit, and inlining another set of
// rows pushes the compiler over it.

@Composable
internal fun deviceGroupRows(
    factsReady: Boolean,
    network: String?,
    model: String?,
    freeSpace: String?,
    permissions: String,
    updateValue: String?,
    onUpdate: (() -> Unit)?,
    onBackup: () -> Unit,
    onOpen: (Int) -> Unit,
): List<SettingsRow> = listOfNotNull(
    SettingsRow(
        title = stringResource(R.string.settings_network),
        value = network,
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_NETWORK) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_about_device),
        value = model,
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_DEVICE) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_storage),
        value = freeSpace,
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_STORAGE) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_permissions),
        value = permissions,
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_PERMISSIONS) },
    ),
    SettingsRow(
        // The Android version and build channel live on the System page, not
        // on this row: one fact, one place.
        title = stringResource(R.string.settings_system),
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_SYSTEM) },
    ),
    // Maintenance lives one level below Device, not three: Home → Device →
    // Update/Backup. The System page keeps only Home-button and service state.
    if (updateValue != null && onUpdate != null) {
        SettingsRow(
            title = stringResource(R.string.settings_software_update),
            value = updateValue,
            chevron = true,
            action = onUpdate,
        )
    } else {
        null
    },
    SettingsRow(
        title = stringResource(R.string.backup_title),
        value = stringResource(R.string.backup_open),
        chevron = true,
        action = onBackup,
    ),
)

@Composable
internal fun aboutGroupRows(
    clipCount: Int,
    onOpen: (Int) -> Unit,
    onRunSetup: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_updates),
        value = pluralStringResource(
            R.plurals.settings_clips_count,
            clipCount,
            clipCount,
        ),
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_UPDATES) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_system_info),
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_ABOUT) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_legal),
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_LEGAL) },
    ),
    SettingsRow(
        // The wizard keeps current values and asks nothing twice; this row is
        // the way back after a language or shelf mistake.
        title = stringResource(R.string.settings_run_setup),
        chevron = true,
        action = onRunSetup,
    ),
)

@Composable
internal fun legalRows(onOpen: (Int) -> Unit): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_terms),
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_DOC_TERMS) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_privacy),
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_DOC_PRIVACY) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_aerial_sources),
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_DOC_AERIAL) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_licenses),
        chevron = true,
        action = { onOpen(SETTINGS_PAGE_DOC_LICENSES) },
    ),
)

