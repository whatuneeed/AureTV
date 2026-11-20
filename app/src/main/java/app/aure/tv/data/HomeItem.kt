package app.aure.tv.data

/**
 * TVOS27_RESEARCH/04_APP_LIBRARY.md §1: there is no separate library screen;
 * Home is a single grid whose only grouping is user folders (HBFolder : HBRootItem).
 */
sealed interface HomeItem {
    val key: String

    data class AppItem(val app: TvApp) : HomeItem {
        override val key: String get() = app.packageName
    }

    data class FolderItem(
        val id: String,
        val name: String,
        val apps: List<TvApp>,
    ) : HomeItem {
        override val key: String get() = "folder:$id"
    }
}
