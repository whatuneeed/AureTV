package com.whatuneed.ovisiontv.kotlin.ui

import com.whatuneed.ovisiontv.kotlin.data.TvApp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOverlayStateTest {

    private fun app() = TvApp(
        packageName = "com.example.app",
        activityName = "com.example.app.Main",
        label = "App",
        banner = null,
        icon = null,
    )

    @Test
    fun any_context_menu_gates_the_grid() {
        val state = HomeOverlayState()

        assertFalse(state.menuOpen)
        state.menuFolderId = "f1"
        assertTrue(state.menuOpen)
        state.menuFolderId = null
        state.folderDeleteId = "f1"
        assertTrue(state.menuOpen)
        state.folderDeleteId = null
        state.menuApp = app()
        assertTrue(state.menuOpen)
        state.menuApp = null
        state.moveToFolderApp = app()
        assertTrue(state.menuOpen)
    }

    @Test
    fun keyboard_and_panel_overlays_are_independent() {
        val state = HomeOverlayState()

        state.renameApp = app()
        state.searchOpen = true
        state.weatherPanelOpen = true

        assertFalse(state.menuOpen)
        assertTrue(state.searchOpen)
        assertTrue(state.weatherPanelOpen)
        assertFalse(state.controlCenterOpen)
    }
}
