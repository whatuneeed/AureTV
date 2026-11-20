package app.aure.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.aure.tv.data.TvApp

/**
 * Home overlays that are not the shelf or the grid: context menus, search,
 * Control Center and the weather/capsule focus. Keeping them together lets the
 * key handler work on one object instead of a dozen local variables.
 */
internal class HomeOverlayState {
    var menuFolderId by mutableStateOf<String?>(null)
    var folderDeleteId by mutableStateOf<String?>(null)
    var menuApp by mutableStateOf<TvApp?>(null)
    var menuOpenedByHold by mutableStateOf(false)
    var moveToFolderApp by mutableStateOf<TvApp?>(null)
    var newFolderApp by mutableStateOf<TvApp?>(null)
    var renameApp by mutableStateOf<TvApp?>(null)
    var searchOpen by mutableStateOf(false)
    var controlCenterOpen by mutableStateOf(false)
    var capsuleFocused by mutableStateOf(false)
    var capsuleFromShelf by mutableStateOf(false)
    var weatherFocused by mutableStateOf(false)
    var weatherFromShelf by mutableStateOf(false)
    var weatherPanelOpen by mutableStateOf(false)
    var okDown by mutableStateOf(false)

    val menuOpen: Boolean
        get() = menuFolderId != null || folderDeleteId != null || menuApp != null ||
            moveToFolderApp != null
}
