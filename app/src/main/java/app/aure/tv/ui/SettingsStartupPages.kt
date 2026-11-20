package app.aure.tv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.data.StartupPreferences
import app.aure.tv.data.TvApp

/** Startup app list; selection is written and closed by SettingsScreen. */
@Composable
internal fun settingsStartupRows(
    startupTarget: String,
    catalog: List<TvApp>?,
    context: Context,
    onSelectHome: () -> Unit,
    onSelectApp: (TvApp) -> Unit,
): List<SettingsRow> = buildList {
    add(
        SettingsRow(
            title = stringResource(R.string.settings_start_home),
            checked = startupTarget.isEmpty(),
            choice = true,
            action = onSelectHome,
        ),
    )
    catalog.orEmpty()
        .filter { it.packageName.isNotEmpty() && it.packageName != context.packageName }
        .forEach { app ->
            add(
                SettingsRow(
                    title = app.label,
                    checked = startupTarget == StartupPreferences.targetFor(app.packageName),
                    choice = true,
                    action = { onSelectApp(app) },
                ),
            )
        }
}

/** Boot/wake choice; the preference write stays in SettingsScreen. */
@Composable
internal fun settingsStartupWhenRows(
    startupOnWake: Boolean,
    onSelectWake: (Boolean) -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_start_when_boot),
        checked = !startupOnWake,
        choice = true,
        action = { if (startupOnWake) onSelectWake(false) },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_start_when_wake),
        checked = startupOnWake,
        choice = true,
        action = { if (!startupOnWake) onSelectWake(true) },
    ),
)
