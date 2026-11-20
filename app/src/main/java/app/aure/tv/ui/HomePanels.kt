package app.aure.tv.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import app.aure.tv.data.TvApp
import app.aure.tv.ui.components.ControlCenter
import app.aure.tv.ui.components.SearchOverlay

/**
 * Search and Control Center. Both are full-screen overlays with their own focus;
 * Back closes them, while a tile that leaves for another screen does not reopen
 * the panel on return.
 */
@Composable
internal fun HomePanels(
    overlays: HomeOverlayState,
    grid: HomeGridState,
    shelf: HomeShelfState,
    searchableApps: List<TvApp>,
    scale: TvScale,
    reduceMotion: Boolean,
    columns: Int,
    onLaunch: (TvApp) -> Boolean,
    onStartSaver: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystem: ((Intent) -> Unit)?,
) {
    if (overlays.searchOpen) {
        SearchOverlay(
            apps = searchableApps,
            scale = scale,
            reduceMotion = reduceMotion,
            onClose = {
                overlays.searchOpen = false
                if (shelf.focus >= 0) shelf.focusTick++
            },
            // A failed start shows its own toast; the panel stays so the
            // query and the chosen app are not lost.
            onLaunch = { app -> if (onLaunch(app)) overlays.searchOpen = false },
        )
    }

    // 07_CONTROL_CENTER: the panel is a separate overlay with its own focus
    // and Back closes it. Back fades out and hands the focus back to the
    // pill; a tile that leaves for another screen does not (so returning
    // from that screen does not reopen the panel).
    if (overlays.controlCenterOpen) {
        ControlCenter(
            scale = scale,
            reduceMotion = reduceMotion,
            libraryAvailable = grid.items.size > columns,
            onClose = { returnFocus ->
                overlays.controlCenterOpen = false
                if (returnFocus) {
                    overlays.capsuleFocused = true
                } else {
                    overlays.capsuleFocused = false
                    overlays.capsuleFromShelf = false
                }
            },
            onStartSaver = onStartSaver,
            onOpenLibrary = { grid.focusIndex = columns },
            onOpenSearch = { overlays.searchOpen = true },
            onOpenSettings = onOpenSettings,
            onOpenSystem = onOpenSystem,
        )
    }
}
