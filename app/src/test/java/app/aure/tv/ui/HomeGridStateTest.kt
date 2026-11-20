package app.aure.tv.ui

import androidx.compose.runtime.mutableIntStateOf
import app.aure.tv.data.HomeItem
import app.aure.tv.data.TvApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grid cursor, drag order and folder edits are the parts of Home that are easy
 * to break without a screenshot. These tests pin the edge cases of the move
 * engine and the item list mutations.
 */
class HomeGridStateTest {

    private fun app(name: String) = TvApp(
        packageName = "com.example.$name",
        activityName = "com.example.$name.Main",
        label = name,
        banner = null,
        icon = null,
    )

    private fun item(name: String) = HomeItem.AppItem(app(name))

    private fun grid(vararg names: String, focus: Int = 0) = HomeGridState(
        initialItems = names.map(::item),
        focusState = mutableIntStateOf(focus),
    )

    @Test
    fun down_from_an_incomplete_last_row_lands_on_the_last_cell() {
        val state = grid("a", "b", "c", "d", "e", "f", "g", focus = 4)

        state.moveFocus(0, 1)

        assertEquals(6, state.focusIndex)
    }

    @Test
    fun right_on_the_last_column_stays_put() {
        val state = grid("a", "b", "c", "d", "e", "f", focus = 5)

        state.moveFocus(1, 0)

        assertEquals(5, state.focusIndex)
    }

    @Test
    fun up_from_the_first_row_stays_put() {
        val state = grid("a", "b", "c", focus = 1)

        state.moveFocus(0, -1)

        assertEquals(1, state.focusIndex)
    }

    @Test
    fun moving_with_a_picked_up_tile_reorders_and_commits_on_drop() {
        val state = grid("a", "b", "c")
        var committed: List<HomeItem>? = null

        state.togglePickup { committed = it }
        state.moveFocus(1, 0)
        state.dropPickup { committed = it }

        assertEquals(listOf("b", "a", "c"), state.items.map { (it as HomeItem.AppItem).app.label })
        assertEquals("b", (committed?.get(0) as HomeItem.AppItem).app.label)
        assertFalse(state.pickupActive)
    }

    @Test
    fun renaming_a_folder_updates_the_open_panel() {
        val folder = HomeItem.FolderItem("f1", "Old", listOf(app("a")))
        val state = HomeGridState(listOf(folder), mutableIntStateOf(0))
        state.openFolder = OpenFolder(folder, 0f, 0f)

        state.renameFolder("f1", "New") { }

        assertEquals("New", (state.items.single() as HomeItem.FolderItem).name)
        assertEquals("New", state.openFolder?.folder?.name)
    }

    @Test
    fun resetting_an_app_label_clears_it_inside_folders_too() {
        val folder = HomeItem.FolderItem("f1", "Folder", listOf(app("a"), app("b")))
        val state = HomeGridState(listOf(folder), mutableIntStateOf(0))

        state.applyAppLabel("com.example.a", "Custom")
        assertEquals("Custom", (state.items.single() as HomeItem.FolderItem).apps[0].label)

        state.applyAppLabel("com.example.a", "a")
        assertEquals("a", (state.items.single() as HomeItem.FolderItem).apps[0].label)
    }

    @Test
    fun removing_a_folder_keeps_its_apps_and_commits_the_edit() {
        val folder = HomeItem.FolderItem("f1", "Folder", listOf(app("a"), app("b")))
        val state = HomeGridState(listOf(folder, item("c")), mutableIntStateOf(0))
        var committed: List<HomeItem>? = null

        state.removeFolderById("f1") { committed = it }

        assertTrue(state.items.none { it is HomeItem.FolderItem })
        assertNotNull(committed)
        assertEquals(3, committed?.size)
    }

    @Test
    fun dropping_an_app_into_a_folder_updates_the_target_and_focus() {
        val folder = HomeItem.FolderItem("f1", "Folder", listOf(app("a")))
        val state = HomeGridState(listOf(item("b"), folder), mutableIntStateOf(0))
        var committed: List<HomeItem>? = null

        state.moveAppIntoFolder(app("b"), "f1") { committed = it }

        val updated = state.items.single() as HomeItem.FolderItem
        assertEquals(listOf("a", "b"), updated.apps.map { it.label })
        assertEquals(0, state.focusIndex)
        assertNotNull(committed)
    }

    @Test
    fun an_empty_pickup_is_not_dropped_as_a_change() {
        val state = grid("a")
        var committed = false

        state.dropPickup { committed = true }

        assertFalse(committed)
        assertNull(state.pickupSnapshot)
    }
}
