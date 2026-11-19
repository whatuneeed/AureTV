package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.ui.components.LAUNCH_START_DELAY_MS
import com.whatuneed.ovisiontv.kotlin.ui.components.LaunchSpec
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launch zoom and its return. The app window covers the end of the forward
 * animation, and the reverse starts from a full-screen card on the first frame.
 */
@Composable
internal fun HomeLaunchEffects(
    grid: HomeGridState,
    launchProgress: Animatable<Float, AnimationVector1D>,
    hideShift: Animatable<Float, AnimationVector1D>,
    reduceMotion: Boolean,
    resumeTick: Int,
    onLaunch: (TvApp) -> Boolean,
) {
    // 12_SYSTEM_TRANSITIONS §3: a launch hides the grid by the same 340 pt as a folder.
    // Closing starts the return at BACK, together with the panel zoom (T4, 0.5 s),
    // instead of waiting for the panel to reach the icon and adding a second half-second.
    LaunchedEffect(grid.openFolder != null, grid.launching != null, grid.folderClosing) {
        val hidden = grid.launching != null || (grid.openFolder != null && !grid.folderClosing)
        hideShift.animateTo(
            targetValue = if (hidden) TvOs.HIDE_GRID_SHIFT else 0f,
            animationSpec = tween(
                motionDuration(FOLDER_ZOOM_MS, reduceMotion),
                easing = TravelEasing,
            ),
        )
    }

    // T1: the card grows for 0.5 s; the app is started at 0.38 s so its window
    // covers the end of the zoom instead of waiting for it. Reduce Motion keeps
    // the same hand-off but starts the app on the first frame.
    LaunchedEffect(grid.launching?.app?.packageName) {
        val active = grid.launching ?: return@LaunchedEffect
        if (reduceMotion) {
            launchProgress.snapTo(1f)
            if (!onLaunch(active.app)) {
                launchProgress.snapTo(0f)
                grid.launching = null
            }
            return@LaunchedEffect
        }
        launchProgress.snapTo(0f)
        coroutineScope {
            val forward = launch { launchProgress.animateTo(1f, LaunchSpec) }
            delay(LAUNCH_START_DELAY_MS)
            if (!onLaunch(active.app)) {
                forward.cancel()
                launchProgress.animateTo(0f, tween(200))
                grid.launching = null
            }
            forward.join()
        }
    }

    // T2: the app window hid the last frame of the zoom, so the reverse starts
    // from a full-screen card. The grid never moved, which is the tvOS rule of
    // pre-positioning the offset before the return frame.
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0 || grid.launching == null) return@LaunchedEffect
        if (reduceMotion) {
            launchProgress.snapTo(0f)
            grid.launching = null
            return@LaunchedEffect
        }
        // 12_SYSTEM_TRANSITIONS T2: the grid must already sit at its final
        // offset on the first return frame; animating the 340 pt back while the
        // card shrinks produces a visible double move. A launch from a folder
        // returns to an open panel, so the grid stays hidden there (T6).
        if (grid.openFolder == null) hideShift.snapTo(0f)
        launchProgress.snapTo(1f)
        launchProgress.animateTo(0f, LaunchSpec)
        grid.launching = null
    }

}
