package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The panel used to navigate by a hand-written index graph: Down from Wi-Fi
 * landed on the saver circle, Right from Sound jumped to the top row. These
 * tests pin the spatial rules against the same rectangles the layout uses.
 */
class CcFocusNavigationTest {

    private val rights = listOf("r0", "r1", "r2")
    private val circles = (0..5).map { "c$it" }
    private val rects = ccFocusRects(rights, circles)

    private fun move(from: String, direction: Key): String? = nextCcFocusId(from, direction, rects)

    @Test
    fun geometryMatchesThePanelLayout() {
        val full = ccGeometry(3, 6)
        assertEquals(208f, full.powerHeight)
        assertEquals(224f, full.soundY)
        assertEquals(336f, full.circleY)
        assertEquals(416f, full.panelHeight)

        val short = ccGeometry(2, 6)
        assertEquals(96f, short.powerHeight)
        assertEquals(112f, short.soundY)
        assertEquals(224f, short.circleY)
        assertEquals(304f, short.panelHeight)

        assertEquals(208f, ccGeometry(2, 0).panelHeight)
    }

    @Test
    fun circlesSpreadAcrossThePanelWidth() {
        assertEquals(0f, ccCircleX(0, 6))
        assertEquals(192f, ccCircleX(2, 6))
        assertEquals(480f, ccCircleX(5, 6))
        assertEquals(0f, ccCircleX(0, 1))
    }

    @Test
    fun rightFromPowerEntersTheFirstRow() {
        assertEquals("r0", move(LEAD_ID, Key.DirectionRight))
    }

    @Test
    fun rightFromSoundStaysOnItsRow() {
        assertEquals("r2", move(SOUND_ID, Key.DirectionRight))
    }

    @Test
    fun downWalksTheRightColumn() {
        assertEquals("r1", move("r0", Key.DirectionDown))
        assertEquals("r2", move("r1", Key.DirectionDown))
    }

    @Test
    fun downFromTheLastTileDropsIntoTheCircleBelowIt() {
        assertEquals("c4", move("r2", Key.DirectionDown))
    }

    @Test
    fun upFromCirclesReturnsToTheTileAbove() {
        assertEquals(SOUND_ID, move("c0", Key.DirectionUp))
        assertEquals(SOUND_ID, move("c1", Key.DirectionUp))
        assertEquals(SOUND_ID, move("c2", Key.DirectionUp))
        assertEquals("r2", move("c3", Key.DirectionUp))
        assertEquals("r2", move("c4", Key.DirectionUp))
        assertEquals("r2", move("c5", Key.DirectionUp))
    }

    @Test
    fun upWalksTheRightColumn() {
        assertEquals("r1", move("r2", Key.DirectionUp))
        assertEquals("r0", move("r1", Key.DirectionUp))
    }

    @Test
    fun leftFollowsRows() {
        assertEquals(LEAD_ID, move("r0", Key.DirectionLeft))
        assertEquals(LEAD_ID, move("r1", Key.DirectionLeft))
        assertEquals(SOUND_ID, move("r2", Key.DirectionLeft))
    }

    @Test
    fun downTheLeftColumnLandsOnTheCenteredCircle() {
        assertEquals(SOUND_ID, move(LEAD_ID, Key.DirectionDown))
        assertEquals("c1", move(SOUND_ID, Key.DirectionDown))
    }

    @Test
    fun circlesMoveOnlyAlongTheirRow() {
        assertEquals("c3", move("c2", Key.DirectionRight))
        assertEquals("c2", move("c3", Key.DirectionLeft))
        assertNull(move("c0", Key.DirectionDown))
    }

    @Test
    fun edgesHoldTheFocus() {
        assertNull(move(LEAD_ID, Key.DirectionUp))
        assertNull(move(LEAD_ID, Key.DirectionLeft))
        assertNull(move(SOUND_ID, Key.DirectionLeft))
        assertNull(move("r0", Key.DirectionUp))
        assertNull(move("c0", Key.DirectionLeft))
        assertNull(move("c5", Key.DirectionRight))
    }

    @Test
    fun anUnknownIdHasNowhereToGo() {
        assertNull(move("missing", Key.DirectionRight))
    }

    @Test
    fun theReportedWalkStaysSpatial() {
        // Power → Wi-Fi → Экран → Таймер → circle row → back along the row →
        // Sound → Power; every step follows the pressed direction.
        var focus: String = LEAD_ID
        val path = listOf(
            Key.DirectionRight to "r0",
            Key.DirectionDown to "r1",
            Key.DirectionDown to "r2",
            Key.DirectionDown to "c4",
            Key.DirectionLeft to "c3",
            Key.DirectionLeft to "c2",
            Key.DirectionLeft to "c1",
            Key.DirectionLeft to "c0",
            Key.DirectionUp to SOUND_ID,
            Key.DirectionUp to LEAD_ID,
        )
        path.forEach { (direction, expected) ->
            focus = requireNotNull(move(focus, direction))
            assertEquals(expected, focus)
        }
    }

    @Test
    fun aTwoRowColumnKeepsTheSingleRowPower() {
        val short = ccFocusRects(listOf("r0", "r1"), circles)
        fun go(from: String, direction: Key) = nextCcFocusId(from, direction, short)

        assertEquals("r0", go(LEAD_ID, Key.DirectionRight))
        assertEquals("r1", go(SOUND_ID, Key.DirectionRight))
        assertEquals("r1", go("r0", Key.DirectionDown))
        assertEquals(SOUND_ID, go("r1", Key.DirectionLeft))
        assertEquals(LEAD_ID, go("r0", Key.DirectionLeft))
        assertEquals(SOUND_ID, go(LEAD_ID, Key.DirectionDown))
    }

    @Test
    fun withoutCirclesThePanelEndsAtTheTiles() {
        val bare = ccFocusRects(rights, emptyList())
        assertNull(nextCcFocusId("r2", Key.DirectionDown, bare))
        assertNull(nextCcFocusId(SOUND_ID, Key.DirectionDown, bare))
    }
}