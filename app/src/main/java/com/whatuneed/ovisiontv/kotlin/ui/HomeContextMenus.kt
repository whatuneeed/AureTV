package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import com.whatuneed.ovisiontv.kotlin.data.AppLabelStore
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryEngine
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason

/**
 * Folder, app, shelf and "move to folder" menus. They only present choices;
 * every mutation stays on HomeScreen callbacks so a dismissed menu cannot
 * change the grid by itself.
 */
@Composable
internal fun HomeContextMenus(
    overlays: HomeOverlayState,
    grid: HomeGridState,
    shelf: HomeShelfState,
    discovery: DiscoveryEngine,
    scale: TvScale,
    context: Context,
    prefsName: String,
    cellCenter: (Int) -> Offset,
    startLaunch: (TvApp, Offset) -> Unit,
    togglePickup: () -> Unit,
    openFolderAt: (Int) -> Unit,
    removeFolderById: (String) -> Unit,
    hideAppFromHome: (TvApp) -> Unit,
    resetAppLabel: (TvApp) -> Unit,
    onAppInfo: (TvApp) -> Unit,
    onUninstall: (TvApp) -> Unit,
    shelfCardAnchor: () -> Offset,
    closeShelfMenu: () -> Unit,
    activateShelfCard: (DiscoveryItem) -> Unit,
    isHiddenFolder: (HomeItem.FolderItem) -> Boolean,
    moveAppIntoFolder: (TvApp, String) -> Unit,
) {
    overlays.menuFolderId?.let { folderId ->
        val index = grid.items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
        val folder = grid.items.getOrNull(index) as? HomeItem.FolderItem
        if (folder != null) {
            FolderContextMenu(
                folder = folder,
                anchor = cellCenter(index),
                scale = scale,
                suppressOkUntilRelease = overlays.menuOpenedByHold,
                onOpen = { openFolderAt(index) },
                onMove = {
                    grid.focusIndex = index
                    grid.editing = true
                    grid.mergeVictim = null
                    grid.confirmIndex = null
                    togglePickup()
                },
                onRequestDelete = {
                    // The menu already animated out; ask once more
                    // before the layout loses the folder.
                    overlays.menuFolderId = null
                    overlays.folderDeleteId = folderId
                },
                onDismiss = {
                    overlays.menuFolderId = null
                    overlays.menuOpenedByHold = false
                },
            )
        }
    }

    // Destructive confirmation for a folder. It is a second menu, not a
    // silent delete: the apps return home, but the user still chooses.
    overlays.folderDeleteId?.let { folderId ->
        val index = grid.items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
        val folder = grid.items.getOrNull(index) as? HomeItem.FolderItem
        if (folder != null) {
            FolderDeleteMenu(
                folder = folder,
                anchor = cellCenter(index),
                scale = scale,
                onCancel = { overlays.folderDeleteId = null },
                onDelete = {
                    removeFolderById(folderId)
                    overlays.folderDeleteId = null
                },
                onDismiss = { overlays.folderDeleteId = null },
            )
        }
    }

    overlays.menuApp?.let { app ->
        val index = grid.items.indexOfFirst {
            it is HomeItem.AppItem && it.app.packageName == app.packageName
        }
        if (index >= 0) {
            // The own settings tile is the launcher's screen: hiding or
            // uninstalling it would remove the way into Settings.
            val own = app.packageName == context.packageName
            val hasCustomLabel = app.available &&
                AppLabelStore.label(context, app.packageName, prefsName) != null
            AppContextMenu(
                app = app,
                own = own,
                hasCustomLabel = hasCustomLabel,
                anchor = cellCenter(index),
                scale = scale,
                suppressOkUntilRelease = overlays.menuOpenedByHold,
                onOpen = { startLaunch(app, cellCenter(index)) },
                onMove = {
                    grid.focusIndex = index
                    grid.editing = true
                    grid.mergeVictim = null
                    grid.confirmIndex = null
                    togglePickup()
                },
                onMoveToFolder = { overlays.moveToFolderApp = app },
                onHide = { hideAppFromHome(app) },
                onRename = { overlays.renameApp = app },
                onResetLabel = { resetAppLabel(app) },
                onAppInfo = { onAppInfo(app) },
                onUninstall = { onUninstall(app) },
                onDismiss = {
                    overlays.menuApp = null
                    overlays.menuOpenedByHold = false
                },
            )
        }
    }

    shelf.menu?.let { item ->
        val favorite = discovery.isFavorite(item.id)
        ShelfContextMenu(
            item = item,
            favorite = favorite,
            anchor = shelfCardAnchor(),
            scale = scale,
            suppressOkUntilRelease = overlays.menuOpenedByHold,
            onOpen = {
                closeShelfMenu()
                activateShelfCard(item)
            },
            onToggleFavorite = {
                if (favorite) {
                    discovery.removeFavorite(item.id)
                } else {
                    discovery.addFavorite(
                        item.copy(reason = DiscoveryReason.FAVORITE),
                    )
                }
                closeShelfMenu()
                shelf.revision++
            },
            onHide = {
                discovery.hide(item)
                closeShelfMenu()
                shelf.revision++
            },
            onDismiss = closeShelfMenu,
        )
    }

    // 05_FOLDERS: the app menu's "Move to Folder…" lists the user folders and
    // ends with "New Folder…", as the Java launcher did — a folder made here
    // is named first and takes the app with it.
    overlays.moveToFolderApp?.let { app ->
        val folders = grid.items.filterIsInstance<HomeItem.FolderItem>()
            .filterNot(isHiddenFolder)
        val tileIndex = grid.items.indexOfFirst {
            it is HomeItem.AppItem && it.app.packageName == app.packageName
        }
        MoveToFolderMenu(
            folders = folders,
            anchor = cellCenter(if (tileIndex >= 0) tileIndex else grid.focusIndex),
            scale = scale,
            onMove = { folder -> moveAppIntoFolder(app, folder.id) },
            onNewFolder = { overlays.newFolderApp = app },
            onDismiss = { overlays.moveToFolderApp = null },
        )
    }
}
