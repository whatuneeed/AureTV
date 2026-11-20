package app.aure.tv.ui.components

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.aure.tv.R
import kotlinx.coroutines.delay

/**
 * The tvOS-style hero: the selected online title's official preview plays
 * muted, looped and full-bleed behind the title, info and dock. It is not a
 * second player mode: the same ExoPlayer and the same `saver_player` layout as
 * the full trailer are reused.
 *
 * The view is transparent until the first video frame, so the already loaded
 * poster stays visible while the network is buffering. A failed preview simply
 * disappears and leaves that poster; there is no retry loop and no black hero.
 *
 * Like [TrailerOverlay], this is a direct stream: no CacheDataSource, no
 * SimpleCache and no downloaded copy on the device.
 */
private const val HERO_PREVIEW_SETTLE_MS = 650L
private const val HERO_PREVIEW_FADE_MS = 320

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun HeroPreview(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            // A background hero never takes audio focus. Sound belongs to the
            // full trailer opened with OK.
            setAudioAttributes(AudioAttributes.DEFAULT, false)
        }
    }
    var failed by remember(uri) { mutableStateOf(false) }
    var visible by remember(uri) { mutableStateOf(false) }
    var prepared by remember(uri) { mutableStateOf(false) }
    var foreground by remember(uri) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foreground = true
                    if (prepared && !failed) player.play()
                }

                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    player.pause()
                }

                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                visible = true
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                player.pause()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(uri, foreground, failed) {
        if (failed || !foreground) return@LaunchedEffect
        // A short settle keeps a fast D-pad sweep from starting a download for
        // every card it passes. It is an autoplay dwell, not a focus delay.
        delay(HERO_PREVIEW_SETTLE_MS)
        if (failed || !foreground) return@LaunchedEffect
        if (!prepared) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            prepared = true
        }
        player.playWhenReady = true
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (visible && !failed) 1f else 0f,
        animationSpec = tween(HERO_PREVIEW_FADE_MS),
        label = "heroPreviewAlpha",
    )

    if (!failed) {
        AndroidView(
            modifier = modifier.graphicsLayer { alpha = videoAlpha },
            factory = { ctx ->
                (LayoutInflater.from(ctx)
                    .inflate(R.layout.saver_player, FrameLayout(ctx), false) as PlayerView)
                    .apply {
                        this.player = player
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
            },
            update = { it.player = player },
        )
    }
}
