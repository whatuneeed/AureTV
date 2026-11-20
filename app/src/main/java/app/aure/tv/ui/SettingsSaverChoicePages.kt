package app.aure.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.ambient.AmbientClockFormat
import app.aure.tv.ambient.AmbientDim
import app.aure.tv.ambient.AmbientMode
import app.aure.tv.ambient.AmbientSession
import app.aure.tv.ambient.RedditRefresh
import app.aure.tv.data.SaverDelay
import app.aure.tv.data.SaverRefresh
import app.aure.tv.data.SaverTheme
import app.aure.tv.ui.components.ambientClockFormatLabel
import app.aure.tv.ui.components.ambientDimLabel
import app.aure.tv.ui.components.ambientModeLabel
import app.aure.tv.ui.components.ambientSessionLabel
import app.aure.tv.ui.components.saverThemeLabel

@Composable
internal fun settingsSaverResetRows(
    onReset: () -> Unit,
    onCancel: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.saver_reset_confirm),
        destructive = true,
        action = onReset,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_cancel),
        action = onCancel,
    ),
)

@Composable
internal fun settingsSaverModeRows(
    selected: AmbientMode,
    onSelect: (AmbientMode) -> Unit,
): List<SettingsRow> = AmbientMode.entries.map { mode ->
    SettingsRow(
        title = ambientModeLabel(mode),
        checked = mode == selected,
        choice = true,
        action = { onSelect(mode) },
    )
}

@Composable
internal fun settingsSaverRedditRefreshRows(
    selected: RedditRefresh,
    onSelect: (RedditRefresh) -> Unit,
): List<SettingsRow> = RedditRefresh.entries.map { refresh ->
    SettingsRow(
        title = stringResource(settingsRedditRefreshRes(refresh)),
        checked = refresh == selected,
        choice = true,
        action = { onSelect(refresh) },
    )
}

@Composable
internal fun settingsSaverClockFormatRows(
    selected: AmbientClockFormat,
    onSelect: (AmbientClockFormat) -> Unit,
): List<SettingsRow> = AmbientClockFormat.entries.map { format ->
    SettingsRow(
        title = ambientClockFormatLabel(format),
        checked = format == selected,
        choice = true,
        action = { onSelect(format) },
    )
}

@Composable
internal fun settingsSaverDimRows(
    selected: AmbientDim,
    onSelect: (AmbientDim) -> Unit,
): List<SettingsRow> = AmbientDim.entries.map { dim ->
    SettingsRow(
        title = ambientDimLabel(dim),
        checked = dim == selected,
        choice = true,
        action = { onSelect(dim) },
    )
}

@Composable
internal fun settingsSaverSessionRows(
    selected: AmbientSession,
    onSelect: (AmbientSession) -> Unit,
): List<SettingsRow> = AmbientSession.entries.map { session ->
    SettingsRow(
        title = ambientSessionLabel(session),
        checked = session == selected,
        choice = true,
        action = { onSelect(session) },
    )
}

@Composable
internal fun settingsSaverDelayRows(
    selected: SaverDelay,
    onSelect: (SaverDelay) -> Unit,
): List<SettingsRow> = SaverDelay.entries.map { delay ->
    SettingsRow(
        title = settingsSaverDelayLabel(delay),
        checked = delay == selected,
        choice = true,
        action = { onSelect(delay) },
    )
}

@Composable
internal fun settingsSaverThemeRows(
    selected: Set<SaverTheme>,
    onToggle: (SaverTheme) -> Unit,
): List<SettingsRow> = SaverTheme.entries.map { theme ->
    SettingsRow(
        title = saverThemeLabel(theme),
        checked = theme in selected,
        action = { onToggle(theme) },
    )
}

@Composable
internal fun settingsSaverRefreshRows(
    selected: SaverRefresh,
    onSelect: (SaverRefresh) -> Unit,
): List<SettingsRow> = SaverRefresh.entries.map { refresh ->
    SettingsRow(
        title = settingsSaverRefreshLabel(refresh),
        checked = refresh == selected,
        choice = true,
        action = { onSelect(refresh) },
    )
}

@Composable
internal fun settingsSaverDelayLabel(delay: SaverDelay): String = stringResource(
    when (delay) {
        SaverDelay.TWO -> R.string.saver_delay_2
        SaverDelay.FIVE -> R.string.saver_delay_5
        SaverDelay.TEN -> R.string.saver_delay_10
        SaverDelay.TWENTY -> R.string.saver_delay_20
        SaverDelay.NEVER -> R.string.saver_never
    },
)

@Composable
internal fun settingsSaverRefreshLabel(refresh: SaverRefresh): String = stringResource(
    when (refresh) {
        SaverRefresh.DAILY -> R.string.saver_refresh_daily
        SaverRefresh.WEEKLY -> R.string.saver_refresh_weekly
        SaverRefresh.MONTHLY -> R.string.saver_refresh_monthly
        SaverRefresh.NEVER -> R.string.saver_refresh_never
    },
)

internal fun settingsRedditRefreshRes(refresh: RedditRefresh): Int = when (refresh) {
    RedditRefresh.SIX -> R.string.saver_reddit_six
    RedditRefresh.TWELVE -> R.string.saver_reddit_twelve
    RedditRefresh.TWENTY_FOUR -> R.string.saver_reddit_twenty_four
}
