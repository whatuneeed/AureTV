package com.whatuneed.ovisiontv.kotlin.data

/**
 * tvOS restores focus to the item that had it, and a folder is a root item in
 * its own right (04_APP_LIBRARY §4, 05_FOLDERS), so the saved value must be
 * able to name a folder as well as an app. App entries stay bare package names
 * so lists written by earlier builds remain readable.
 */
private const val FOLDER_PREFIX = "folder:"

internal fun homeFocusKey(item: HomeItem): String = when (item) {
    is HomeItem.AppItem -> item.app.packageName
    is HomeItem.FolderItem -> FOLDER_PREFIX + item.id
}

/** -1 when nothing matches; legacy values may still name an app now in a folder. */
internal fun indexOfHomeFocus(items: List<HomeItem>, saved: String?): Int {
    if (saved.isNullOrEmpty()) return -1
    if (saved.startsWith(FOLDER_PREFIX)) {
        val id = saved.removePrefix(FOLDER_PREFIX)
        val byId = items.indexOfFirst { it is HomeItem.FolderItem && it.id == id }
        if (byId >= 0) return byId
    }
    val byPackage = items.indexOfFirst {
        it is HomeItem.AppItem && it.app.packageName == saved
    }
    if (byPackage >= 0) return byPackage
    return items.indexOfFirst { item ->
        item is HomeItem.FolderItem && item.apps.any { it.packageName == saved }
    }
}
