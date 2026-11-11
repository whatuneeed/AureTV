package com.whatuneed.ovisiontv.kotlin.data

/** The order after an edit and the tile that should keep focus. */
data class HomeEdit(val items: List<HomeItem>, val focusIndex: Int)

/**
 * 05_FOLDERS §9: an app taken out of the panel returns to Home right after the
 * folder, and a folder that loses its last child is not drawn at all — the app
 * takes the folder's place.
 */
internal fun takeAppOutOfFolder(
    items: List<HomeItem>,
    folderId: String,
    packageName: String,
): HomeEdit? {
    val index = items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
    if (index < 0) return null
    val folder = items[index] as HomeItem.FolderItem
    val app = folder.apps.firstOrNull { it.packageName == packageName } ?: return null
    val rest = folder.apps.filterNot { it.packageName == packageName }
    val list = items.toMutableList()
    return if (rest.isEmpty()) {
        list[index] = HomeItem.AppItem(app)
        HomeEdit(list, index)
    } else {
        list[index] = folder.copy(apps = rest)
        list.add(index + 1, HomeItem.AppItem(app))
        HomeEdit(list, index + 1)
    }
}

/**
 * Hiding is an Android TV launcher convention: the app stays installed and its
 * tile moves into an auto-created "More Apps" folder at the end of the grid.
 * The id is fixed so restarts find the same tile.
 */
internal const val HIDDEN_FOLDER_ID = "hidden"

internal fun isHiddenFolder(item: HomeItem?): Boolean =
    item is HomeItem.FolderItem && item.id == HIDDEN_FOLDER_ID

/** The apps parked in the hidden folder, in the order they were hidden. */
internal fun hiddenApps(items: List<HomeItem>): List<TvApp> =
    items.filterIsInstance<HomeItem.FolderItem>()
        .firstOrNull { it.id == HIDDEN_FOLDER_ID }
        ?.apps
        .orEmpty()

/**
 * "Hide" moves the app into the hidden folder at the end of the grid. The app
 * stays installed and returns with "Show on Home" from that folder.
 */
internal fun hideApp(
    items: List<HomeItem>,
    packageName: String,
    hiddenFolderName: String,
): HomeEdit? {
    val index = items.indexOfFirst { it is HomeItem.AppItem && it.app.packageName == packageName }
    if (index < 0) return null
    val app = (items[index] as HomeItem.AppItem).app
    val list = items.toMutableList()
    list.removeAt(index)
    val hiddenIndex = list.indexOfFirst { isHiddenFolder(it) }
    if (hiddenIndex >= 0) {
        val folder = list[hiddenIndex] as HomeItem.FolderItem
        list[hiddenIndex] = folder.copy(apps = folder.apps + app)
    } else {
        list.add(HomeItem.FolderItem(HIDDEN_FOLDER_ID, hiddenFolderName, listOf(app)))
    }
    // Focus must not jump into the hidden folder just because the tile before it
    // disappeared; the neighbours keep it.
    val target = when {
        index < list.size && !isHiddenFolder(list[index]) -> index
        else -> (index - 1 downTo 0).firstOrNull { !isHiddenFolder(list[it]) }
            ?: list.lastIndex.coerceAtLeast(0)
    }
    return HomeEdit(list, target)
}

/** A deleted folder keeps its apps: they land where the folder was, in order. */
internal fun removeFolder(items: List<HomeItem>, folderId: String): HomeEdit? {
    val index = items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
    if (index < 0) return null
    val folder = items[index] as HomeItem.FolderItem
    val list = items.toMutableList()
    list.removeAt(index)
    list.addAll(index, folder.apps.map(HomeItem::AppItem))
    return HomeEdit(list, index)
}
