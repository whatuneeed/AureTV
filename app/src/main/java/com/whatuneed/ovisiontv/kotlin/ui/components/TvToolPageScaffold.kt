package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType

internal const val TV_TOOL_LIST_WIDTH = 776f

private const val TITLE_TOP = 44f
private const val DESCRIPTION_TOP = 128f
private const val DESCRIPTION_WIDTH = 900f
private const val DESCRIPTION_LINE_HEIGHT = 30f
private const val DESCRIPTION_LIST_GAP = 88f
private const val ICON_LEFT = 259f
private const val ICON_TOP = 247f
private const val ICON_SIZE = 534f
private const val ICON_RADIUS = 116f
private const val ICON_GLYPH = 236f
private const val LIST_LEFT = 1066f
private const val LIST_BOTTOM_INSET = 44f

/**
 * The two-pane frame used by Settings subpages, now shared by Backup and
 * Software Update. The title and description are centered, the category glyph
 * sits on the left and the actionable list starts on the right at the same
 * x/width as a Settings list. Tool screens keep their own row content and
 * activity lifecycle; only the surface language is shared.
 */
@Composable
internal fun TvToolPageScaffold(
    title: String,
    subtitle: String,
    icon: ImageVector,
    scale: TvScale,
    content: @Composable BoxScope.() -> Unit,
) {
    var subtitleLines by remember(title) { mutableIntStateOf(1) }
    val listTop = DESCRIPTION_TOP + subtitleLines * DESCRIPTION_LINE_HEIGHT + DESCRIPTION_LIST_GAP
    val iconShape = RoundedCornerShape(scale.dp(ICON_RADIUS))

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LauncherColors.backgroundTop, LauncherColors.backgroundBottom),
                ),
            ),
    ) {
        Text(
            text = title,
            color = LauncherColors.textPrimary,
            fontSize = scale.sp(TvType.largeTitle),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = scale.dp(TITLE_TOP))
                .semantics { heading() },
        )

        Text(
            text = subtitle,
            color = LauncherColors.textSecondary,
            fontSize = scale.sp(TvType.callout),
            lineHeight = scale.sp(DESCRIPTION_LINE_HEIGHT),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                subtitleLines = result.lineCount.coerceIn(1, 2)
            },
            modifier = Modifier
                .width(scale.dp(DESCRIPTION_WIDTH))
                .align(Alignment.TopCenter)
                .offset(y = scale.dp(DESCRIPTION_TOP)),
        )

        Box(
            Modifier
                .offset(x = scale.dp(ICON_LEFT), y = scale.dp(ICON_TOP))
                .size(scale.dp(ICON_SIZE))
                .clip(iconShape)
                .background(Brush.linearGradient(listOf(TvInk.rowTop, TvInk.rowBottom)))
                .border(2.dp, Color.White.copy(alpha = 0.08f), iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                imageVector = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
                modifier = Modifier.size(scale.dp(ICON_GLYPH)),
            )
        }

        Box(
            Modifier
                .offset(x = scale.dp(LIST_LEFT), y = scale.dp(listTop))
                .size(
                    width = scale.dp(TV_TOOL_LIST_WIDTH),
                    height = scale.dp(TvOs.SCREEN_HEIGHT - listTop - LIST_BOTTOM_INSET),
                ),
        ) {
            content()
        }
    }
}
