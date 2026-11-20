package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ambient.RedditRefresh
import com.whatuneed.ovisiontv.kotlin.ambient.RedditSelection
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientClockFormat
import com.whatuneed.ovisiontv.kotlin.data.AerialQuality
import com.whatuneed.ovisiontv.kotlin.data.SaverRefresh

/** Photos mode rows; picker/clear actions stay on SettingsScreen. */
@Composable
internal fun saverPhotoRows(
    label: String?,
    selected: Int,
    canPickFolder: Boolean,
    canPickPhotos: Boolean,
    onPick: () -> Unit,
    onForget: () -> Unit,
): List<SettingsRow> = buildList {
    add(
        SettingsRow(
            title = stringResource(
                if (canPickFolder) {
                    R.string.saver_photos_folder
                } else {
                    R.string.saver_photos_selected_title
                },
            ),
            value = when {
                label != null -> label
                selected > 0 -> pluralStringResource(
                    R.plurals.saver_photos_selected,
                    selected,
                    selected,
                )
                canPickFolder || canPickPhotos -> stringResource(R.string.saver_photos_none)
                else -> stringResource(R.string.saver_photos_unavailable_short)
            },
            chevron = true,
            action = onPick,
        ),
    )
    if (label != null || selected > 0) {
        add(
            SettingsRow(
                title = stringResource(
                    if (label != null) {
                        R.string.saver_photos_forget
                    } else {
                        R.string.saver_photos_forget_photos
                    },
                ),
                chevron = true,
                action = onForget,
            ),
        )
    }
}

/** Reddit mode rows; feed fetch/clear actions stay on SettingsScreen. */
@Composable
internal fun saverRedditRows(
    subreddit: String,
    refresh: RedditRefresh,
    selection: RedditSelection,
    itemCount: Int,
    selectedCount: Int,
    loading: Boolean,
    stale: Boolean,
    onEditName: () -> Unit,
    onOpenRefresh: () -> Unit,
    onToggleAll: () -> Unit,
    onOpenCatalog: () -> Unit,
    onRefreshNow: () -> Unit,
    onForget: () -> Unit,
): List<SettingsRow> = buildList {
    add(
        SettingsRow(
            title = stringResource(R.string.saver_reddit_subreddit),
            value = "r/$subreddit",
            chevron = true,
            action = onEditName,
        ),
    )
    add(
        SettingsRow(
            title = stringResource(R.string.saver_reddit_refresh),
            value = stringResource(settingsRedditRefreshRes(refresh)),
            chevron = true,
            action = onOpenRefresh,
        ),
    )
    add(
        SettingsRow(
            title = stringResource(R.string.saver_reddit_all),
            value = stringResource(
                if (selection == RedditSelection.ALL) R.string.saver_on else R.string.saver_off,
            ),
            toggleState = selection == RedditSelection.ALL,
            action = onToggleAll,
        ),
    )
    add(
        SettingsRow(
            title = stringResource(R.string.saver_reddit_catalog),
            value = when {
                loading -> stringResource(R.string.saver_reddit_loading)
                itemCount == 0 -> stringResource(R.string.saver_reddit_unavailable)
                selection == RedditSelection.ALL -> pluralStringResource(
                    R.plurals.saver_reddit_images,
                    itemCount,
                    itemCount,
                )
                else -> pluralStringResource(
                    R.plurals.saver_reddit_selected,
                    selectedCount,
                    selectedCount,
                )
            },
            chevron = true,
            action = onOpenCatalog,
        ),
    )
    add(
        SettingsRow(
            title = stringResource(R.string.saver_reddit_refresh_now),
            value = if (loading) stringResource(R.string.saver_reddit_loading) else null,
            // This runs the refresh in place; a chevron would promise another page.
            action = onRefreshNow,
        ),
    )
    if (stale) {
        add(
            SettingsRow(
                title = stringResource(R.string.saver_reddit_stale),
                value = null,
            ),
        )
    }
    add(
        SettingsRow(
            title = stringResource(R.string.saver_reddit_forget),
            chevron = true,
            action = onForget,
        ),
    )
}

/** Aerial mode rows; catalogue/quality writes stay on SettingsScreen. */
@Composable
internal fun saverAerialRows(
    quality: AerialQuality,
    uhd: Boolean,
    hdr: Boolean,
    themeSummary: String,
    refresh: SaverRefresh,
    onToggleQuality: () -> Unit,
    onOpenChooser: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenRefresh: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.saver_aerial_quality),
        value = when {
            quality == AerialQuality.COMPATIBLE ->
                stringResource(R.string.saver_aerial_quality_1080)
            uhd && hdr -> stringResource(R.string.saver_aerial_quality_best)
            uhd -> stringResource(R.string.saver_aerial_quality_4k)
            hdr -> stringResource(R.string.saver_aerial_quality_hdr)
            else -> stringResource(R.string.saver_aerial_quality_limited)
        },
        toggleState = quality == AerialQuality.BEST,
        action = onToggleQuality,
    ),
    SettingsRow(
        title = stringResource(R.string.saver_choose),
        value = stringResource(R.string.saver_aerials),
        chevron = true,
        action = onOpenChooser,
    ),
    SettingsRow(
        title = stringResource(R.string.saver_themes),
        value = themeSummary,
        chevron = true,
        action = onOpenThemes,
    ),
    SettingsRow(
        title = stringResource(R.string.saver_refresh),
        value = settingsSaverRefreshLabel(refresh),
        chevron = true,
        action = onOpenRefresh,
    ),
)

/** Clock-only rows: hour format and the anti-burn-in pixel shift. */
@Composable
internal fun saverClockRows(
    format: AmbientClockFormat,
    pixelShift: Boolean,
    onOpenFormat: () -> Unit,
    onTogglePixelShift: () -> Unit,
): List<SettingsRow> = listOf(
    SettingsRow(
        title = stringResource(R.string.saver_clock_format),
        value = com.whatuneed.ovisiontv.kotlin.ui.components.ambientClockFormatLabel(format),
        chevron = true,
        action = onOpenFormat,
    ),
    SettingsRow(
        title = stringResource(R.string.saver_pixel_shift),
        value = stringResource(if (pixelShift) R.string.saver_on else R.string.saver_off),
        toggleState = pixelShift,
        action = onTogglePixelShift,
    ),
)

/** Weather overlay row, shown only for Clock mode. */
@Composable
internal fun saverWeatherOverlayRows(
    weatherEnabled: Boolean,
    showWeather: Boolean,
    onToggleWeather: () -> Unit,
    onOpenWeather: () -> Unit,
): List<SettingsRow> = listOf(
    if (weatherEnabled) {
        SettingsRow(
            title = stringResource(R.string.saver_show_weather),
            value = stringResource(if (showWeather) R.string.saver_on else R.string.saver_off),
            toggleState = showWeather,
            action = onToggleWeather,
        )
    } else {
        SettingsRow(
            title = stringResource(R.string.saver_show_weather),
            value = stringResource(R.string.saver_weather_off),
            chevron = true,
            action = onOpenWeather,
        )
    },
)
