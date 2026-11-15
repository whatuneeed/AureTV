package com.whatuneed.ovisiontv.kotlin.ui.components

import android.text.format.DateFormat
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.media3.exoplayer.ExoPlayer
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientClock
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientClockFormat
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientMode
import com.whatuneed.ovisiontv.kotlin.ambient.AmbientPixelShift
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.ui.LocalReduceMotion
import com.whatuneed.ovisiontv.kotlin.ui.SaverAlphaEasing
import com.whatuneed.ovisiontv.kotlin.ui.SaverExitEasing
import com.whatuneed.ovisiontv.kotlin.ui.SurfaceEasing
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.util.Locale

internal const val AMBIENT_SCREEN_TAG = "ambient_screen"
internal const val AMBIENT_CLOCK_TAG = "ambient_clock"
internal const val AMBIENT_PREVIEW_TAG = "ambient_preview"

private const val AMBIENT_ENTRY_MS = 450
private const val AMBIENT_ALPHA_MS = 250
private const val AMBIENT_EXIT_MS = 330
private const val AMBIENT_REDUCED_MS = 400
private const val AMBIENT_SCALE = 1.03f
private const val CLOCK_DRIFT_MS = 15_000L

/**
 * One ambient surface for every mode. Aerial delegates to the existing
 * [SaverScreen] so its playback, still fallback and watchdog are unchanged;
 * Clock and Minimal are local surfaces with no decoder and no network.
 */
@Composable
internal fun AmbientScreen(
    mode: AmbientMode,
    items: List<AerialClip>,
    clockFormat: AmbientClockFormat = AmbientClockFormat.SYSTEM,
    pixelShift: Boolean = true,
    closing: Boolean = false,
    swallowOpeningPress: Boolean = false,
    players: List<ExoPlayer>? = null,
    onCloseRequested: () -> Unit = {},
    onDismissed: () -> Unit = {},
    publishMediaSession: Boolean = false,
    handleBack: Boolean = true,
) {
    if (mode == AmbientMode.AERIAL) {
        SaverScreen(
            items = items,
            closing = closing,
            swallowOpeningPress = swallowOpeningPress,
            players = players,
            onCloseRequested = onCloseRequested,
            onDismissed = onDismissed,
            publishMediaSession = publishMediaSession,
            handleBack = handleBack,
        )
        return
    }
    AmbientStillScreen(
        mode = mode,
        clockFormat = clockFormat,
        pixelShift = pixelShift,
        closing = closing,
        swallowOpeningPress = swallowOpeningPress,
        onCloseRequested = onCloseRequested,
        onDismissed = onDismissed,
        handleBack = handleBack,
    )
}

@Composable
private fun AmbientStillScreen(
    mode: AmbientMode,
    clockFormat: AmbientClockFormat,
    pixelShift: Boolean,
    closing: Boolean,
    swallowOpeningPress: Boolean,
    onCloseRequested: () -> Unit,
    onDismissed: () -> Unit,
    handleBack: Boolean,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current
    val scale = rememberTvScale()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val systemIs24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val is24Hour = clockFormat.use24Hour(systemIs24Hour)
    val now = rememberAmbientNow()
    val modeLabel = ambientModeLabel(mode)
    val clockTime = if (mode == AmbientMode.CLOCK) {
        AmbientClock.formatTime(now, is24Hour, locale)
    } else {
        ""
    }
    val clockDate = if (mode == AmbientMode.CLOCK) {
        AmbientClock.formatDate(now, locale)
    } else {
        ""
    }
    val description = if (mode == AmbientMode.CLOCK) {
        stringResource(R.string.ambient_clock_description, clockTime, clockDate)
    } else {
        stringResource(R.string.ambient_screen_description, modeLabel)
    }

    var entered by remember { mutableStateOf(false) }
    var openingPressReleased by remember { mutableStateOf(!swallowOpeningPress) }
    LaunchedEffect(Unit) { entered = true }

    val screenAlpha by animateFloatAsState(
        targetValue = if (entered && !closing) 1f else 0f,
        animationSpec = tween(
            durationMillis = when {
                reduceMotion -> AMBIENT_REDUCED_MS
                closing -> AMBIENT_EXIT_MS
                else -> AMBIENT_ALPHA_MS
            },
            easing = if (closing) SaverExitEasing else SaverAlphaEasing,
        ),
        label = "ambientScreenAlpha",
    )
    val screenScale by animateFloatAsState(
        targetValue = if (reduceMotion || (entered && !closing)) 1f else AMBIENT_SCALE,
        animationSpec = tween(
            durationMillis = if (reduceMotion) AMBIENT_REDUCED_MS else AMBIENT_ENTRY_MS,
            easing = SurfaceEasing,
        ),
        label = "ambientScreenScale",
    )

    LaunchedEffect(closing) {
        if (closing) {
            delay((if (reduceMotion) AMBIENT_REDUCED_MS else AMBIENT_EXIT_MS).toLong())
            onDismissed()
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    if (handleBack) {
        BackHandler { if (!closing) onCloseRequested() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = screenAlpha
                scaleX = screenScale
                scaleY = screenScale
            }
            .testTag(AMBIENT_SCREEN_TAG)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                when (event.type) {
                    KeyEventType.KeyDown -> if (openingPressReleased && !closing) {
                        onCloseRequested()
                    }

                    KeyEventType.KeyUp -> openingPressReleased = true

                    else -> Unit
                }
                true
            }
            .focusable()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        AmbientBackdrop(mode = mode, reduceMotion = reduceMotion)
        if (mode == AmbientMode.CLOCK) {
            ClockFace(
                scale = scale,
                now = now,
                is24Hour = is24Hour,
                locale = locale,
                pixelShift = pixelShift,
            )
        }
    }
}

/**
 * A real still of the chosen mode for the Settings page. It never starts a
 * decoder; the full-screen surface is opened deliberately, as before.
 */
@Composable
internal fun AmbientPreview(
    scale: TvScale,
    mode: AmbientMode,
    clockFormat: AmbientClockFormat = AmbientClockFormat.SYSTEM,
    pixelShift: Boolean = true,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val systemIs24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val is24Hour = clockFormat.use24Hour(systemIs24Hour)
    val now = rememberAmbientNow()
    val shape = RoundedCornerShape(scale.dp(TvRadius.field))
    Box(
        Modifier
            .offset(x = scale.dp(SAVER_PREVIEW_LEFT), y = scale.dp(SAVER_PREVIEW_TOP))
            .size(scale.dp(SAVER_PREVIEW_WIDTH), scale.dp(SAVER_PREVIEW_HEIGHT))
            .testTag(AMBIENT_PREVIEW_TAG)
            .clip(shape),
    ) {
        AmbientBackdrop(mode = mode, reduceMotion = true)
        if (mode == AmbientMode.CLOCK) {
            ClockFace(
                scale = TvScale(scale.factor * 0.55f),
                now = now,
                is24Hour = is24Hour,
                locale = locale,
                pixelShift = pixelShift,
            )
        }
    }
}

@Composable
private fun ClockFace(
    scale: TvScale,
    now: ZonedDateTime,
    is24Hour: Boolean,
    locale: Locale,
    pixelShift: Boolean,
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
    }
}

/**
 * A slow, low-frequency gradient. It changes every [CLOCK_DRIFT_MS] instead of
 * every frame, so a clock/minimal saver does not keep the GPU at 60 Hz.
 */
@Composable
private fun AmbientBackdrop(mode: AmbientMode, reduceMotion: Boolean) {
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
