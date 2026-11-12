package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType

/**
 * 05_FOLDERS §8: HBFolderCellIconVendor renders the first nine icons in a 3×3 grid.
 * Apple's preview sizes were never captured; 75.33×45.2 with pad 8 was the
 * `GENERATED` reference. That grid filled the whole 150 pt cell, so the corner
 * icons sat under the tile's 30 pt radius and were cut by it — they read as if
 * they had escaped the folder. The grid is now inset and scaled to stay inside
 * the rounded shape, and keeps the 16:9 ratio of the cell artwork.
 */
internal const val PREVIEW_INSET = 12f
internal const val PREVIEW_GAP = 4f
internal const val PREVIEW_COLUMNS = 3
internal const val PREVIEW_RADIUS = 6f
internal val PREVIEW_HEIGHT =
    (TvOs.ICON_HEIGHT - PREVIEW_INSET * 2 - PREVIEW_GAP * 2) / PREVIEW_COLUMNS
internal val PREVIEW_WIDTH = PREVIEW_HEIGHT * 5f / 3f

@Composable
fun FolderCard(
    folder: HomeItem.FolderItem,
    focused: Boolean,
    scale: TvScale,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    pickedUp: Boolean = false,
    confirming: Boolean = false,
    stateLabel: String? = null,
    focusRequester: FocusRequester? = null,
    onActivate: (() -> Unit)? = null,
) {
    CardShell(
        focused = focused,
        scale = scale,
        label = folder.name,
        modifier = modifier,
        editing = editing,
        pickedUp = pickedUp,
        confirming = confirming,
        stateLabel = stateLabel,
        focusRequester = focusRequester,
        onActivate = onActivate,
    ) {
        val gridWidth = PREVIEW_COLUMNS * PREVIEW_WIDTH + (PREVIEW_COLUMNS - 1) * PREVIEW_GAP
        val gridHeight = PREVIEW_COLUMNS * PREVIEW_HEIGHT + (PREVIEW_COLUMNS - 1) * PREVIEW_GAP
        val left = (TvOs.ICON_WIDTH - gridWidth) / 2f
        val top = (TvOs.ICON_HEIGHT - gridHeight) / 2f
        Box(Modifier.fillMaxSize()) {
            folder.apps.take(9).forEachIndexed { index, app ->
                val icon = rememberArt(app.icon)
                val banner = rememberArt(app.banner)
                val column = index % PREVIEW_COLUMNS
                val row = index / PREVIEW_COLUMNS
                Box(
                    Modifier
                        .offset(
                            x = scale.dp(left + column * (PREVIEW_WIDTH + PREVIEW_GAP)),
                            y = scale.dp(top + row * (PREVIEW_HEIGHT + PREVIEW_GAP)),
                        )
                        .size(scale.dp(PREVIEW_WIDTH), scale.dp(PREVIEW_HEIGHT))
                        .clip(RoundedCornerShape(scale.dp(PREVIEW_RADIUS)))
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        icon != null -> Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(scale.dp(4f), scale.dp(2f)),
                            contentScale = ContentScale.Fit,
                            filterQuality = FilterQuality.Medium,
                        )

                        banner != null -> Image(
                            bitmap = banner,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                        )

                        else -> Text(
                            text = app.label.take(1).uppercase(),
                            color = LauncherColors.textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = scale.sp(TvType.callout),
                        )
                    }
                }
            }
        }
    }
}
