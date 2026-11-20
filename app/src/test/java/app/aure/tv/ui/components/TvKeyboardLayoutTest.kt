package app.aure.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvKeyboardLayoutTest {

    @Test
    fun eachLanguageHasOneScreenRowPerPhysicalRow() {
        assertEquals(listOf(10, 9, 7), TvKeyboardLayout.LATIN.map { it.length })
        assertEquals(listOf(12, 11, 10), TvKeyboardLayout.CYRILLIC.map { it.length })
    }

    @Test
    fun cyrillicEndsWithYoOnTheBottomRow() {
        assertTrue(TvKeyboardLayout.CYRILLIC.last().endsWith("ё"))
    }

    @Test
    fun digitsKeepTheirOwnRows() {
        assertEquals(listOf(10, 7), TvKeyboardLayout.DIGITS.map { it.length })
    }

    @Test
    fun columnStopsAtRowEnds() {
        assertEquals(0, TvKeyboardLayout.clampColumn(-1, 10))
        assertEquals(9, TvKeyboardLayout.clampColumn(99, 10))
        assertEquals(4, TvKeyboardLayout.clampColumn(4, 10))
    }

    @Test
    fun emptyRowDoesNotCrash() {
        assertEquals(0, TvKeyboardLayout.clampColumn(3, 0))
    }
}
