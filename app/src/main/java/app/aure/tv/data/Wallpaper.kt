package app.aure.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * The colour field behind a surface, ported from the Java launcher's
 * `makeWallpaperBase` (`MainActivity.java:1251`). The Kotlin launcher has no
 * wallpaper on Home — it paints one fixed dark gradient — so this exists only
 * as the backdrop decoration of the first-run wizard: a page is shown over the
 * same aurora field Java showed, which is what makes the wizard read as one
 * theme.
 *
 * Nothing here is invented: the five palettes, the five radial blobs and the
 * three alpha stops are the measured Java values. The image is rendered once at
 * screen resolution (capped at 2560 wide so a 4K panel never shows the gradient
 * as rings) and must be built off the main thread — a full-screen radial field
 * is several milliseconds of work.
 */
internal object Wallpaper {
    /** The style Java used when no choice had been made yet. */
    const val DEFAULT = "aurora"

    /** Keys in Java's own order; kept for a future Settings picker. */
    val KEYS = listOf("aurora", "midnight", "sunset", "ocean", "graphite")

    /**
     * Centre x, centre y and radius as fractions of the width/height. Identical
     * for every style: Java varied only the colours, never the placement.
     */
    private val BLOBS = arrayOf(
        floatArrayOf(0.14f, 0.18f, 0.62f),
        floatArrayOf(0.88f, 0.22f, 0.50f),
        floatArrayOf(0.72f, 0.92f, 0.55f),
        floatArrayOf(0.20f, 0.95f, 0.42f),
        floatArrayOf(0.50f, 0.45f, 0.38f),
    )

    private data class Style(val base: Int, val colors: IntArray)

    private val STYLES = mapOf(
        "aurora" to Style(
            0xFF080C1E.toInt(),
            intArrayOf(
                0xFF4B2DB0.toInt(), 0xFF0B7F8C.toInt(), 0xFFB02F6E.toInt(),
                0xFFC27A2C.toInt(), 0xFF2150C0.toInt(),
            ),
        ),
        "midnight" to Style(
            0xFF04060F.toInt(),
            intArrayOf(
                0xFF1B2A6B.toInt(), 0xFF0F3D63.toInt(), 0xFF2B1B5E.toInt(),
                0xFF14254F.toInt(), 0xFF22306E.toInt(),
            ),
        ),
        "sunset" to Style(
            0xFF140812.toInt(),
            intArrayOf(
                0xFFB0412E.toInt(), 0xFF8E2C6E.toInt(), 0xFFD08A2E.toInt(),
                0xFF6B2A8E.toInt(), 0xFFC0563A.toInt(),
            ),
        ),
        "ocean" to Style(
            0xFF031418.toInt(),
            intArrayOf(
                0xFF0C6E86.toInt(), 0xFF0B8C76.toInt(), 0xFF1A4FA0.toInt(),
                0xFF0E5E6E.toInt(), 0xFF2A7FB0.toInt(),
            ),
        ),
        "graphite" to Style(
            0xFF0C0C0E.toInt(),
            intArrayOf(
                0xFF3A3A42.toInt(), 0xFF2A2C34.toInt(), 0xFF4A4A52.toInt(),
                0xFF26262C.toInt(), 0xFF34363E.toInt(),
            ),
        ),
    )

    /** Screen-sized field, capped at 2560 wide exactly as Java capped it. */
    fun render(context: Context, style: String): Bitmap {
        val metrics = context.resources.displayMetrics
        val sourceWidth = metrics.widthPixels.coerceAtLeast(1)
        val width = sourceWidth.coerceIn(192, 2560)
        val height = (width.toFloat() * metrics.heightPixels / sourceWidth)
            .toInt().coerceAtLeast(1)
        return render(style, width, height)
    }

    /** Explicit size: used by tests and by any caller that already knows them. */
    fun render(style: String, width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val chosen = STYLES[style] ?: STYLES.getValue(DEFAULT)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(chosen.base)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        for (i in chosen.colors.indices) {
            val blob = BLOBS[i]
            val color = chosen.colors[i]
            // Java masked each stop down from the style colour: the RGB stays
            // the blob's, the alpha falls 0xD9 → 0x59 → 0x00 so the blobs melt
            // into the base instead of ending on a hard edge.
            paint.shader = RadialGradient(
                blob[0] * w,
                blob[1] * h,
                blob[2] * w,
                intArrayOf(
                    color and 0xD9FFFFFF.toInt(),
                    color and 0x59FFFFFF.toInt(),
                    color and 0x00FFFFFF,
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        return bitmap
    }
}
