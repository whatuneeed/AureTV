package com.whatuneed.ovisiontv.kotlin.ui

import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.homeFocusKey
import kotlinx.coroutines.delay

/**
 * Scroll, shelf index and the debounced focus save. The tile itself is part of
 * the key: deleting a folder can put another app at the same index.
 */
@Composable
internal fun HomeFocusEffects(
    grid: HomeGridState,
    scroll: Animatable<Float, AnimationVector1D>,
    columns: Int,
    shelfIndex: Int,
    onShelfIndex: (Int) -> Unit,
    onLastFocusIndex: (Int) -> Unit,
    onLastFocusKey: (String?) -> Unit,
    scrollTarget: (Int) -> Float,
    prefs: SharedPreferences,
) {
    LaunchedEffect(grid.focusIndex) {
        onLastFocusIndex(grid.focusIndex)
    }

    LaunchedEffect(grid.focusIndex, grid.items.size, grid.items.getOrNull(grid.focusIndex)) {
        val focused = grid.items.getOrNull(grid.focusIndex) ?: return@LaunchedEffect
        if (grid.focusIndex < columns) onShelfIndex(grid.focusIndex)

        scroll.animateTo(
            targetValue = scrollTarget(grid.focusIndex),
            animationSpec = tween(
                durationMillis = TvOs.SCROLL_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    // Phase 08: a cursor that is still moving is not a choice. Only the focus
    // that rests for a moment is saved, so a burst of presses neither rewrites
    // the preference on every step nor leaves a half-travelled position behind.
    LaunchedEffect(grid.focusIndex, grid.items.getOrNull(grid.focusIndex)) {
        val focused = grid.items.getOrNull(grid.focusIndex) ?: return@LaunchedEffect
        onLastFocusKey(homeFocusKey(focused))
        delay(FOCUS_SAVE_MS)
        val editor = prefs.edit()
        editor.putString(LauncherPrefs.KEY_FOCUS_PACKAGE, homeFocusKey(focused))
        if (focused is HomeItem.AppItem && grid.focusIndex < columns) {
            editor.putString(LauncherPrefs.KEY_SHELF_PACKAGE, focused.app.packageName)
        }
        editor.apply()
    }
}
