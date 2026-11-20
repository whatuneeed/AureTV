package app.aure.tv.ui

import app.aure.tv.ui.components.launchRect
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchGeometryTest {

    // First-column cell centre on a 1920×1080 surface.
    private val cellX = 215f
    private val cellY = 899f

    @Test
    fun start_rect_is_the_focused_cell_centered_on_the_icon() {
        val rect = launchRect(cellX, cellY, 0f)
        assertEquals(306f, rect.width, 0.01f)
        assertEquals(184f, rect.height, 0.01f)
        assertEquals(cellX, rect.left + rect.width / 2f, 0.01f)
        assertEquals(cellY, rect.top + rect.height / 2f, 0.01f)
    }

    @Test
    fun end_rect_covers_the_whole_screen() {
        val rect = launchRect(cellX, cellY, 1f)
        assertEquals(0f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
        assertEquals(1920f, rect.width, 0.01f)
        assertEquals(1080f, rect.height, 0.01f)
    }

    @Test
    fun middle_rect_moves_the_center_towards_the_screen_center() {
        val rect = launchRect(cellX, cellY, 0.5f)
        assertEquals((cellX + 1920f / 2f) / 2f, rect.left + rect.width / 2f, 0.01f)
        assertEquals((cellY + 1080f / 2f) / 2f, rect.top + rect.height / 2f, 0.01f)
    }

    @Test
    fun progress_is_clamped() {
        assertEquals(306f, launchRect(cellX, cellY, -1f).width, 0.01f)
        assertEquals(1920f, launchRect(cellX, cellY, 2f).width, 0.01f)
    }
}
