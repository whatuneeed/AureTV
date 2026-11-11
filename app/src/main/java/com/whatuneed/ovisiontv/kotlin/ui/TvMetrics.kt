package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home geometry measured on tvOS 27 in logical points (1920x1080).
 * Sources: TVOS27_RESEARCH/02_HOME_SCREEN.md, 03_FOCUS_ENGINE.md,
 * 06_TOP_SHELF.md, 13_VISUAL_DESIGN_SYSTEM.md.
 */
object TvOs {
    const val SCREEN_WIDTH = 1920f
    const val SCREEN_HEIGHT = 1080f

    const val COLUMNS = 6
    // Content margin for the grid, the shelf and settings (UX-25).
    const val SAFE_AREA = 90f

    // The dock is a full-width band: on tvOS its edge sits closer to the screen
    // than the grid edge, so it bleeds this many points into both margins. The
    // two roles stay related through SAFE_AREA instead of two unrelated numbers.
    const val DOCK_BLEED = 40f
    const val DOCK_X = SAFE_AREA - DOCK_BLEED
    const val DOCK_Y = 784f
    const val DOCK_WIDTH = SCREEN_WIDTH - DOCK_X * 2f
    const val DOCK_HEIGHT = 230f
    const val DOCK_RADIUS = 70f
    const val DOCK_FOCUS_SHIFT = 30f
    // 13_VISUAL_DESIGN_SYSTEM dock.focusGrowth: x 50→39, w 1820→1831.
    const val DOCK_FOCUS_GROWTH = 11f

    const val ICON_WIDTH = 250f
    const val ICON_HEIGHT = 150f
    const val ICON_GAP = 48f
    const val ICON_RADIUS = 30f
    const val ICON_COLUMN_PITCH = ICON_WIDTH + ICON_GAP
    const val ROW_TOP = 824f
    const val ROW_PITCH = 260f

    const val FOCUS_SCALE = 306f / ICON_WIDTH
    const val FOCUSED_WIDTH = 306f
    const val FOCUSED_HEIGHT = 184f

    const val SHADOW_WIDTH = 268f
    const val SHADOW_HEIGHT = 169f
    const val SHADOW_OFFSET_Y = 9.5f

    // 15_ICONS_AND_ARTWORK §3: the focused highlight asset is 300×300 pt and is
    // deliberately larger than the 250×150 tile.
    const val SPECULAR_SIZE = 300f

    const val SCROLL_DURATION_MS = 300

    // M1 focusDuration is UNKNOWN in tvOS; 180 ms is the project value. Every
    // focus surface reads this one constant so screens cannot drift apart.
    const val FOCUS_MS = 180
    const val HIDE_GRID_SHIFT = 340f

    // One page-level entrance for every gate that takes the whole screen
    // (profile picker, avatar picker, PIN pad). It is a single slide-fade, never
    // a staggered reveal: the page has to read as already there.
    const val SURFACE_ENTER_MS = 240
    const val SURFACE_SLIDE = 26f
}

/** Cubic easing shared by the whole-page entrances above. */
val SurfaceEasing = CubicBezierEasing(0.22f, 0f, 0.36f, 1f)

/**
 * Travel easing for surfaces that move a long way: the folder zoom and the
 * launch hand-off. It leaves fast and lands slowly, so the eye follows the
 * tile instead of the background.
 */
val TravelEasing = CubicBezierEasing(0.3825f, 0.091f, 0.98f, 0.785f)

/** Artwork crossfade: the new still arrives evenly, without a visible snap. */
val ArtEasing = CubicBezierEasing(0.2f, 0f, 0.67f, 1f)

/** Saver exit runs slightly quicker than its entrance. */
val SaverExitEasing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)
val SaverAlphaEasing = CubicBezierEasing(0.25f, 0f, 0.4f, 1f)

@Immutable
class TvScale(val factor: Float) {
    fun dp(points: Float): Dp = (points * factor).dp
    fun sp(points: Float): TextUnit = (points * factor).sp
}

/**
 * Fits the 1920x1080 design canvas into a window measured in dp (UX-18).
 * The smaller axis wins: a 21:9 panel keeps the design size and gains margins
 * instead of stretching past its height, a 4:3 window shrinks the canvas
 * instead of clipping the dock. A 16:9 window reduces to the old width rule.
 * A window without a bounded size (a preview, a test host) keeps factor 1.
 */
internal fun tvCanvasFactor(widthDp: Float, heightDp: Float): Float {
    if (!widthDp.isFinite() || !heightDp.isFinite() || widthDp <= 0f || heightDp <= 0f) {
        return 1f
    }
    return minOf(widthDp / TvOs.SCREEN_WIDTH, heightDp / TvOs.SCREEN_HEIGHT)
}

// UX-19: past 2× the pixels of a 320×180 store banner the magnified logo only
// reads as a blur, so the wallpaper is the honest backdrop instead.
internal const val BANNER_MAX_UPSCALE = 2

/**
 * Whether an app banner may cover the shelf at no more than [BANNER_MAX_UPSCALE].
 * [pxPerPoint] is the canvas factor times the screen density, so the limit is
 * measured in the same pixels as [widthPx] on any panel from HD to 4K.
 */
internal fun bannerIsHero(widthPx: Int, pxPerPoint: Float): Boolean =
    widthPx * BANNER_MAX_UPSCALE >= TvOs.SCREEN_WIDTH * pxPerPoint

// TvCanvas provides the scale that matches the window it fitted. Screens hosted
// without a canvas (unit tests, previews) fall back to the configuration.
internal val LocalTvScale = compositionLocalOf<TvScale?> { null }

@Composable
fun rememberTvScale(): TvScale {
    LocalTvScale.current?.let { return it }
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp.toFloat()
    val height = configuration.screenHeightDp.toFloat()
    return remember(width, height) { TvScale(tvCanvasFactor(width, height)) }
}

/**
 * Reduce Motion keeps every surface and every hand-off, but removes the travel:
 * the destination state appears at once instead of the element sliding to it.
 * Durations stay in one place so a new screen cannot forget the setting.
 */
internal fun motionDuration(ms: Int, reduceMotion: Boolean): Int = if (reduceMotion) 0 else ms
