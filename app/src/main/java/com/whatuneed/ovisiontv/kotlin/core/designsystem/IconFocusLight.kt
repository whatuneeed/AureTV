package com.whatuneed.ovisiontv.kotlin.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale

/**
 * 15_ICONS_AND_ARTWORK §3: tvOS lights a focused icon with `edge-light` (250×150)
 * and `specular` (300×300, not clipped by the icon). Those are Apple rasters, so
 * these are drawn analogues with the measured geometry: light falls from the upper
 * left, the bevel catches only the edges, the highlight spills past the tile.
 */
private const val TOP_LIGHT = 0.22f
private const val LEFT_LIGHT = 0.16f
private const val RIGHT_LIGHT = 0.10f
private const val BOTTOM_LIGHT = 0.07f
private const val RIM_LIGHT = 0.50f
// A white rim disappears into white artwork. The dark contour underneath keeps
// the focused tile's boundary readable on light banners without touching the
// white rim that dark artwork still needs.
private const val RIM_DARK = 0.38f
private const val RIM_DARK_WIDTH_DP = 4f
private const val SPECULAR_LIGHT = 0.10f

// Blob centre inside the 300×300 layer (measured size, content unknown). The
// layer is 75 pt taller than the tile, so the centre sits lower and the radius
// stays inside the tile: the highlight catches the top edge instead of hanging
// a 70 pt white halo above the icon.
private const val SPECULAR_CENTER_X = 0.42f
private const val SPECULAR_CENTER_Y = 0.40f
private const val SPECULAR_RADIUS = 0.18f

/** Soft top-left bevel, drawn inside the clipped tile. */
@Composable
internal fun BoxScope.IconEdgeLight(lightAlpha: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            // The gradients are recorded once at full strength; the animation only
            // changes this layer's alpha. Re-recording them for every focus frame
            // was the per-frame cost this split removes.
            .graphicsLayer { alpha = lightAlpha().coerceIn(0f, 1f) }
            .drawBehind {
                val white = Color.White
                drawRect(
                    Brush.verticalGradient(
                        listOf(white.copy(alpha = TOP_LIGHT), white.copy(alpha = 0.06f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.16f,
                    ),
                )
                drawRect(
                    Brush.horizontalGradient(
                        listOf(white.copy(alpha = LEFT_LIGHT), white.copy(alpha = 0.05f), Color.Transparent),
                        startX = 0f,
                        endX = size.width * 0.18f,
                    ),
                )
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, white.copy(alpha = RIGHT_LIGHT)),
                        startX = size.width * 0.86f,
                        endX = size.width,
                    ),
                )
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, white.copy(alpha = BOTTOM_LIGHT)),
                        startY = size.height * 0.88f,
                        endY = size.height,
                    ),
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = RIM_DARK),
                    cornerRadius = CornerRadius(size.height * (TvOs.ICON_RADIUS / TvOs.ICON_HEIGHT)),
                    style = Stroke(width = RIM_DARK_WIDTH_DP.dp.toPx()),
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(
                            white.copy(alpha = RIM_LIGHT),
                            white.copy(alpha = 0.10f),
                            white.copy(alpha = 0.04f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    cornerRadius = CornerRadius(size.height * (TvOs.ICON_RADIUS / TvOs.ICON_HEIGHT)),
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
    )
}

/** Focus light brightness for a tile scale: 0 at rest, 1 at the focused size. */
internal fun focusLightAlpha(tileScale: Float): Float =
    ((tileScale - 1f) / (TvOs.FOCUS_SCALE - 1f)).coerceIn(0f, 1f)

/**
 * Highlight that outgrows the tile: a sibling of the clipped card, so the spill
 * over the shelf and neighbouring rows survives — exactly what "not clipped" means.
 */
@Composable
internal fun BoxScope.IconSpecular(lightAlpha: () -> Float, scale: TvScale) {
    Box(
        // requiredSize, not size: the 300×300 layer must overrule the 250×150 parent.
        Modifier
            .requiredSize(scale.dp(TvOs.SPECULAR_SIZE), scale.dp(TvOs.SPECULAR_SIZE))
            // Same split as the bevel: one recorded radial gradient, animated alpha.
            .graphicsLayer { alpha = lightAlpha().coerceIn(0f, 1f) }
            .drawBehind {
                val center = Offset(size.width * SPECULAR_CENTER_X, size.height * SPECULAR_CENTER_Y)
                val radius = size.width * SPECULAR_RADIUS
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = SPECULAR_LIGHT),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            },
    )
}
