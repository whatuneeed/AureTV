package com.whatuneed.ovisiontv.kotlin.ui

import android.graphics.Bitmap
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
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
    }
}
