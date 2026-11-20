package app.aure.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import app.aure.tv.ui.WeatherInk

private val Sun = WeatherInk.sun
private val Moon = WeatherInk.moon
private val Cloud = Color.White
private val CloudBack = WeatherInk.cloud
private val Rain = WeatherInk.rain

/**
 * Apple-style multicolour weather symbols drawn in code: a yellow sun or white
 * moon, white clouds with a grey one behind, cyan rain and a yellow bolt.
 * Keyed by the WMO weather code the engine normalises every source to.
 */
@Composable
internal fun WeatherIcon(code: Int, day: Boolean, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension
        // The gap where a cloud overlaps the sun or the cloud behind is cut
        // out of one layer, so it never punches through the glass underneath.
        drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        when (kind(code)) {
            "clear" -> if (day) sun(s * 0.5f, s * 0.5f, s * 0.22f) else moon(s * 0.5f, s * 0.5f, s * 0.3f)
            "partly" -> {
                if (day) sun(s * 0.64f, s * 0.36f, s * 0.16f) else moon(s * 0.64f, s * 0.36f, s * 0.2f)
                cloud(s * 0.06f, s * 0.4f, s * 0.7f, back = false)
            }

            "cloudy" -> {
                cloud(s * 0.3f, s * 0.2f, s * 0.62f, back = true)
                cloud(s * 0.06f, s * 0.36f, s * 0.74f, back = false)
            }

            "fog" -> {
                cloud(s * 0.14f, s * 0.14f, s * 0.72f, back = false)
                drawLine(WeatherInk.cloudHighlight, Offset(s * 0.16f, s * 0.7f), Offset(s * 0.84f, s * 0.7f), s * 0.06f, StrokeCap.Round)
                drawLine(WeatherInk.cloudHighlight, Offset(s * 0.26f, s * 0.84f), Offset(s * 0.74f, s * 0.84f), s * 0.06f, StrokeCap.Round)
            }

            "drizzle" -> {
                cloud(s * 0.12f, s * 0.12f, s * 0.76f, back = false)
                drops(s, len = 0.1f)
            }

            "rain" -> {
                cloud(s * 0.12f, s * 0.12f, s * 0.76f, back = false)
                drops(s, len = 0.17f)
            }

            "snow" -> {
                cloud(s * 0.12f, s * 0.12f, s * 0.76f, back = false)
                for (dot in listOf(
                    Offset(0.32f, 0.74f), Offset(0.52f, 0.82f), Offset(0.72f, 0.74f),
                    Offset(0.42f, 0.92f), Offset(0.62f, 0.94f),
                )) {
                    drawCircle(Color.White, s * 0.045f, Offset(s * dot.x, s * dot.y))
                }
            }

            "thunder" -> {
                cloud(s * 0.12f, s * 0.1f, s * 0.76f, back = false)
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.56f)
                    lineTo(s * 0.38f, s * 0.78f)
                    lineTo(s * 0.5f, s * 0.78f)
                    lineTo(s * 0.43f, s * 0.97f)
                    lineTo(s * 0.64f, s * 0.7f)
                    lineTo(s * 0.52f, s * 0.7f)
                    lineTo(s * 0.6f, s * 0.56f)
                    close()
                }
                drawPath(bolt, Sun)
            }

            else -> cloud(s * 0.12f, s * 0.24f, s * 0.76f, back = false)
        }
        drawContext.canvas.restore()
    }
}

internal fun kind(code: Int): String = when {
    code == 0 -> "clear"
    code == 1 || code == 2 -> "partly"
    code == 3 -> "cloudy"
    code == 45 || code == 48 -> "fog"
    code in 51..57 -> "drizzle"
    (code in 61..67) || (code in 80..82) -> "rain"
    (code in 71..77) || code == 85 || code == 86 -> "snow"
    code >= 95 -> "thunder"
    else -> "cloudy"
}

private fun DrawScope.sun(cx: Float, cy: Float, radius: Float) {
    drawCircle(Sun, radius, Offset(cx, cy))
    val stroke = radius * 0.28f
    for (i in 0 until 8) {
        val angle = Math.PI / 4 * i
        val x = Math.cos(angle).toFloat()
        val y = Math.sin(angle).toFloat()
        drawLine(
            Sun,
            Offset(cx + x * radius * 1.45f, cy + y * radius * 1.45f),
            Offset(cx + x * radius * 1.85f, cy + y * radius * 1.85f),
            stroke,
            StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(cx: Float, cy: Float, radius: Float) {
    val full = Path().apply { addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius)) }
    val bite = Path().apply {
        val r = radius * 0.82f
        val bx = cx + radius * 0.55f
        val by = cy - radius * 0.4f
        addOval(Rect(bx - r, by - r, bx + r, by + r))
    }
    val crescent = Path().apply { op(full, bite, PathOperation.Difference) }
    drawPath(crescent, Moon)
}

/** A cloud [w] wide with its top-left at (l, t); [back] draws the grey one behind. */
private fun DrawScope.cloud(l: Float, t: Float, w: Float, back: Boolean) {
    val h = w * 0.6f
    fun circle(cx: Float, cy: Float, r: Float): Path =
        Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }

    var path = circle(l + w * 0.56f, t + h * 0.44f, w * 0.27f)
    path = Path().apply { op(path, circle(l + w * 0.3f, t + h * 0.62f, w * 0.19f), PathOperation.Union) }
    path = Path().apply { op(path, circle(l + w * 0.8f, t + h * 0.68f, w * 0.16f), PathOperation.Union) }
    val body = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(l + w * 0.12f, t + h * 0.55f, l + w * 0.96f, t + h * 0.95f),
                CornerRadius(h * 0.2f),
            ),
        )
    }
    path = Path().apply { op(path, body, PathOperation.Union) }

    // A thin gap where the cloud overlaps the sun, moon or a cloud behind.
    drawPath(
        path,
        Color.Black,
        style = Stroke(width = w * 0.07f, cap = StrokeCap.Round),
        blendMode = BlendMode.Clear,
    )
    drawPath(path, if (back) CloudBack else Cloud)
}

private fun DrawScope.drops(s: Float, len: Float) {
    for (i in 0 until 3) {
        val x = s * (0.34f + i * 0.17f)
        val y = s * 0.68f
        drawLine(
            Rain,
            Offset(x, y),
            Offset(x - s * len * 0.4f, y + s * len),
            s * 0.065f,
            StrokeCap.Round,
        )
    }
}
