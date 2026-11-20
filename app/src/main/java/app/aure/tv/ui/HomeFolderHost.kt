package app.aure.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import app.aure.tv.data.TvApp

/** The open-folder overlay; folder mutations stay on HomeScreen callbacks. */
@Composable
internal fun HomeFolderHost(
    grid: HomeGridState,
    folderFocus: MutableMap<String, String>,
    scale: TvScale,
    reduceMotion: Boolean,
    kidsMode: Boolean,
    startLaunch: (TvApp, Offset) -> Unit,
    updateFolderApps: (String, List<TvApp>) -> Unit,
    renameFolder: (String, String) -> Unit,
    moveAppToHome: (String, TvApp) -> Unit,
    onAppInfo: (TvApp) -> Unit,
    onUninstall: (TvApp) -> Unit,
) {
    grid.openFolder?.let { open ->
        FolderOverlay(
            folder = open.folder,
            tileCenter = Offset(open.tileX, open.tileY),
            scale = scale,
            launching = grid.launching != null,
            initialFocusKey = folderFocus[open.folder.id],
            onFocusKeyChanged = { key ->
                if (key == null) {
                    folderFocus.remove(open.folder.id)
                } else {
                    folderFocus[open.folder.id] = key
                }
            },
            onLaunch = { app, center -> startLaunch(app, center) },
            onClosing = { grid.folderClosing = true },
            onClose = {
                grid.openFolder = null
                grid.folderClosing = false
            },
            onAppsChanged = { apps -> updateFolderApps(open.folder.id, apps) },
            onRename = { name -> renameFolder(open.folder.id, name) },
            onMoveToHome = { app -> moveAppToHome(open.folder.id, app) },
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
            reduceMotion = reduceMotion,
            kidsMode = kidsMode,
        )
    }
}
