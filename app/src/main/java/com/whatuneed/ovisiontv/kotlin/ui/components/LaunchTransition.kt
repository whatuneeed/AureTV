package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TravelEasing
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale

// 12_SYSTEM_TRANSITIONS T1/T2 and 14_MOTION_DESIGN M14/M15: 0.5 s both ways.
// The curve lives in HBUILaunchAnimator and was never extracted, so the project
// reuses the measured folder ease instead of inventing a second one.
internal const val LAUNCH_MS = 500

// The app is started before the zoom finishes, like the reference launcher:
// its window covers the last stretch, so no black frame shows through.
internal const val LAUNCH_START_DELAY_MS = 380L
internal val LaunchEasing = TravelEasing

internal val LaunchSpec = tween<Float>(LAUNCH_MS, easing = LaunchEasing)

internal data class LaunchRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * 12_SYSTEM_TRANSITIONS §2: at progress 0 the layer is the focused rect
 * (306×184) centred on the icon's content view; at progress 1 it is the full
 * screen. Both edges interpolate linearly, so the centre travels from the cell
 * to the screen centre while the size grows around it.
 */
internal fun launchRect(centerX: Float, centerY: Float, progress: Float): LaunchRect {
    val p = progress.coerceIn(0f, 1f)
    val width = TvOs.FOCUSED_WIDTH + (TvOs.SCREEN_WIDTH - TvOs.FOCUSED_WIDTH) * p
    val height = TvOs.FOCUSED_HEIGHT + (TvOs.SCREEN_HEIGHT - TvOs.FOCUSED_HEIGHT) * p
    val left = centerX - width / 2f + (TvOs.SCREEN_WIDTH / 2f - centerX) * p
    val top = centerY - height / 2f + (TvOs.SCREEN_HEIGHT / 2f - centerY) * p
    return LaunchRect(left, top, width, height)
}

/** Progress 0 shows the focused cell, 1 fills the screen (T1); the return runs it back (T2). */
@Composable
internal fun LaunchOverlay(
    app: TvApp,
    cellCenter: Offset,
    progress: () -> Float,
    scale: TvScale,
) {
    // The clock is read here, not by Home: reading it in the caller recomposed
    // the whole grid once per frame, which is what made the zoom look stepped.
    val p = progress().coerceIn(0f, 1f)
    // The card starts on the same focused rect the grid draws: 250×150 grown by
    // 306/250, so the handoff from the tile needs no cross-fade.
    val rect = launchRect(cellCenter.x, cellCenter.y, p)
    val left = rect.left
    val top = rect.top
    val width = rect.width
    val height = rect.height
    val startRadius = TvOs.ICON_RADIUS * TvOs.FOCUS_SCALE
    val radius = startRadius * (1f - p)
    val shape = RoundedCornerShape(scale.dp(radius.coerceAtLeast(0f)))
    val focusAlpha = (1f - p).coerceIn(0f, 1f)

    Box(
        Modifier
            .offset(x = scale.dp(left), y = scale.dp(top))
            .size(scale.dp(width), scale.dp(height))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(LauncherColors.cardFallbackTop, LauncherColors.cardFallbackBottom),
                ),
            ),
    ) {
        AppArtwork(app = app, scale = scale)
        IconEdgeLight { focusAlpha }
    }
}
