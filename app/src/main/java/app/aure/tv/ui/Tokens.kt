package app.aure.tv.ui

import androidx.compose.ui.graphics.Color

/**
 * Surface inks for every overlay. The values above are the ones the screens
 * already used; the point of the object is that a new panel picks an ink by
 * role instead of inventing a hex. Backgrounds and accents live in
 * [LauncherColors]; these are the layers that sit on top of them.
 */
object TvInk {
    // Sheets: PIN pad, keyboard, folder, menu. One ink, one nominal alpha.
    val panel = Color(0xFF1D1D1F)

    // Text and glyphs drawn on a white focus fill.
    val focusInk = Color(0xFF101014)

    // Dark disc behind the profile picker's avatars.
    val disc = Color(0xFF101012)
    val discFadeTop = Color(0xCC101012)
    val discFadeBottom = Color(0xE6101012)

    // Glass capsules: status, weather, Control Center bar.
    val capsuleTop = Color(0xE01F1F22)
    val capsuleBottom = Color(0xD918181B)

    // Saver staging and its preview tiles.
    val saver = Color(0xFF0B0E12)
    val previewTop = Color(0xFF1A1E24)
    val previewBottom = Color(0xFF0E1116)

    // Dock band on Home.
    val dockTop = Color(0xFF0C0C0E)
    val dockBottom = Color(0xFF141417)

    // Selected settings row.
    val rowTop = Color(0xFF31353C)
    val rowBottom = Color(0xFF14161A)

    val shadow = Color(0x99000000)

    // Wizard backdrop: three-stop veil over the setup wallpaper, darkest at
    // the bottom where the buttons sit.
    val veilTop = Color(0x99000000)
    val veilMiddle = Color(0x66000000)
    val veilBottom = Color(0xB3000000)

    // Full-screen veils, by the weight of the surface they cover.
    val scrimModal = Color.Black.copy(alpha = 0.65f)
    val scrimFolder = Color.Black.copy(alpha = 0.55f)
    val scrimMenu = Color.Black.copy(alpha = 0.30f)

    // One hairline and one glass fill for every sheet edge and key cap.
    val hairline = Color.White.copy(alpha = 0.10f)
    val hairlineStrong = Color.White.copy(alpha = 0.16f)
    val glassFill = Color.White.copy(alpha = 0.10f)
}

/** Weather glyph palette; the only place these five tints are allowed. */
object WeatherInk {
    val sun = Color(0xFFFFD60A)
    val moon = Color(0xFFF2F2F7)
    val cloud = Color(0xFFA1A7B3)
    val rain = Color(0xFF5AC8FA)
    val cloudHighlight = Color(0xCCE5E5EA)
}

/** Corner radii by role, in design points before [TvScale]. */
object TvRadius {
    const val panel = 40f
    const val panelWide = 48f
    const val panelFolder = 60f
    const val menu = 28f
    const val field = 24f
    const val key = 24f
    const val result = 20f
    const val row = 18f
    const val keyCompact = 14f
}

/**
 * Type scale, in design points at 1920×1080. Call sites wrap a role in
 * `TvScale.sp`; no screen picks its own size. Roles follow the size they hold
 * on a TV: body is 26, not a phone's 16, because the viewer sits metres away.
 * Emoji are optical sizes, not text: a glyph needs its own box to look even
 * beside a 26 pt label.
 */
object TvType {
    const val display = 128f
    const val hero = 72f
    const val largeTitle = 56f
    const val title = 48f
    const val headline = 40f
    const val subhead = 32f
    const val body = 26f
    const val callout = 22f
    const val caption = 18f

    const val glyphHero = 150f
    const val glyphLarge = 104f
    const val glyph = 62f
}

/** Gaps and paddings by role, in design points before [TvScale]. */
object TvSpace {
    const val hairline = 1f
    const val xs = 4f
    const val sm = 8f
    const val md = 12f
    const val lg = 16f
    const val xl = 24f
    const val xxl = 32f
    const val huge = 48f
}

/**
 * Line heights, in design points before [TvScale]. They do not follow one
 * ratio: a list row keeps its fixed height, a shelf title may breathe, and the
 * two lines of the weather pill are tightened so the capsule does not grow.
 * A surface takes the leading of its role next to the [TvType] size it uses.
 */
object TvLeading {
    const val row = 38f // list row title, beside [TvType.subhead]
    const val rowDetail = 28f // list row detail, beside [TvType.callout]
    const val shelfTitle = 54f // shelf app name, beside [TvType.title]
    const val infoTitle = 40f // focused card title, beside [TvType.subhead]
    const val infoSubtitle = 24f // card subtitle, beside [TvType.callout]
    const val capsule = 27f // place name in the weather pill
    const val capsuleLine = 23f // condition under the place name
}
