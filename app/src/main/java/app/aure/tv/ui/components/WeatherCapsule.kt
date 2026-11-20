package app.aure.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.aure.tv.R
import app.aure.tv.data.Weather
import app.aure.tv.ui.LocalReduceMotion
import app.aure.tv.ui.TvFocus
import app.aure.tv.ui.TvInk
import app.aure.tv.ui.TvOs
import app.aure.tv.ui.TvScale
import app.aure.tv.ui.TvLeading
import app.aure.tv.ui.TvType
import app.aure.tv.ui.TvSpace
import app.aure.tv.ui.motionDuration

// The Java card sat at dp(24) from both edges of its 960-wide canvas; the
// Kotlin launcher works in 1920×1080 points, so the same corner is 48 here and
// the pill is as tall as the status capsule on the other side.
internal const val WEATHER_CAPSULE_LEFT = 48f
internal const val WEATHER_CAPSULE_TOP = 48f
internal const val WEATHER_CAPSULE_HEIGHT = 76f
private const val WEATHER_CAPSULE_MIN = 248f
private const val WEATHER_CAPSULE_MAX = 464f
private const val WEATHER_FOCUS_SCALE = 1.04f

/**
 * The current weather in the top-left corner, in the manner of Apple's weather
 * widgets: one flat multicolour symbol, the temperature, then the place (with a
 * location arrow while it follows the connection) over the condition. The pill
 * wraps its content between the Java build's minimum and maximum widths.
 */
@Composable
internal fun WeatherCapsule(
    scale: TvScale,
    data: Weather.Data,
    stale: Boolean,
    manual: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = data.place.ifEmpty { stringResource(R.string.weather_my_location) }
    val condition = buildString {
        if (stale) append(stringResource(R.string.weather_outdated)).append(" · ")
        append(weatherDescription(data.code, data.day))
    }
    val label = stringResource(R.string.weather_a11y, "${data.temp}°", place)
    val reduceMotion = LocalReduceMotion.current
    val grow by animateFloatAsState(
        targetValue = if (focused) WEATHER_FOCUS_SCALE else 1f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "weatherFocus",
    )
    val ring by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "weatherRing",
    )
    val shape = RoundedCornerShape(scale.dp(WEATHER_CAPSULE_HEIGHT / 2f))
    Row(
        modifier = modifier
            .widthIn(
                min = scale.dp(WEATHER_CAPSULE_MIN),
                max = scale.dp(WEATHER_CAPSULE_MAX),
            )
            .height(scale.dp(WEATHER_CAPSULE_HEIGHT))
            // The Java pill grows from its top-left pivot.
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(TvInk.capsuleTop, TvInk.capsuleBottom),
                ),
                shape = shape,
            )
            .border(
                width = TvFocus.capsuleWidth(scale, ring),
                brush = TvFocus.capsuleBrush(ring),
                shape = shape,
            )
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = condition
                role = Role.Button
                onClick(label = label) {
                    onActivate()
                    true
                }
            }
            .padding(horizontal = scale.dp(26f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherIcon(
            code = data.code,
            day = data.day,
            iconSize = scale.dp(42f),
        )
        Spacer(Modifier.width(scale.dp(14f)))
        Text(
            text = "${data.temp}°",
            color = Color.White,
            fontSize = scale.sp(TvType.headline),
            letterSpacing = (-0.03f * 42f).sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.width(scale.dp(18f)))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = place,
                    color = Color.White,
                    fontSize = scale.sp(TvType.callout),
                    // An explicit line height keeps the inherited body style's
                    // 24sp from making the two-line column taller than the pill.
                    lineHeight = scale.sp(TvLeading.capsule),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!manual) {
                    Spacer(Modifier.width(scale.dp(TvSpace.sm)))
                    LocationArrow(size = scale.dp(12f))
                }
            }
            Text(
                text = condition,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = scale.sp(TvType.callout),
                lineHeight = scale.sp(TvLeading.capsuleLine),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Apple's location.fill arrow: a small filled triangle pointing up-right. */
@Composable
private fun LocationArrow(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val arrow = Path().apply {
            moveTo(w, 0f)
            lineTo(0f, h * 0.45f)
            lineTo(w * 0.48f, h * 0.52f)
            lineTo(w * 0.55f, h)
            close()
        }
        drawPath(arrow, Color.White.copy(alpha = 0.9f))
    }
}
