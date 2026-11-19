package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientClock
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientMode
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientPhotoDecoder
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientPhotoPlaylist
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientPixelShift
import com.whatuneed.ovisiontv.kotlin.ambient.RedditImageStore
import com.whatuneed.ovisiontv.kotlin.ambient.RedditWallpaper
import com.whatuneed.ovisiontv.kotlin.data.Weather
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.Locale

internal const val PHOTO_PREVIEW_HEIGHT = 540
internal const val PHOTO_PREVIEW_WIDTH = 960
internal const val PHOTO_FADE_MS = 1_200
internal const val PHOTO_HOLD_MS = 12_000L
internal const val POWER_TICK_MS = 15_000L
internal const val CLOCK_DRIFT_MS = 15_000L
@Composable
internal fun PhotoFace(
    context: Context,
    photos: List<Uri>,
    allowed: Boolean,
    reduceMotion: Boolean,
    preview: Boolean = false,
) {
    val ordered = remember(photos) { AmbientPhotoPlaylist.ordered(photos) }
    var current by remember(ordered) { mutableStateOf<Bitmap?>(null) }
    var previous by remember(ordered) { mutableStateOf<Bitmap?>(null) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(ordered, allowed, preview) {
        current = null
        previous = null
        progress.snapTo(0f)
        if (!allowed || ordered.isEmpty()) return@LaunchedEffect
        val width = if (preview) PHOTO_PREVIEW_WIDTH else AmbientPhotoDecoder.MAX_WIDTH
        val height = if (preview) PHOTO_PREVIEW_HEIGHT else AmbientPhotoDecoder.MAX_HEIGHT

        if (preview) {
            current = AmbientPhotoDecoder.decode(context, ordered.first(), width, height)
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        var index = 0
        var failures = 0
        while (true) {
            val bitmap = AmbientPhotoDecoder.decode(context, ordered[index], width, height)
            index = (index + 1) % ordered.size
            if (bitmap == null) {
                failures++
                if (failures >= ordered.size) return@LaunchedEffect
                continue
            }
            failures = 0
            previous = current
            current = bitmap
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = if (reduceMotion) 1 else PHOTO_FADE_MS,
                ),
            )
            previous = null
            if (ordered.size == 1) return@LaunchedEffect
            delay(PHOTO_HOLD_MS)
        }
    }

    Box(Modifier.fillMaxSize().testTag(AMBIENT_PHOTO_TAG)) {
        previous?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - progress.value },
            )
        }
        current?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value },
            )
        }
    }
}

/**
 * Reddit posts are remote images, not local files. The face keeps at most the
 * previous and current bitmap and lets [RedditImageStore] validate every URL
 * and redirect; a failed image is skipped. Until the first bitmap arrives, the
 * real clock is drawn so the viewer never sees a bare gradient.
 */
@Composable
internal fun RedditFace(
    context: Context,
    items: List<RedditWallpaper>,
    allowed: Boolean,
    reduceMotion: Boolean,
    scale: TvScale,
    preview: Boolean = false,
    fallback: @Composable () -> Unit = {},
) {
    val ordered = remember(items) {
        if (items.size > 1) items.shuffled() else items
    }
    var current by remember(ordered) { mutableStateOf<Bitmap?>(null) }
    var previous by remember(ordered) { mutableStateOf<Bitmap?>(null) }
    var credit by remember(ordered) { mutableStateOf("") }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(ordered, allowed, preview) {
        current = null
        previous = null
        credit = ""
        progress.snapTo(0f)
        if (!allowed || ordered.isEmpty()) return@LaunchedEffect

        if (preview) {
            val item = ordered.first()
            current = withContext(Dispatchers.IO) {
                RedditImageStore.loadThumbnail(context, item.previewUrl)
            }
            credit = creditFor(item)
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        var index = 0
        var failures = 0
        while (true) {
            val item = ordered[index]
            index = (index + 1) % ordered.size
            val bitmap = withContext(Dispatchers.IO) {
                RedditImageStore.loadFull(context, item.imageUrl)
            }
            if (bitmap == null) {
                failures++
                if (failures >= ordered.size) return@LaunchedEffect
                continue
            }
            failures = 0
            previous = current
            current = bitmap
            credit = creditFor(item)
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = if (reduceMotion) 1 else PHOTO_FADE_MS,
                ),
            )
            previous = null
            if (ordered.size == 1) return@LaunchedEffect
            delay(PHOTO_HOLD_MS)
        }
    }

    Box(Modifier.fillMaxSize().testTag(AMBIENT_REDDIT_TAG)) {
        // The first remote image is not instant: keep a real clock on screen
        // instead of a bare gradient until the CDN answers.
        if (current == null) fallback()
        previous?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - progress.value },
            )
        }
        current?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value },
            )
        }
        if (!preview && credit.isNotBlank()) {
            Text(
                text = credit,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = scale.sp(20f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = scale.dp(64f),
                        bottom = scale.dp(54f),
                    ),
            )
        }
    }
}

/** The feed's author is optional; never print a dangling separator. */
internal fun creditFor(item: RedditWallpaper): String =
    if (item.author.isBlank()) {
        "r/${item.community}"
    } else {
        "r/${item.community} · u/${item.author}"
    }

@Composable
internal fun ClockFace(
    scale: TvScale,
    now: ZonedDateTime,
    is24Hour: Boolean,
    locale: Locale,
    pixelShift: Boolean,
    weather: AmbientWeather?,
) {
    var shiftStep by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pixelShift) {
        if (!pixelShift) {
            shiftStep = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(AmbientPixelShift.STEP_MS)
            shiftStep++
        }
    }
    val shift = AmbientPixelShift.offset(shiftStep)
    val shiftX by animateDpAsState(
        targetValue = scale.dp(shift.xDp),
        animationSpec = tween(durationMillis = 2_500),
        label = "clockShiftX",
    )
    val shiftY by animateDpAsState(
        targetValue = scale.dp(shift.yDp),
        animationSpec = tween(durationMillis = 2_500),
        label = "clockShiftY",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(shiftX.roundToPx(), shiftY.roundToPx()) }
            .padding(horizontal = scale.dp(120f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = AmbientClock.formatTime(now, is24Hour, locale),
            color = Color.White.copy(alpha = 0.94f),
            fontSize = scale.sp(TvType.display),
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.testTag(AMBIENT_CLOCK_TAG),
        )
        Spacer(Modifier.height(scale.dp(18f)))
        Text(
            text = AmbientClock.formatDate(now, locale),
            color = Color.White.copy(alpha = 0.58f),
            fontSize = scale.sp(TvType.title),
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (weather != null) {
            val data = weather.data
            val condition = buildString {
                if (weather.stale) {
                    append(stringResource(R.string.weather_outdated)).append(" · ")
                }
                append(weatherDescription(data.code, data.day))
            }.trim(' ', '·')
            Row(
                modifier = Modifier.padding(top = scale.dp(28f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WeatherIcon(
                    code = data.code,
                    day = data.day,
                    iconSize = scale.dp(38f),
                )
                Spacer(Modifier.width(scale.dp(12f)))
                Text(
                    text = "${data.temp}°",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = scale.sp(TvType.title),
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
                if (condition.isNotEmpty()) {
                    Spacer(Modifier.width(scale.dp(12f)))
                    Text(
                        text = condition,
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = scale.sp(TvType.callout),
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * A slow, low-frequency gradient. It changes every [CLOCK_DRIFT_MS] instead of
 * every frame, so a clock/minimal saver does not keep the GPU at 60 Hz.
 */
@Composable
internal fun AmbientBackdrop(mode: AmbientMode, reduceMotion: Boolean) {
    val clock = mode == AmbientMode.CLOCK
    val top = if (clock) Color(0xFF111923) else Color(0xFF0B0F14)
    val middle = if (clock) Color(0xFF0C121A) else Color(0xFF0E141B)
    val bottom = Color(0xFF07090C)
    val accent = if (clock) Color(0xFF27445F) else Color(0xFF173039)
    var phase by remember(mode) { mutableFloatStateOf(if (clock) 0.25f else 0.65f) }

    LaunchedEffect(mode, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(CLOCK_DRIFT_MS)
            phase = (phase + 0.13f) % 1f
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(top, middle, bottom),
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = if (clock) 0.34f else 0.22f),
                            Color.Transparent,
                        ),
                        center = Offset(
                            x = size.width * (0.22f + 0.56f * phase),
                            y = size.height * 0.18f,
                        ),
                        radius = size.maxDimension * 0.82f,
                    ),
                )
            },
    )
}

/**
 * Weather is read from the existing opt-in cache only. The ambient surface
 * never starts a network request: if there is no cached forecast, it shows the
 * clock alone instead of inventing a reading.
 */
internal data class AmbientWeather(val data: Weather.Data, val stale: Boolean)

@Composable
internal fun rememberAmbientWeather(
    context: android.content.Context,
    mode: AmbientMode,
    showWeather: Boolean,
): AmbientWeather? {
    val shouldShow = showWeather && mode == AmbientMode.CLOCK
    var weather by remember(shouldShow) { mutableStateOf<AmbientWeather?>(null) }
    LaunchedEffect(shouldShow, context) {
        weather = if (!shouldShow) {
            null
        } else {
            withContext(Dispatchers.IO) {
                if (!WeatherPreferences.enabled(context)) {
                    null
                } else {
                    Weather.cached(context)?.let { AmbientWeather(it, Weather.stale(context)) }
                }
            }
        }
    }
    return weather
}

/**
 * The clock changes on the minute, not on every frame. The first value is
 * current; the loop aligns itself to the next wall-clock minute.
 */
@Composable
internal fun rememberAmbientNow(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val sinceMinute = System.currentTimeMillis() % 60_000L
            delay((60_000L - sinceMinute).coerceAtLeast(1L))
            now = ZonedDateTime.now()
        }
    }
    return now
}
