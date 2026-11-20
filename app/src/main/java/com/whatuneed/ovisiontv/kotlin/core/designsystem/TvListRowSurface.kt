package com.whatuneed.ovisiontv.kotlin.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.whatuneed.ovisiontv.kotlin.ui.LocalReduceMotion
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration

internal const val TV_LIST_ROW_HEIGHT = 64f
internal const val TV_LIST_ROW_RADIUS = 16f
internal const val TV_LIST_ROW_PADDING = 22f
internal const val TV_LIST_ROW_FOCUS_SCALE = 1.04f

/**
 * The one focus surface for a list row in Settings, Backup and Software Update.
 *
 * The three screens used to grow the row differently: Settings filled it white
 * and scaled it, while the tool screens drew a translucent fill plus a border.
 * Keeping fill, scale, radius and padding here makes the same focus transition
 * read as one product, while the row content and width stay screen-specific.
 *
 * [content] receives the animated focus amount so text can move from white to
 * black in step with the fill instead of switching a frame earlier.
 */
@Composable
internal fun TvListRowSurface(
    focused: Boolean,
    scale: TvScale,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Float) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "tvListRowFocus",
    )
    val shape = RoundedCornerShape(scale.dp(TV_LIST_ROW_RADIUS))
    Box(
        modifier
            .height(scale.dp(TV_LIST_ROW_HEIGHT))
            .graphicsLayer {
                val grow = 1f + (TV_LIST_ROW_FOCUS_SCALE - 1f) * focusAmount
                scaleX = grow
                scaleY = grow
            }
            .clip(shape)
            .background(
                lerp(Color.White.copy(alpha = 0.10f), Color.White, focusAmount),
            )
            .padding(horizontal = scale.dp(TV_LIST_ROW_PADDING)),
    ) {
        content(focusAmount)
    }
}
