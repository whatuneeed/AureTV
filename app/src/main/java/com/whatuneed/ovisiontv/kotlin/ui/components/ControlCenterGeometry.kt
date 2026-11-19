package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.ui.input.key.Key
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import kotlin.math.abs

internal const val CC_PANEL_WIDTH = 560f
internal const val CC_TILE_WIDTH = 272f
internal const val CC_TILE_HEIGHT = 96f
internal const val CC_TILE_GAP = 16f
internal const val CC_CIRCLE = 80f

internal const val LEAD_ID = "lead"
internal const val SOUND_ID = "left:1"
internal const val CC_TALL_RADIUS = TvRadius.panelWide

// One geometry source for the layout and for focus navigation: the hand-written
// index graph routed arrows diagonally (Down from Wi-Fi landed on the saver
// circle), so moves are resolved against the real tile rectangles instead.
internal data class CcFocusRect(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

internal data class CcGeometry(
    val powerHeight: Float,
    val soundY: Float,
    val circleY: Float,
    val panelHeight: Float,
)

internal fun ccGeometry(rightCount: Int, circleCount: Int): CcGeometry {
    val rowsCount = maxOf(rightCount, 2)
    val rowsHeight = rowsCount * CC_TILE_HEIGHT + (rowsCount - 1) * CC_TILE_GAP
    val powerHeight = (rowsCount - 1) * CC_TILE_HEIGHT + (rowsCount - 2) * CC_TILE_GAP
    val soundY = powerHeight + CC_TILE_GAP
    return CcGeometry(
        powerHeight = powerHeight,
        soundY = soundY,
        circleY = rowsHeight + CC_TILE_GAP,
        panelHeight = rowsHeight + if (circleCount == 0) 0f else CC_TILE_GAP + CC_CIRCLE,
    )
}

internal fun ccCircleX(index: Int, count: Int): Float =
    if (count <= 1) 0f else index * (CC_PANEL_WIDTH - CC_CIRCLE) / (count - 1f)

internal fun ccFocusRects(rightIds: List<String>, circleIds: List<String>): List<CcFocusRect> {
    val geometry = ccGeometry(rightIds.size, circleIds.size)
    return buildList {
        add(CcFocusRect(LEAD_ID, 0f, 0f, CC_TILE_WIDTH, geometry.powerHeight))
        add(CcFocusRect(SOUND_ID, 0f, geometry.soundY, CC_TILE_WIDTH, geometry.soundY + CC_TILE_HEIGHT))
        rightIds.forEachIndexed { index, id ->
            val y = index * (CC_TILE_HEIGHT + CC_TILE_GAP)
            add(CcFocusRect(id, CC_TILE_WIDTH + CC_TILE_GAP, y, CC_PANEL_WIDTH, y + CC_TILE_HEIGHT))
        }
        circleIds.forEachIndexed { index, id ->
            val x = ccCircleX(index, circleIds.size)
            add(CcFocusRect(id, x, geometry.circleY, x + CC_CIRCLE, geometry.circleY + CC_CIRCLE))
        }
    }
}

// FocusFinder rules: the candidate must lie in the pressed direction and share
// the perpendicular beam with the source — a corner touch must not steal a
// vertical move. Nearest edge wins; ties go to the closer cross-axis center,
// then to layout order. No candidate means the focus stays put.
internal fun nextCcFocusId(currentId: String, direction: Key, rects: List<CcFocusRect>): String? {
    val current = rects.firstOrNull { it.id == currentId } ?: return null
    val horizontal = direction == Key.DirectionLeft || direction == Key.DirectionRight
    val forward = direction == Key.DirectionRight || direction == Key.DirectionDown
    var best: CcFocusRect? = null
    var bestMajor = Float.MAX_VALUE
    var bestCross = Float.MAX_VALUE
    for (candidate in rects) {
        if (candidate.id == currentId) continue
        val inDirection: Boolean
        val sharesBeam: Boolean
        val major: Float
        val cross: Float
        if (horizontal) {
            inDirection = if (forward) candidate.left >= current.right else candidate.right <= current.left
            sharesBeam = candidate.top < current.bottom && candidate.bottom > current.top
            major = if (forward) candidate.left - current.right else current.left - candidate.right
            cross = abs(candidate.centerY - current.centerY)
        } else {
            inDirection = if (forward) candidate.top >= current.bottom else candidate.bottom <= current.top
            sharesBeam = candidate.left < current.right && candidate.right > current.left
            major = if (forward) candidate.top - current.bottom else current.top - candidate.bottom
            cross = abs(candidate.centerX - current.centerX)
        }
        if (!inDirection || !sharesBeam) continue
        if (major < bestMajor || (major == bestMajor && cross < bestCross)) {
            best = candidate
            bestMajor = major
            bestCross = cross
        }
    }
    return best?.id
}

