package app.aure.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.discovery.DiscoveryItem
import app.aure.tv.discovery.DiscoverySettings

internal data class DiscoveryUi(
    val recommendations: Boolean,
    val online: Boolean,
    val previewAutoplay: Boolean,
    val continueWatching: Boolean,
    val personalization: Boolean,
    val hidden: List<DiscoveryItem>,
)

@Composable
internal fun discoverySettingsRows(
    ui: DiscoveryUi,
    onUiChange: (DiscoveryUi) -> Unit,
    settings: DiscoverySettings,
    onOpenHidden: () -> Unit,
    onOpenClear: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.settings_recommendations),
        value = stringResource(if (ui.recommendations) R.string.saver_on else R.string.saver_off),
        toggleState = ui.recommendations,
        action = {
            val next = ui.copy(recommendations = !ui.recommendations)
            onUiChange(next)
            settings.setRecommendations(next.recommendations)
        },
    ),
    SettingsRow(
        title = stringResource(R.string.discovery_online),
        value = stringResource(if (ui.online) R.string.saver_on else R.string.saver_off),
        toggleState = ui.online,
        action = {
            val next = ui.copy(online = !ui.online)
            onUiChange(next)
            settings.setOnline(next.online)
        },
    ),
    SettingsRow(
        title = stringResource(R.string.discovery_preview_autoplay),
        value = stringResource(if (ui.previewAutoplay) R.string.saver_on else R.string.saver_off),
        toggleState = ui.previewAutoplay,
        action = {
            val next = ui.copy(previewAutoplay = !ui.previewAutoplay)
            onUiChange(next)
            settings.setPreviewAutoplay(next.previewAutoplay)
        },
    ),
    SettingsRow(
        title = stringResource(R.string.settings_continue_watching),
        value = stringResource(if (ui.continueWatching) R.string.saver_on else R.string.saver_off),
        toggleState = ui.continueWatching,
        action = {
            val next = ui.copy(continueWatching = !ui.continueWatching)
            onUiChange(next)
            settings.setContinueWatching(next.continueWatching)
        },
    ),
    SettingsRow(
        title = stringResource(R.string.discovery_personalization),
        value = stringResource(if (ui.personalization) R.string.saver_on else R.string.saver_off),
        toggleState = ui.personalization,
        action = {
            val next = ui.copy(personalization = !ui.personalization)
            onUiChange(next)
            settings.setPersonalization(next.personalization)
        },
    ),
    SettingsRow(
        title = stringResource(R.string.discovery_hidden),
        value = ui.hidden.size.toString(),
        chevron = true,
        action = onOpenHidden,
    ),
    SettingsRow(
        title = stringResource(R.string.discovery_clear),
        chevron = true,
        action = onOpenClear,
    ),
)

@Composable
internal fun discoveryHiddenRows(
    hidden: List<DiscoveryItem>,
    onRestore: (DiscoveryItem) -> Unit,
): List<SettingsRow> = if (hidden.isEmpty()) {
    listOf(SettingsRow(title = stringResource(R.string.discovery_hidden_empty)))
} else {
    hidden.map { item ->
        SettingsRow(
            title = item.title,
            value = item.packageLabel ?: item.packageName,
            chevron = true,
            actionLabel = stringResource(R.string.discovery_restore),
            action = { onRestore(item) },
        )
    }
}

@Composable
internal fun discoveryClearRows(
    onClear: () -> Unit,
    onCancel: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.discovery_clear_confirm),
        destructive = true,
        action = onClear,
    ),
    SettingsRow(
        title = stringResource(R.string.settings_cancel),
        action = onCancel,
    ),
)

