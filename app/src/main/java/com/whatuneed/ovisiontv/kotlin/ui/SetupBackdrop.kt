package com.whatuneed.ovisiontv.kotlin.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.ui.components.createPlayer
import kotlinx.coroutines.delay

/** Marks the moving welcome backdrop in UI tests and screenshots. */
internal const val SETUP_BACKDROP_TAG = "setup_backdrop"

private const val SETUP_BACKDROP_ZOOM_MIN = 1.02f
private const val SETUP_BACKDROP_ZOOM_MAX = 1.08f
private const val SETUP_BACKDROP_MOTION_MS = 34_000
private const val SETUP_BACKDROP_DRIFT = 0.012f
private const val SETUP_BACKDROP_DRIFT_MS = 29_000
private const val SETUP_BACKDROP_SWAP_MS = 22_000L
private const val SETUP_BACKDROP_FADE_MS = 1_800
private const val SETUP_BACKDROP_VIDEO_FADE_MS = 1_200

/**
 * Two real frames from the bundled Aerial catalogue, not invented artwork.
 * A saved/refreshed catalogue may carry only network stills, so the selection
 * falls back to any bundled frame when no landscape one survives.
 */
internal fun setupWelcomeClips(clips: List<AerialClip>): List<AerialClip> {
    val bundled = clips.filter {
        it.previewUrl.startsWith("asset:aerial_previews/")
    }.sortedBy { it.order }
    val landscapes = bundled.filter { it.theme == SaverTheme.LANDSCAPES }
    return (landscapes.ifEmpty { bundled }).take(2)
}

/**
 * The tvOS-like welcome wallpaper: a slow camera move over real aerial frames
 * with a long crossfade. It is deliberately local-only, so first launch works
 * before the network and never contacts Apple just to paint a background.
 *
 * Reduce Motion keeps the first frame still; the rest of the wizard keeps the
 * static aurora from [Wallpaper].
 */
@Composable
internal fun SetupAerialBackdrop(
    frames: List<Bitmap>,
    reduceMotion: Boolean,
    videoClip: AerialClip? = null,
    videoEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (frames.isEmpty()) return
    var index by remember(frames.size) { mutableIntStateOf(0) }

    LaunchedEffect(frames.size, reduceMotion) {
        if (reduceMotion || frames.size < 2) return@LaunchedEffect
        while (true) {
            delay(SETUP_BACKDROP_SWAP_MS)
            index = (index + 1) % frames.size
        }
    }

    val zoom: Float
    val driftX: Float
    val driftY: Float
    if (reduceMotion) {
        zoom = SETUP_BACKDROP_ZOOM_MIN
        driftX = 0f
        driftY = 0f
    } else {
        val transition = rememberInfiniteTransition(label = "setupBackdrop")
        zoom = transition.animateFloat(
            initialValue = SETUP_BACKDROP_ZOOM_MIN,
            targetValue = SETUP_BACKDROP_ZOOM_MAX,
            animationSpec = infiniteRepeatable(
                tween(SETUP_BACKDROP_MOTION_MS, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "setupBackdropZoom",
        ).value
        driftX = transition.animateFloat(
            initialValue = -SETUP_BACKDROP_DRIFT,
            targetValue = SETUP_BACKDROP_DRIFT,
            animationSpec = infiniteRepeatable(
                tween(SETUP_BACKDROP_DRIFT_MS, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "setupBackdropDriftX",
        ).value
        driftY = transition.animateFloat(
            initialValue = SETUP_BACKDROP_DRIFT * 0.5f,
            targetValue = -SETUP_BACKDROP_DRIFT * 0.5f,
            animationSpec = infiniteRepeatable(
                tween(SETUP_BACKDROP_DRIFT_MS + 5_000, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "setupBackdropDriftY",
        ).value
    }

    Box(modifier.fillMaxSize().testTag(SETUP_BACKDROP_TAG)) {
        Crossfade(
            targetState = index,
            animationSpec = tween(SETUP_BACKDROP_FADE_MS),
            label = "setupBackdropFrame",
        ) { frame ->
            Image(
                bitmap = frames[frame.coerceIn(0, frames.lastIndex)].asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = size.width * driftX
                        translationY = size.height * driftY
                    },
            )
        }
        if (videoEnabled && !reduceMotion && videoClip != null) {
            SetupAerialVideo(
                clip = videoClip,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Plays the welcome clip behind the wizard. The local still is drawn first and
 * stays visible until the first decoded frame; a missing network or a decoder
 * error only leaves that still on screen. No audio, no cache, no focus.
 *
 * The player is deliberately scoped to this composable: it is created once for
 * the whole wizard and released when Setup leaves, not once per page.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun SetupAerialVideo(
    clip: AerialClip,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember(clip.videoUrl) { mutableStateOf<ExoPlayer?>(null) }
    var firstFrame by remember(clip.videoUrl) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (firstFrame) 1f else 0f,
        animationSpec = tween(SETUP_BACKDROP_VIDEO_FADE_MS),
        label = "setupBackdropVideoAlpha",
    )

    DisposableEffect(clip.videoUrl, context) {
        val created = runCatching { createPlayer(context) }.getOrNull()
        val listener = created?.let {
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrame = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    firstFrame = false
                }
            }
        }
        if (created != null && listener != null) {
            created.addListener(listener)
            created.repeatMode = Player.REPEAT_MODE_ONE
            created.setMediaItem(MediaItem.fromUri(clip.videoUrl))
            created.prepare()
            created.playWhenReady = true
            player = created
        }
        onDispose {
            if (created != null) {
                if (listener != null) created.removeListener(listener)
                created.release()
            }
            if (player === created) player = null
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val active = player
        if (active == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> active.play()
                    Lifecycle.Event.ON_STOP -> active.pause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (LayoutInflater.from(ctx)
                .inflate(R.layout.saver_player, FrameLayout(ctx), false) as PlayerView)
                .apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.alpha = 0f
                    visibility = View.VISIBLE
                }
        },
        update = { view ->
            view.player = player
            view.alpha = alpha
        },
    )
}
