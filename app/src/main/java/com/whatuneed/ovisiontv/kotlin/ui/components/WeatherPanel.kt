package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.Weather
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import com.whatuneed.ovisiontv.kotlin.ui.SurfaceEasing
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration

// The Java panel was 280×324 on a 960×540 canvas; the Kotlin launcher doubles
// every value to its 1920×1080 points, keeping the Control Center's top margin.
private const val WEATHER_PANEL_LEFT = 48f
private const val WEATHER_PANEL_TOP = 144f
private const val WEATHER_PANEL_WIDTH = 560f
private const val WEATHER_PANEL_HEIGHT = 648f
private const val WEATHER_PANEL_PAD = 30f
private const val WEATHER_CONTENT_WIDTH = WEATHER_PANEL_WIDTH - WEATHER_PANEL_PAD * 2
private const val WEATHER_PANEL_DROP = 20f
private const val WEATHER_PANEL_SCRIM_ALPHA = 0.45f
// The panel shares the launcher's page entrance; only its faster exit is local.
private const val WEATHER_EXIT_MS = 190

/**
 * The full forecast opened from the weather capsule: current conditions with
 * wind and humidity, six hours and five days, and the source with the fetch
 * time. It is as wide as the Control Center and hangs from the same top-left
 * corner, so the two panels read as one family. Back closes it and returns the
 * cursor to the capsule.
 */
@Composable
internal fun WeatherPanel(
    scale: TvScale,
    data: Weather.Data?,
    stale: Boolean,
    reduceMotion: Boolean,
    onClose: () -> Unit,
) {
    val russian = LocalConfiguration.current.locales[0]?.language == "ru"
    val requester = remember { FocusRequester() }
    var closing by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val surfaceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            motionDuration(
                if (closing) WEATHER_EXIT_MS else TvOs.SURFACE_ENTER_MS,
                reduceMotion,
            ),
            easing = SurfaceEasing,
        ),
        label = "weatherAlpha",
    )
    val drop by animateFloatAsState(
        targetValue = if (entered) 0f else -WEATHER_PANEL_DROP,
        animationSpec = tween(
            motionDuration(
                if (closing) WEATHER_EXIT_MS else TvOs.SURFACE_ENTER_MS,
                reduceMotion,
            ),
            easing = SurfaceEasing,
        ),
        label = "weatherDrop",
    )

    fun requestClose() {
        if (closing) return
        closing = true
    }

    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        entered = false
        kotlinx.coroutines.delay(motionDuration(WEATHER_EXIT_MS, reduceMotion).toLong())
        onClose()
    }

    LaunchedEffect(Unit) { requester.requestFocus() }
    BackHandler { requestClose() }

    val title = stringResource(R.string.weather_title)
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(requester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || closing) return@onKeyEvent true
                // The panel is read-only, but it owns the focus: OK must do
                // something rather than swallow the press. It closes the
                // forecast, the same way Back does.
                when (event.key) {
                    Key.Back, Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                        requestClose()

                    else -> Unit
                }
                true
            }
            .focusable(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = surfaceAlpha * WEATHER_PANEL_SCRIM_ALPHA }
                .background(Color.Black),
        )

        val shape = RoundedCornerShape(scale.dp(TvRadius.panelWide))
        Box(
            Modifier
                .padding(start = scale.dp(WEATHER_PANEL_LEFT), top = scale.dp(WEATHER_PANEL_TOP))
                .size(scale.dp(WEATHER_PANEL_WIDTH), scale.dp(WEATHER_PANEL_HEIGHT))
                .graphicsLayer {
                    alpha = surfaceAlpha
                    translationY = drop * scale.factor * density
                }
                .background(
                    brush = Brush.verticalGradient(
                        listOf(TvInk.capsuleTop, TvInk.capsuleBottom),
                    ),
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = shape,
                )
                .semantics { paneTitle = title },
        ) {
            if (data == null) {
                Text(
                    text = stringResource(R.string.weather_loading),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = scale.sp(TvType.body),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                PanelContent(scale = scale, data = data, stale = stale, russian = russian)
            }
        }
    }
}

@Composable
private fun PanelContent(
    scale: TvScale,
    data: Weather.Data,
    stale: Boolean,
    russian: Boolean,
) {
    val place = data.place.ifEmpty { stringResource(R.string.weather_my_location) }
    val condition = weatherDescription(data.code, data.day)
    val high = stringResource(R.string.weather_high)
    val low = stringResource(R.string.weather_low)
    val windUnit = Weather.windUnitName(data.windUnit, russian)
    val detail = buildList {
        add(stringResource(R.string.weather_feels_like, data.feels))
        add(stringResource(R.string.weather_wind, data.wind, windUnit))
        if (data.humidity > 0) add(stringResource(R.string.weather_humidity, data.humidity))
    }.joinToString(" · ")

    // The detail line is assembled from three translated parts, so it changes
    // width with the language and units: shrink it a little rather than cut the
    // humidity off the end.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxDetailWidth = with(density) { scale.dp(WEATHER_CONTENT_WIDTH).toPx() }
    val detailSize = remember(detail, maxDetailWidth, scale.factor) {
        var size = TvType.caption
        while (size > 16f) {
            val layout = measurer.measure(
                text = AnnotatedString(detail),
                style = TextStyle(
                    fontSize = scale.sp(size),
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (layout.size.width <= maxDetailWidth) break
            size -= 0.5f
        }
        size
    }

    val source = Weather.sourceName(data.source)
    val fetched = if (data.fetchedAt > 0) "  ·  " + weatherTimeLabel(data.fetchedAt) else ""
    val footer = source + fetched +
        if (stale) "  ·  " + stringResource(R.string.weather_outdated_footer) else ""

    PanelText(place, 20f, TvType.body, FontWeight.SemiBold, 1f, scale)
    PanelText(
        text = "${data.temp}°",
        y = 48f,
        size = TvType.hero,
        weight = FontWeight.Normal,
        alpha = 1f,
        scale = scale,
        letterSpacing = -0.03f,
    )
    WeatherIcon(
        code = data.code,
        day = data.day,
        iconSize = scale.dp(68f),
        modifier = Modifier.offset(x = scale.dp(WEATHER_CONTENT_WIDTH + WEATHER_PANEL_PAD - 68f), y = scale.dp(16f)),
    )
    PanelText(condition, 130f, TvType.callout, FontWeight.Medium, 0.85f, scale)
    PanelText(
        text = stringResource(R.string.weather_high_low, high, data.max, low, data.min),
        y = 130f,
        size = TvType.callout,
        weight = FontWeight.Medium,
        alpha = 0.85f,
        scale = scale,
        width = WEATHER_CONTENT_WIDTH,
        align = TextAlign.End,
    )
    PanelText(detail, 166f, detailSize, FontWeight.Medium, 0.55f, scale)

    PanelHairline(204f, scale)
    PanelText(stringResource(R.string.weather_hourly), 208f, TvType.caption, FontWeight.SemiBold, 0.55f, scale)

    val hours = data.hours.take(6)
    val cellWidth = WEATHER_CONTENT_WIDTH / hours.size.coerceAtLeast(1)
    hours.forEachIndexed { index, hour ->
        val x = WEATHER_PANEL_PAD + cellWidth * index
        val first = index == 0
        PanelText(
            text = weatherHourLabel(hour.time, first),
            x = x,
            y = 234f,
            size = TvType.caption,
            weight = FontWeight.SemiBold,
            alpha = if (first) 1f else 0.8f,
            scale = scale,
            width = cellWidth,
            align = TextAlign.Center,
        )
        WeatherIcon(
            code = hour.code,
            day = hour.day,
            iconSize = scale.dp(36f),
            modifier = Modifier.offset(
                x = scale.dp(x + cellWidth / 2f - 18f),
                y = scale.dp(262f),
            ),
        )
        PanelText(
            text = "${hour.temp}°",
            x = x,
            y = 302f,
            size = TvType.callout,
            weight = FontWeight.SemiBold,
            alpha = 1f,
            scale = scale,
            width = cellWidth,
            align = TextAlign.Center,
        )
    }

    val days = data.days.take(5)
    if (days.isNotEmpty()) {
        PanelHairline(342f, scale)
        PanelText(stringResource(R.string.weather_daily), 346f, TvType.caption, FontWeight.SemiBold, 0.55f, scale)
        PanelText(
            text = low,
            y = 348f,
            size = TvType.caption,
            weight = FontWeight.Medium,
            alpha = 0.45f,
            scale = scale,
            width = WEATHER_CONTENT_WIDTH - 116f,
            align = TextAlign.End,
        )
        PanelText(
            text = high,
            y = 348f,
            size = TvType.caption,
            weight = FontWeight.Medium,
            alpha = 0.45f,
            scale = scale,
            width = WEATHER_CONTENT_WIDTH,
            align = TextAlign.End,
        )
        days.forEachIndexed { index, day ->
            val top = 380f + 46f * index
            PanelText(
                text = weatherDayLabel(day.date, index == 0, russian),
                x = WEATHER_PANEL_PAD,
                y = top + 10f,
                size = TvType.callout,
                weight = FontWeight.SemiBold,
                alpha = if (index == 0) 1f else 0.85f,
                scale = scale,
                width = 220f,
            )
            WeatherIcon(
                code = day.code,
                day = true,
                iconSize = scale.dp(34f),
                modifier = Modifier.offset(
                    x = scale.dp(WEATHER_PANEL_WIDTH / 2f - 17f),
                    y = scale.dp(top + 5f),
                ),
            )
            PanelText(
                text = "${day.min}°",
                y = top + 10f,
                size = TvType.callout,
                weight = FontWeight.Normal,
                alpha = 0.6f,
                scale = scale,
                width = WEATHER_CONTENT_WIDTH - 116f,
                align = TextAlign.End,
            )
            PanelText(
                text = "${day.max}°",
                y = top + 10f,
                size = TvType.callout,
                weight = FontWeight.SemiBold,
                alpha = 1f,
                scale = scale,
                width = WEATHER_CONTENT_WIDTH,
                align = TextAlign.End,
            )
        }
    }

    PanelText(footer, 612f, TvType.caption, FontWeight.Medium, 0.38f, scale)
}

@Composable
private fun PanelText(
    text: String,
    y: Float,
    size: Float,
    weight: FontWeight,
    alpha: Float,
    scale: TvScale,
    x: Float = WEATHER_PANEL_PAD,
    width: Float? = null,
    align: TextAlign = TextAlign.Start,
    letterSpacing: Float = 0f,
) {
    val modifier = Modifier
        .offset(x = scale.dp(x), y = scale.dp(y))
        .then(if (width != null) Modifier.width(scale.dp(width)) else Modifier)
    Text(
        text = text,
        color = Color.White.copy(alpha = alpha),
        fontSize = scale.sp(size),
        fontWeight = weight,
        letterSpacing = (letterSpacing * size).sp,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun PanelHairline(y: Float, scale: TvScale) {
    Box(
        Modifier
            .offset(x = scale.dp(WEATHER_PANEL_PAD), y = scale.dp(y))
            .width(scale.dp(WEATHER_CONTENT_WIDTH))
            .height(scale.dp(1.6f))
            .background(Color.White.copy(alpha = 0.2f)),
    )
}
