package app.aure.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import app.aure.tv.data.AerialClip
import app.aure.tv.data.SaverTheme
import app.aure.tv.ui.TvInk
import app.aure.tv.ui.TvRadius
import app.aure.tv.ui.LauncherColors
import app.aure.tv.ui.TvScale
import app.aure.tv.ui.TvType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// TSKPreviewViewController puts the preview beside the option list (10_SETTINGS §2).
// The still occupies the left pane of the two-pane Settings layout: 800×450
// centred in the 1066-wide column beside the 776-wide list.
internal const val SAVER_PREVIEW_LEFT = 133f
internal const val SAVER_PREVIEW_TOP = 280f
internal const val SAVER_PREVIEW_WIDTH = 800f
internal const val SAVER_PREVIEW_HEIGHT = 450f
internal const val SAVER_PREVIEW_TAG = "saver_preview"
private const val PREVIEW_FADE_MS = 500

/**
 * A real still from the aerial the saver would start with. A neutral dark
 * surface holds the frame while it loads; nothing is drawn in its place.
 */
@Composable
internal fun SaverPreview(
    scale: TvScale,
    clip: AerialClip?,
    themes: Set<SaverTheme>,
) {
    // The saved preference may be "system": clip names must follow the locale
    // the screen is actually drawn in, not that placeholder.
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty()
    val theme = clip?.theme ?: themes.minByOrNull { it.ordinal } ?: SaverTheme.LANDSCAPES
    val bitmap = clip?.previewUrl?.takeIf { it.isNotEmpty() }?.let {
        rememberAerialPreview(it)
    }
    val imageAlpha by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = tween(PREVIEW_FADE_MS),
        label = "saverPreviewAlpha",
    )

    val shape = RoundedCornerShape(scale.dp(TvRadius.field))
    Box(
        Modifier
            .offset(x = scale.dp(SAVER_PREVIEW_LEFT), y = scale.dp(SAVER_PREVIEW_TOP))
            .size(scale.dp(SAVER_PREVIEW_WIDTH), scale.dp(SAVER_PREVIEW_HEIGHT))
            .testTag(SAVER_PREVIEW_TAG)
            .clip(shape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(TvInk.previewTop, TvInk.previewBottom),
                    ),
                ),
        )

        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = imageAlpha },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to LauncherColors.scrim.copy(alpha = 0.85f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(scale.dp(28f)),
        ) {
            Text(
                text = clip?.name(language).orEmpty(),
                color = LauncherColors.textPrimary,
                fontSize = scale.sp(TvType.subhead),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = saverThemeLabel(clip?.theme ?: theme),
                color = LauncherColors.textSecondary,
                fontSize = scale.sp(TvType.callout),
            )
        }
    }
}

/**
 * The saver entrance prefers the full-resolution frame captured on an earlier
 * run and falls back to Apple's 900×580 still before the clip has played.
 */
@Composable
internal fun rememberSaverStill(clip: AerialClip?): Bitmap? {
    val context = LocalContext.current.applicationContext
    // Seed from memory: Home has already warmed the first clip's still, and
    // waiting for an IO round trip leaves the overlay black for its first
    // frames, because the entrance fade is shorter than any dispatcher hop.
    var bitmap by remember(clip) {
        mutableStateOf(
            clip?.let {
                AerialPosterStore.cached(it.id) ?: AerialPreviewStore.cached(it.previewUrl)
            },
        )
    }
    LaunchedEffect(clip) {
        if (clip == null || bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            AerialPosterStore.load(context, clip.id)
                ?: AerialPreviewStore.load(context, clip.previewUrl)
        }
    }
    return bitmap
}

/** Shared with the saver entrance: the same still stands in for the clip. */
@Composable
internal fun rememberAerialPreview(url: String): Bitmap? {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(url) { mutableStateOf(AerialPreviewStore.cached(url)) }
    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { AerialPreviewStore.load(context, url) }
    }
    return bitmap
}
