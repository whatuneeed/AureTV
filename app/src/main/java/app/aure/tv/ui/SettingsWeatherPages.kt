package app.aure.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.data.Weather
import app.aure.tv.data.WeatherPreferences

internal val SETTINGS_WEATHER_UNITS = listOf(
    WeatherPreferences.UNIT_AUTO,
    WeatherPreferences.UNIT_C,
    WeatherPreferences.UNIT_F,
)

@Composable
internal fun settingsWeatherUnitsLabel(unit: String): String = stringResource(
    when (unit) {
        WeatherPreferences.UNIT_C -> R.string.weather_units_c
        WeatherPreferences.UNIT_F -> R.string.weather_units_f
        else -> R.string.weather_automatic
    },
)

/** Main Weather page; every write is delegated back to SettingsScreen. */
@Composable
internal fun settingsWeatherRows(
    enabled: Boolean,
    manual: Boolean,
    place: String,
    units: String,
    source: String,
    refreshMinutes: Int,
    age: String,
    busy: Boolean,
    sourceLabels: Map<String, String>,
    onToggle: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenUnits: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenRefresh: () -> Unit,
    onShowUpdated: () -> Unit,
    onRefreshNow: () -> Unit,
    onClear: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.weather_show),
        value = stringResource(if (enabled) R.string.saver_on else R.string.saver_off),
        toggleState = enabled,
        action = onToggle,
    ),
    SettingsRow(
        title = stringResource(R.string.weather_location),
        value = if (manual) place else stringResource(R.string.weather_automatic),
        chevron = true,
        enabled = enabled,
        action = onOpenLocation,
    ),
    SettingsRow(
        title = stringResource(R.string.weather_temperature),
        value = settingsWeatherUnitsLabel(units),
        chevron = true,
        enabled = enabled,
        action = onOpenUnits,
    ),
    SettingsRow(
        title = stringResource(R.string.weather_source),
        value = sourceLabels[source] ?: Weather.sourceName(source),
        chevron = true,
        enabled = enabled,
        action = onOpenSource,
    ),
    SettingsRow(
        title = stringResource(R.string.weather_refresh_every),
        value = stringResource(R.string.weather_minutes, refreshMinutes),
        chevron = true,
        enabled = enabled,
        action = onOpenRefresh,
    ),
    SettingsRow(
        title = stringResource(R.string.weather_last_updated),
        value = age,
        enabled = enabled,
        action = onShowUpdated,
    ),
    SettingsRow(
        title = stringResource(R.string.weather_refresh_now),
        value = if (busy) stringResource(R.string.weather_refreshing_status) else null,
        enabled = enabled,
        action = onRefreshNow,
    ),
    SettingsRow(
        title = stringResource(R.string.weather_clear_data),
        chevron = true,
        // Cancel is the safe default on the confirmation page.
        action = onClear,
    ),
)

@Composable
internal fun settingsWeatherClearRows(
    onClear: () -> Unit,
    onCancel: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.weather_clear_confirm),
        destructive = true,
        action = onClear,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_cancel),
        action = onCancel,
    ),
)

@Composable
internal fun settingsWeatherLocationRows(
    manual: Boolean,
    place: String,
    onAutomatic: () -> Unit,
    onChooseCity: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.weather_automatic),
        checked = !manual,
        choice = true,
        action = onAutomatic,
    ),
    SettingsRow(
        title = stringResource(R.string.weather_choose_city),
        value = if (manual) place else null,
        checked = manual,
        choice = true,
        action = onChooseCity,
    ),
)

@Composable
internal fun settingsWeatherUnitsRows(
    selected: String,
    onSelect: (String) -> Unit,
): List<SettingsRow> = SETTINGS_WEATHER_UNITS.map { unit ->
    SettingsRow(
        title = settingsWeatherUnitsLabel(unit),
        checked = selected == unit,
        choice = true,
        action = { if (selected != unit) onSelect(unit) },
    )
}

@Composable
internal fun settingsWeatherSourceRows(
    selected: String,
    labels: Map<String, String>,
    onSelect: (String) -> Unit,
): List<SettingsRow> = Weather.SOURCES.map { source ->
    SettingsRow(
        title = labels[source] ?: Weather.sourceName(source),
        checked = selected == source,
        choice = true,
        action = { if (selected != source) onSelect(source) },
    )
}

@Composable
internal fun settingsWeatherRefreshRows(
    selected: Int,
    onSelect: (Int) -> Unit,
): List<SettingsRow> = Weather.REFRESH_CHOICES.map { minutes ->
    SettingsRow(
        title = stringResource(
            if (minutes == WeatherPreferences.DEFAULT_REFRESH) {
                R.string.weather_minutes_default
            } else {
                R.string.weather_minutes
            },
            minutes,
        ),
        checked = selected == minutes,
        choice = true,
        action = { onSelect(minutes) },
    )
}
