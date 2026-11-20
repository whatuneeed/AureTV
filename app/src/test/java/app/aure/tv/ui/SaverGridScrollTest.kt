package app.aure.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class SaverGridScrollTest {

    private val viewportStart = 0
    private val viewportEnd = 600

    @Test
    fun aRowAboveTheViewportScrollsBackUpByTheShortfall() {
        assertEquals(
            -120f,
            rowScrollDelta(
                rowTop = -120,
                rowBottom = 30,
                viewportStart = viewportStart,
                viewportEnd = viewportEnd,
            ),
        )
    }

    @Test
    fun aRowBelowTheViewportScrollsDownByTheOverhang() {
        assertEquals(
            75f,
            rowScrollDelta(
                rowTop = 560,
                rowBottom = 675,
                viewportStart = viewportStart,
                viewportEnd = viewportEnd,
            ),
        )
    }

    @Test
    fun aFullyVisibleRowLeavesTheGridAlone() {
        assertNull(
            rowScrollDelta(
                rowTop = 200,
                rowBottom = 315,
                viewportStart = viewportStart,
                viewportEnd = viewportEnd,
            ),
        )
    }

    @Test
    fun aFullyVisibleFirstRowIsNotDraggedToTheTop() {
        // Regression: animateScrollToItem put the focused row at the viewport
        // start every time, so stepping up one row slid the whole grid up and
        // the previous row appeared above the focus, reading as a spring.
        assertNull(
            rowScrollDelta(
                rowTop = 40,
                rowBottom = 155,
                viewportStart = viewportStart,
                viewportEnd = viewportEnd,
            ),
        )
    }

    @Test
    fun focusRevealLeavesAFullyVisibleRowAlone() {
        // Regression: the Android default bring-into-view spec pulls a focused
        // tile to 30% of the viewport (here it would ask for -80), so the
        // focus request added a second animation on top of the row tween.
        assertEquals(
            0f,
            GridBringIntoViewSpec.calculateScrollDistance(
                offset = 100f,
                size = 100f,
                containerSize = 600f,
            ),
        )
    }

    @Test
    fun focusRevealScrollsOnlyTheClippedPart() {
        assertEquals(
            40f,
            GridBringIntoViewSpec.calculateScrollDistance(
                offset = -40f,
                size = 100f,
                containerSize = 600f,
            ),
        )
        assertEquals(
            -30f,
            GridBringIntoViewSpec.calculateScrollDistance(
                offset = 530f,
                size = 100f,
                containerSize = 600f,
            ),
        )
    }

    @Test
    fun aRowPastTheComposedWindowLandsAtTheEdgeNotAtTheTop() {
        // Regression: the focused row below the composed window was pinned to
        // the viewport top, so one Down at the bottom edge jumped several rows.
        assertEquals(
            194f,
            offscreenRowDelta(
                anchorIndex = 2,
                anchorTop = 770,
                anchorSize = 240,
                pitch = 264f,
                focusedRow = 3,
                viewportStart = 0,
                viewportEnd = 1080,
            ),
        )
    }

    @Test
    fun aRowAboveTheComposedWindowIsRevealedAtTheTop() {
        assertEquals(
            -264f,
            offscreenRowDelta(
                anchorIndex = 3,
                anchorTop = 0,
                anchorSize = 240,
                pitch = 264f,
                focusedRow = 2,
                viewportStart = 0,
                viewportEnd = 1080,
            ),
        )
    }
}
