package app.aure.tv.ui.components

import android.net.Uri
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.media3.exoplayer.ExoPlayer
import app.aure.tv.R
import app.aure.tv.ambient.AmbientClock
import app.aure.tv.ambient.AmbientClockFormat
import app.aure.tv.ambient.AmbientDim
import app.aure.tv.ambient.AmbientMode
import app.aure.tv.ambient.AmbientPowerPolicy
import app.aure.tv.ambient.AmbientSession
import app.aure.tv.ambient.RedditWallpaper
import app.aure.tv.data.AerialClip
import app.aure.tv.data.AerialQuality
import app.aure.tv.ui.LocalReduceMotion
import app.aure.tv.ui.SaverAlphaEasing
import app.aure.tv.ui.SaverExitEasing
import app.aure.tv.ui.SurfaceEasing
import app.aure.tv.ui.TvRadius
import app.aure.tv.ui.TvScale
import app.aure.tv.ui.rememberTvScale
import kotlinx.coroutines.delay
import java.util.Locale

internal const val AMBIENT_SCREEN_TAG = "ambient_screen"
internal const val AMBIENT_CLOCK_TAG = "ambient_clock"
internal const val AMBIENT_PREVIEW_TAG = "ambient_preview"
internal const val AMBIENT_PHOTO_TAG = "ambient_photo"
internal const val AMBIENT_REDDIT_TAG = "ambient_reddit"

private const val AMBIENT_ENTRY_MS = 450
private const val AMBIENT_ALPHA_MS = 250
private const val AMBIENT_EXIT_MS = 330
private const val AMBIENT_REDUCED_MS = 400
private const val AMBIENT_SCALE = 1.03f

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
    dimAfter: AmbientDim = AmbientDim.NEVER,
    maxSession: AmbientSession = AmbientSession.NEVER,
    showWeather: Boolean = false,
    aerialQuality: AerialQuality = AerialQuality.COMPATIBLE,
    photos: List<Uri> = emptyList(),
    photosAllowed: Boolean = true,
    reddit: List<RedditWallpaper> = emptyList(),
    redditAllowed: Boolean = true,
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
            aerialQuality = aerialQuality,
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
        dimAfter = dimAfter,
        maxSession = maxSession,
        showWeather = showWeather,
        photos = photos,
        photosAllowed = photosAllowed,
        reddit = reddit,
        redditAllowed = redditAllowed,
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
    dimAfter: AmbientDim,
    maxSession: AmbientSession,
    showWeather: Boolean,
    photos: List<Uri>,
    photosAllowed: Boolean,
    reddit: List<RedditWallpaper>,
    redditAllowed: Boolean,
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
    // Photos with no readable folder fall back to the clock. A screen saver
    // that silently shows an empty gradient is worse than a calm, real clock.
    val showClock = mode == AmbientMode.CLOCK ||
        (mode == AmbientMode.PHOTOS && (!photosAllowed || photos.isEmpty())) ||
        (mode == AmbientMode.REDDIT && (!redditAllowed || reddit.isEmpty()))
    val clockTime = if (showClock) {
        AmbientClock.formatTime(now, is24Hour, locale)
    } else {
        ""
    }
    val clockDate = if (showClock) {
        AmbientClock.formatDate(now, locale)
    } else {
        ""
    }
    val weather = rememberAmbientWeather(
        context,
        if (showClock) AmbientMode.CLOCK else mode,
        showWeather,
    )
    val description = when {
        showClock -> stringResource(R.string.ambient_clock_description, clockTime, clockDate)
        mode == AmbientMode.PHOTOS -> stringResource(R.string.ambient_photos_description)
        mode == AmbientMode.REDDIT -> stringResource(R.string.ambient_reddit_description)
        else -> stringResource(R.string.ambient_screen_description, modeLabel)
    }

    var entered by remember { mutableStateOf(false) }
    var openingPressReleased by remember { mutableStateOf(!swallowOpeningPress) }
    LaunchedEffect(Unit) { entered = true }

    // The protection clock starts when the surface appears. Dimming is a
    // visible alpha change; the optional session limit closes the surface
    // through the same exit path as BACK.
    val startedAt = remember { SystemClock.elapsedRealtime() }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dimAfter, maxSession) {
        if (dimAfter == AmbientDim.NEVER && maxSession == AmbientSession.NEVER) {
            return@LaunchedEffect
        }
        while (true) {
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (AmbientPowerPolicy.sessionExpired(elapsedMs, maxSession)) {
                onCloseRequested()
                return@LaunchedEffect
            }
            delay(POWER_TICK_MS)
        }
    }
    val dimAlpha = AmbientPowerPolicy.dimAlpha(elapsedMs, dimAfter)

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
                alpha = screenAlpha * dimAlpha
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
        when {
            showClock -> ClockFace(
                scale = scale,
                now = now,
                is24Hour = is24Hour,
                locale = locale,
                pixelShift = pixelShift,
                weather = weather,
            )

            mode == AmbientMode.PHOTOS -> PhotoFace(
                context = context,
                photos = photos,
                allowed = photosAllowed,
                reduceMotion = reduceMotion,
            )

            mode == AmbientMode.REDDIT -> RedditFace(
                context = context,
                items = reddit,
                allowed = redditAllowed,
                reduceMotion = reduceMotion,
                scale = scale,
                fallback = {
                    ClockFace(
                        scale = scale,
                        now = now,
                        is24Hour = is24Hour,
                        locale = locale,
                        pixelShift = pixelShift,
                        weather = weather,
                    )
                },
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
    showWeather: Boolean = false,
    photos: List<Uri> = emptyList(),
    photosAllowed: Boolean = true,
    reddit: List<RedditWallpaper> = emptyList(),
    redditAllowed: Boolean = true,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val systemIs24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val is24Hour = clockFormat.use24Hour(systemIs24Hour)
    val now = rememberAmbientNow()
    val showClock = mode == AmbientMode.CLOCK ||
        (mode == AmbientMode.PHOTOS && (!photosAllowed || photos.isEmpty())) ||
        (mode == AmbientMode.REDDIT && (!redditAllowed || reddit.isEmpty()))
    val weather = rememberAmbientWeather(
        context,
        if (showClock) AmbientMode.CLOCK else mode,
        showWeather,
    )
    val shape = RoundedCornerShape(scale.dp(TvRadius.field))
    Box(
        Modifier
            .offset(x = scale.dp(SAVER_PREVIEW_LEFT), y = scale.dp(SAVER_PREVIEW_TOP))
            .size(scale.dp(SAVER_PREVIEW_WIDTH), scale.dp(SAVER_PREVIEW_HEIGHT))
            .testTag(AMBIENT_PREVIEW_TAG)
            .clip(shape),
    ) {
        AmbientBackdrop(mode = mode, reduceMotion = true)
        when {
            showClock -> ClockFace(
                scale = TvScale(scale.factor * 0.55f),
                now = now,
                is24Hour = is24Hour,
                locale = locale,
                pixelShift = pixelShift,
                weather = weather,
            )

            mode == AmbientMode.PHOTOS -> PhotoFace(
                context = context,
                photos = photos,
                allowed = photosAllowed,
                reduceMotion = true,
                preview = true,
            )

            mode == AmbientMode.REDDIT -> RedditFace(
                context = context,
                items = reddit,
                allowed = redditAllowed,
                reduceMotion = true,
                scale = TvScale(scale.factor * 0.55f),
                preview = true,
                fallback = {
                    ClockFace(
                        scale = TvScale(scale.factor * 0.55f),
                        now = now,
                        is24Hour = is24Hour,
                        locale = locale,
                        pixelShift = pixelShift,
                        weather = weather,
                    )
                },
            )
        }
    }
}

/**
 * One photo at a time, decoded off the main thread and held for [PHOTO_HOLD_MS].
 * The previous frame stays only for the crossfade, so at most two bitmaps are
 * alive. A failed decode skips that file; when every file fails the gradient
 * backdrop remains, never a crash or an endless retry loop.
 */
