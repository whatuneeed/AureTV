package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Hosts a launcher surface in the 1920x1080 design canvas (UX-18).
 *
 * The canvas is fitted by the smaller axis of the window (see
 * [tvCanvasFactor]) and centred, so a wider or taller panel gains soft margins
 * instead of stretching or clipping the layout. Safe-area insets are taken out
 * before fitting: a display cutout or a transient system bar moves the canvas
 * instead of covering the dock. On a 16:9 window both rules agree and the
 * result is the same as the plain width scaling this launcher used before.
 */
@Composable
fun TvCanvas(content: @Composable () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        val factor = remember(maxWidth, maxHeight) {
            tvCanvasFactor(maxWidth.value, maxHeight.value)
        }
        val scale = remember(factor) { TvScale(factor) }
        CompositionLocalProvider(LocalTvScale provides scale) {
            Box(
                Modifier.requiredSize(
                    scale.dp(TvOs.SCREEN_WIDTH),
                    scale.dp(TvOs.SCREEN_HEIGHT),
                ),
            ) {
                content()
            }
        }
    }
}
