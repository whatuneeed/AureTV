package app.aure.tv.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The focus ring contract (UX-13). A control that shows focus as a ring picks
 * a role here; the role owns the stroke width and the alpha ramp, so the same
 * pill cannot look different on Home and in Control Center.
 *
 * The launcher has three focus modes, and every surface belongs to one:
 *  - ring — glass capsules and discs: a white stroke, faint at rest;
 *  - fill — key caps and list rows turn solid white with [TvInk.focusInk] text;
 *  - edge — Home tiles light their own rim (IconFocusLight).
 * Sheet hairlines ([TvInk.hairline]) and settings selection markers are not
 * focus rings; they stay with their components.
 */
object TvFocus {
    // Capsule role: status and weather pills. The stroke thickens with focus
    // instead of fading in, which is how the Java pills behaved.
    const val CAPSULE_STROKE = 2f
    const val CAPSULE_GROWTH = 2f
    private const val CAPSULE_TOP_REST = 0.28f
    private const val CAPSULE_TOP_FOCUS = 0.52f
    private const val CAPSULE_BOTTOM_REST = 0.10f
    private const val CAPSULE_BOTTOM_FOCUS = 0.40f

    fun capsuleWidth(scale: TvScale, amount: Float): Dp =
        scale.dp(CAPSULE_STROKE + CAPSULE_GROWTH * amount)

    fun capsuleBrush(amount: Float): Brush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = CAPSULE_TOP_REST + CAPSULE_TOP_FOCUS * amount),
            Color.White.copy(alpha = CAPSULE_BOTTOM_REST + CAPSULE_BOTTOM_FOCUS * amount),
        ),
    )

    // Disc role: profile and avatar circles. They sit on artwork, so the ring
    // stays visible at rest and focus only lifts it to white.
    const val PROFILE_STROKE = 5f
    const val AVATAR_STROKE = 4f
    private const val DISC_REST = 0.18f
    private const val DISC_FOCUS = 0.82f

    fun discAlpha(amount: Float): Float = DISC_REST + DISC_FOCUS * amount

    // Chip role: the folder title field inside the panel.
    const val CHIP_STROKE = 1.5f
    const val CHIP_ALPHA = 0.5f

    // Tile role: the saver preview tile, which has no artwork rim of its own.
    const val TILE_STROKE = 3f
}
