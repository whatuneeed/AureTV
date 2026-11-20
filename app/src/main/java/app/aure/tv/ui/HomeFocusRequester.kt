package app.aure.tv.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * Layout gate instead of a blind delay: once the grid is placed every tile's
 * FocusRequester is attached, so the cursor can move on the first frame.
 */
@Composable
internal fun HomeFocusRequester(
    grid: HomeGridState,
    shelf: HomeShelfState,
    overlays: HomeOverlayState,
    focusTick: Int,
    gridModal: Boolean,
    gridPlaced: Boolean,
    capsuleRequester: FocusRequester,
    weatherRequester: FocusRequester,
    pickupRequester: FocusRequester,
    focusRequester: FocusRequester,
    tileRequesters: Map<String, FocusRequester>,
    context: Context,
) {
    LaunchedEffect(
        Unit,
        focusTick,
        gridModal,
        overlays.capsuleFocused,
        overlays.weatherFocused,
        grid.focusIndex,
        grid.items,
        grid.editing,
        grid.pickupActive,
        gridPlaced,
        shelf.focus,
        shelf.sectionFocused,
    ) {
        if (gridModal || !gridPlaced) return@LaunchedEffect
        // TopShelf owns the real target while the carousel or its section bar
        // has the cursor; the grid must not pull focus back underneath it.
        if (shelf.focus >= 0 || shelf.sectionFocused) return@LaunchedEffect
        // The pill sits outside the grid; while it owns the cursor the grid must
        // not take the focus back (the panel requests its own tile).
        if (overlays.capsuleFocused) {
            capsuleRequester.requestHomeFocusWhenAttached()
            return@LaunchedEffect
        }
        if (overlays.weatherFocused) {
            weatherRequester.requestHomeFocusWhenAttached()
            return@LaunchedEffect
        }
        // The proxy is the cell under the cursor while it is being moved, so it
        // carries the focus; the tiles the cursor passes over stay silent.
        if (grid.pickupActive) {
            pickupRequester.requestHomeFocusWhenAttached()
            return@LaunchedEffect
        }
        val requester = grid.items.getOrNull(grid.focusIndex)?.let { tileRequesters[it.key] }
        val target = requester ?: focusRequester
        if (!target.requestHomeFocusWhenAttached()) return@LaunchedEffect
        // TalkBack installs its own first-node focus when the window opens, so a
        // cold start would announce tile 0 while the saved cursor sits deeper.
        // Only an accessibility service does that; without one the repeat would
        // be a blind 350 ms window where keys can land on the wrong tile.
        // Compose 1.7 has no enabled flag on its accessibility manager, so the
        // platform one answers whether a screen reader is exploring by touch.
        val screenReader = context
            .getSystemService(AccessibilityManager::class.java)
            ?.isTouchExplorationEnabled == true
        if (!screenReader) return@LaunchedEffect
        delay(350)
        target.requestHomeFocusWhenAttached()
    }
}
