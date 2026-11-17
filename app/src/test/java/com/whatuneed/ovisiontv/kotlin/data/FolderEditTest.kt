package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderEditTest {

    private fun tvApp(name: String) = TvApp(
        packageName = name,
        activityName = "$name.Main",
        label = name,
        banner = null,
        icon = null,
    )

    private fun app(name: String) = HomeItem.AppItem(tvApp(name))

    private fun folder(id: String, vararg children: String) = HomeItem.FolderItem(
        id = id,
        name = "Папка",
        apps = children.map(::tvApp),
    )

    private fun List<HomeItem>.keys(): List<String> = map { it.key }

    @Test
    fun an_app_leaves_the_folder_right_after_its_tile() {
        val items = listOf(app("a"), folder("f", "b", "c"), app("d"))

        val edit = takeAppOutOfFolder(items, "f", "b")!!

        assertEquals(listOf("a", "folder:f", "b", "d"), edit.items.keys())
        assertEquals(2, edit.focusIndex)
        val kept = edit.items[1] as HomeItem.FolderItem
        assertEquals(listOf("c"), kept.apps.map { it.packageName })
        // The grid is rebuilt, not edited in place: an open panel keeps its copy.
        assertEquals(3, items.size)
        assertEquals(listOf("b", "c"), (items[1] as HomeItem.FolderItem).apps.map { it.packageName })
    }

    @Test
    fun the_last_app_takes_the_place_of_the_folder() {
        val items = listOf(app("a"), folder("f", "b"), app("d"))

        val edit = takeAppOutOfFolder(items, "f", "b")!!

        assertEquals(listOf("a", "b", "d"), edit.items.keys())
        assertEquals(1, edit.focusIndex)
    }

    @Test
    fun an_unknown_folder_or_app_changes_nothing() {
        val items = listOf(app("a"), folder("f", "b"))

        assertNull(takeAppOutOfFolder(items, "missing", "b"))
        assertNull(takeAppOutOfFolder(items, "f", "missing"))
    }

    @Test
    fun a_deleted_folder_leaves_its_apps_in_its_place() {
        val items = listOf(app("a"), folder("f", "b", "c"), app("d"))

        val edit = removeFolder(items, "f")!!

        assertEquals(listOf("a", "b", "c", "d"), edit.items.keys())
        assertEquals(1, edit.focusIndex)
    }

    @Test
    fun deleting_an_unknown_folder_changes_nothing() {
        assertNull(removeFolder(listOf(app("a")), "missing"))
    }

    @Test
    fun hiding_an_app_creates_the_hidden_folder_at_the_end() {
        val items = listOf(app("a"), app("b"), app("c"))

        val edit = hideApp(items, "b", "Ещё приложения")!!

        assertEquals(listOf("a", "c", "folder:hidden"), edit.items.keys())
        val hidden = edit.items.last() as HomeItem.FolderItem
        assertEquals("Ещё приложения", hidden.name)
        assertEquals(listOf("b"), hidden.apps.map { it.packageName })
        // Focus stays on the tile that took the gap, not on the new folder.
        assertEquals(1, edit.focusIndex)
    }

    @Test
    fun hiding_more_apps_appends_them_to_the_same_folder() {
        val first = hideApp(listOf(app("a"), app("b"), app("c")), "b", "Ещё приложения")!!

        val second = hideApp(first.items, "c", "Ещё приложения")!!

        assertEquals(listOf("a", "folder:hidden"), second.items.keys())
        val hidden = second.items.last() as HomeItem.FolderItem
        assertEquals(listOf("b", "c"), hidden.apps.map { it.packageName })
        assertEquals(0, second.focusIndex)
    }

    @Test
    fun hiding_the_last_app_keeps_focus_on_its_neighbour() {
        val items = listOf(app("a"), app("b"))

        val edit = hideApp(items, "b", "Ещё приложения")!!

        assertEquals(listOf("a", "folder:hidden"), edit.items.keys())
        assertEquals(0, edit.focusIndex)
    }

    @Test
    fun hiding_a_dead_tile_removes_it_instead_of_parking_it() {
        val dead = HomeItem.AppItem(tvApp("gone.app").copy(available = false))
        val items = listOf(dead, app("b"))

        val edit = hideApp(items, "gone.app", "Ещё приложения")!!

        assertEquals(listOf("b"), edit.items.keys())
        assertEquals(0, edit.focusIndex)
    }

    @Test
    fun hiding_an_unknown_or_folder_package_changes_nothing() {
        assertNull(hideApp(listOf(app("a"), folder("f", "b")), "missing", "Ещё приложения"))
        assertNull(hideApp(listOf(folder("f", "b")), "b", "Ещё приложения"))
    }

    @Test
    fun an_app_leaves_the_hidden_folder_for_the_end_of_the_grid() {
        val items = listOf(app("a"), folder(HIDDEN_FOLDER_ID, "b", "c"))

        val edit = takeAppOutOfFolder(items, HIDDEN_FOLDER_ID, "b")!!

        assertEquals(listOf("a", "folder:hidden", "b"), edit.items.keys())
        assertEquals(2, edit.focusIndex)
    }

    @Test
    fun the_last_hidden_app_takes_the_place_of_the_hidden_folder() {
        val items = listOf(app("a"), folder(HIDDEN_FOLDER_ID, "b"))

        val edit = takeAppOutOfFolder(items, HIDDEN_FOLDER_ID, "b")!!

        assertEquals(listOf("a", "b"), edit.items.keys())
        assertEquals(1, edit.focusIndex)
    }

    @Test
    fun hidden_apps_keep_the_order_they_were_hidden_in() {
        val items = listOf(app("a"), folder(HIDDEN_FOLDER_ID, "c", "b"))

        assertEquals(listOf("c", "b"), hiddenApps(items).map { it.packageName })
    }

    @Test
    fun without_the_hidden_folder_nothing_is_hidden() {
        val items = listOf(app("a"), folder("f", "b"))

        assertTrue(hiddenApps(items).isEmpty())
    }
}
