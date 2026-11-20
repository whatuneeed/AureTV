package app.aure.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The library transition and the clamp that keeps the cursor inside the grid
 * after an app disappears. Both only move existing Animatable values.
 */
@Composable
internal fun HomeLayoutEffects(
    grid: HomeGridState,
    libraryMode: Boolean,
    reduceMotion: Boolean,
    libraryShift: Animatable<Float, AnimationVector1D>,
    libraryFade: Animatable<Float, AnimationVector1D>,
) {
    LaunchedEffect(libraryMode) {
        coroutineScope {
            launch {
                libraryShift.animateTo(
                    targetValue = if (libraryMode) LIBRARY_SHIFT else 0f,
                    animationSpec = tween(
                        motionDuration(LIBRARY_MOTION_MS, reduceMotion),
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            launch {
                libraryFade.animateTo(
                    targetValue = if (libraryMode) 0f else 1f,
                    animationSpec = tween(
                        motionDuration(LIBRARY_FADE_MS, reduceMotion),
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    LaunchedEffect(grid.items.size) {
        if (grid.focusIndex > grid.items.lastIndex) {
            grid.focusIndex = grid.items.lastIndex.coerceAtLeast(0)
        }
    }
}
