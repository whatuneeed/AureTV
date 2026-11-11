package com.whatuneed.ovisiontv.kotlin.ui.components

import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The folder tile is a 250×150 rounded card; the preview grid inside it must
// stay clear of the 30 pt corner, or the corner icons look cut out of the tile.
class FolderPreviewTest {

    private val gridWidth =
        PREVIEW_COLUMNS * PREVIEW_WIDTH + (PREVIEW_COLUMNS - 1) * PREVIEW_GAP
    private val gridHeight =
        PREVIEW_COLUMNS * PREVIEW_HEIGHT + (PREVIEW_COLUMNS - 1) * PREVIEW_GAP
    private val left = (TvOs.ICON_WIDTH - gridWidth) / 2f
    private val top = (TvOs.ICON_HEIGHT - gridHeight) / 2f

    @Test
    fun the_grid_fits_inside_the_inset_cell() {
        assertTrue(gridWidth <= TvOs.ICON_WIDTH - 2 * PREVIEW_INSET)
        assertTrue(gridHeight <= TvOs.ICON_HEIGHT - 2 * PREVIEW_INSET)
        assertEquals(TvOs.ICON_WIDTH / 2f, left + gridWidth / 2f, 0.01f)
        assertEquals(TvOs.ICON_HEIGHT / 2f, top + gridHeight / 2f, 0.01f)
    }

    @Test
    fun the_first_icon_starts_outside_the_rounded_corner() {
        // The arc of a corner with radius R reaches R·(1−1/√2) into the tile.
        val arcInset = TvOs.ICON_RADIUS * (1f - 1f / sqrt(2f))
        assertTrue("left=$left arc=$arcInset", left >= arcInset)
        assertTrue("top=$top arc=$arcInset", top >= arcInset)
    }

    @Test
    fun the_icons_keep_their_wide_aspect() {
        assertEquals(5f / 3f, PREVIEW_WIDTH / PREVIEW_HEIGHT, 0.001f)
    }
}
