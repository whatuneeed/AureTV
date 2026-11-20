package app.aure.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import app.aure.tv.data.HomeItem
import app.aure.tv.data.TvApp
import app.aure.tv.discovery.DiscoveryItem
import app.aure.tv.ui.components.TvMenu
import app.aure.tv.ui.components.TvMenuEntry

/**
 * Context menu for a folder tile: open it, start a drag, or ask for the
 * destructive confirmation. The confirmation is a separate menu, never a
 * silent delete.
 */
@Composable
internal fun FolderContextMenu(
    folder: HomeItem.FolderItem,
    anchor: Offset,
    scale: TvScale,
    suppressOkUntilRelease: Boolean,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvMenu(
        title = folder.name,
        entries = listOf(
            TvMenuEntry(stringResource(R.string.menu_open), onSelect = onOpen),
            TvMenuEntry(stringResource(R.string.menu_move), onSelect = onMove),
            TvMenuEntry(
                label = stringResource(R.string.menu_delete_folder),
                destructive = true,
                onSelect = onRequestDelete,
            ),
        ),
        anchor = anchor,
        scale = scale,
        suppressOkUntilRelease = suppressOkUntilRelease,
        onDismiss = onDismiss,
    )
}

/**
 * Second step for a folder delete. Cancel comes first: opening the menu and
 * pressing OK must not destroy the folder, the user has to move down.
 */
@Composable
internal fun FolderDeleteMenu(
    folder: HomeItem.FolderItem,
    anchor: Offset,
    scale: TvScale,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvMenu(
        title = folder.name,
        entries = listOf(
            TvMenuEntry(stringResource(R.string.settings_cancel), onSelect = onCancel),
            TvMenuEntry(
                label = stringResource(R.string.menu_delete_folder),
                destructive = true,
                onSelect = onDelete,
            ),
        ),
        anchor = anchor,
        scale = scale,
        onDismiss = onDismiss,
    )
}

/**
 * Context menu for an app tile. A missing package keeps only the actions that
 * make sense without an activity: move, move to a folder and hide.
 */
@Composable
internal fun AppContextMenu(
    app: TvApp,
    own: Boolean,
    hasCustomLabel: Boolean,
    anchor: Offset,
    scale: TvScale,
    suppressOkUntilRelease: Boolean,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onMoveToFolder: () -> Unit,
    onHide: () -> Unit,
    onRename: () -> Unit,
    onResetLabel: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = buildList {
        if (app.available) {
            add(TvMenuEntry(stringResource(R.string.menu_open), onSelect = onOpen))
        }
        add(TvMenuEntry(stringResource(R.string.menu_move), onSelect = onMove))
        add(TvMenuEntry(stringResource(R.string.menu_move_to_folder), onSelect = onMoveToFolder))
        if (!own) {
            add(TvMenuEntry(stringResource(R.string.menu_hide), onSelect = onHide))
        }
        if (app.available) {
            add(TvMenuEntry(stringResource(R.string.menu_rename_app), onSelect = onRename))
        }
        if (hasCustomLabel) {
            add(TvMenuEntry(stringResource(R.string.menu_reset_app_name), onSelect = onResetLabel))
        }
        if (app.available) {
            add(TvMenuEntry(stringResource(R.string.menu_app_info), onSelect = onAppInfo))
        }
        if (app.available && !own && !app.system) {
            add(
                TvMenuEntry(
                    label = stringResource(R.string.menu_uninstall),
                    destructive = true,
                    onSelect = onUninstall,
                ),
            )
        }
    }
    TvMenu(
        title = app.label,
        entries = entries,
        anchor = anchor,
        scale = scale,
        suppressOkUntilRelease = suppressOkUntilRelease,
        onDismiss = onDismiss,
    )
}

/**
 * A shelf card is a real content offer: open it, keep it, or hide it. The menu
 * is the only place favorites are added, so a recommendation can never quietly
 * become "personalized" without the viewer's action.
 */
@Composable
internal fun ShelfContextMenu(
    item: DiscoveryItem,
    favorite: Boolean,
    anchor: Offset,
    scale: TvScale,
    suppressOkUntilRelease: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    TvMenu(
        title = item.title,
        entries = listOf(
            TvMenuEntry(stringResource(R.string.menu_open), onSelect = onOpen),
            TvMenuEntry(
                label = stringResource(
                    if (favorite) {
                        R.string.menu_remove_favorite
                    } else {
                        R.string.menu_add_favorite
                    },
                ),
                onSelect = onToggleFavorite,
            ),
            TvMenuEntry(
                // Hiding a card is reversible from Settings; it is not a
                // destructive action and must not be painted like delete.
                label = stringResource(R.string.menu_hide),
                onSelect = onHide,
            ),
        ),
        anchor = anchor,
        scale = scale,
        suppressOkUntilRelease = suppressOkUntilRelease,
        onDismiss = onDismiss,
    )
}

/**
 * The app menu's "Move to Folder…" picker: user folders first, then
 * "New Folder…" that names a folder and takes the app with it.
 */
@Composable
internal fun MoveToFolderMenu(
    folders: List<HomeItem.FolderItem>,
    anchor: Offset,
    scale: TvScale,
    onMove: (HomeItem.FolderItem) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = buildList {
        folders.forEach { folder ->
            add(TvMenuEntry(folder.name, onSelect = { onMove(folder) }))
        }
        add(TvMenuEntry(stringResource(R.string.new_folder_action), onSelect = onNewFolder))
    }
    TvMenu(
        title = stringResource(R.string.move_to_folder_title),
        entries = entries,
        anchor = anchor,
        scale = scale,
        onDismiss = onDismiss,
    )
}
