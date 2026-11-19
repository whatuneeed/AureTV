package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryRail
import com.whatuneed.ovisiontv.kotlin.ui.components.TvKeyRepeat
import kotlinx.coroutines.CoroutineScope

/**
 * The remote surface of Home. Preview restarts the saver clock and releases
 * stuck repeats; the main handler gates every modal before reaching the grid.
 */
internal fun Modifier.homeKeyInput(
    grid: HomeGridState,
    shelf: HomeShelfState,
    overlays: HomeOverlayState,
    rails: List<DiscoveryRail>,
    shelfCards: List<DiscoveryItem>,
    weatherVisible: Boolean,
    libraryMode: Boolean,
    columns: Int,
    kidsMode: Boolean,
    keyRepeat: TvKeyRepeat,
    scope: CoroutineScope,
    onOrderChanged: (List<HomeItem>) -> Unit,
    context: Context,
    cellCenter: (Int) -> Offset,
    startLaunch: (TvApp, Offset) -> Unit,
    activateShelfCard: (DiscoveryItem) -> Unit,
    focusCapsule: (Boolean) -> Unit,
    menuOpen: Boolean,
    onUserActivity: () -> Unit,
): Modifier = this
    // Preview runs before menus and panels consume the key, so any press
    // anywhere on this screen restarts the saver's idle clock.
    .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) onUserActivity()
        // Preview runs before an overlay can consume the release: a key whose
        // KeyUp lands on the panel must not stay "pressed" here and swallow
        // the next press of the same key after it closes.
        if (event.type == KeyEventType.KeyUp) keyRepeat.release(event.key)
        false
    }
    .onKeyEvent { event ->
        // The zoom owns the screen until the app or the return finishes.
        if (grid.launching != null) return@onKeyEvent true
        // Search is a separate surface: anything the panel did not use must
        // not reach the grid behind it (a MENU press, for example).
        if (overlays.searchOpen) return@onKeyEvent true
        // The Control Center owns the remote while it is open; the focused
        // tile already consumed its own keys.
        if (overlays.controlCenterOpen) return@onKeyEvent true
        // A shelf card menu is its own focus surface.
        if (shelf.menu != null) return@onKeyEvent true
        // The forecast panel has its own focus and Back handler.
        if (overlays.weatherPanelOpen) return@onKeyEvent true
        // A context menu owns the remote. A hold that opened it keeps sending
        // repeats, and some remotes deliver each repeat as its own press and
        // release; those pairs must not reach the grid.
        if (menuOpen || overlays.newFolderApp != null || overlays.renameApp != null) {
            return@onKeyEvent true
        }
        handleHomeKey(
            event = event,
            grid = grid,
            shelf = shelf,
            overlays = overlays,
            rails = rails,
            shelfCards = shelfCards,
            weatherVisible = weatherVisible,
            libraryMode = libraryMode,
            columns = columns,
            kidsMode = kidsMode,
            keyRepeat = keyRepeat,
            scope = scope,
            onOrderChanged = onOrderChanged,
            context = context,
            cellCenter = cellCenter,
            startLaunch = startLaunch,
            activateShelfCard = activateShelfCard,
            focusCapsule = focusCapsule,
        )
    }
    .focusable()
