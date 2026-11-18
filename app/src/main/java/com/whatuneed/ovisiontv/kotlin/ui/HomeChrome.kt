package com.whatuneed.ovisiontv.kotlin.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.TvApp

private const val CHROME_TAG = "OVisionHome"

/**
 * App name under a tile. tvOS puts the label on the home surface, not inside
 * the artwork; keeping it a sibling avoids shrinking the 250×150 icon and
 * leaves the TalkBack node on [com.whatuneed.ovisiontv.kotlin.ui.components.CardShell].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TileLabel(text: String, x: Float, y: Float, scale: TvScale) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.86f),
        fontSize = scale.sp(TvType.callout),
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .offset(x = scale.dp(x), y = scale.dp(y))
            .width(scale.dp(TvOs.ICON_WIDTH))
            // The card already announces this label; keep the visible line out
            // of TalkBack while leaving it inspectable for layout tests.
            .semantics { invisibleToUser() }
            .testTag("tile_label"),
    )
}

/**
 * Section title inside the full-screen grid. It shares the grid's shifted layer
 * so it scrolls with the row it names, and fades with the shelf.
 */
@Composable
internal fun LibraryHeading(
    text: String,
    y: Float,
    fade: Animatable<Float, AnimationVector1D>,
    scale: TvScale,
) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = text.uppercase(locale),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = scale.sp(TvType.callout),
        letterSpacing = (21f * scale.factor * 0.08f).sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .offset(x = scale.dp(TvOs.SAFE_AREA), y = scale.dp(y))
            .graphicsLayer { alpha = 1f - fade.value },
    )
}

/**
 * The library is a flat dark surface. The blurred app banner it used to show
 * cost a full-screen RenderEffect on every frame; without it the screen is one
 * static gradient, which is what tvOS 27 shows behind the app grid.
 */
@Composable
internal fun LibraryBackdrop(alpha: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .background(
                Brush.verticalGradient(
                    listOf(TvInk.dockTop, TvInk.dockBottom),
                ),
            ),
    )
}

@Composable
internal fun DockBand(
    scale: TvScale,
    reduceMotion: Boolean,
    scroll: Animatable<Float, AnimationVector1D>,
    shift: () -> Float,
    focused: Boolean,
    fade: () -> Float,
) {
    val shape = RoundedCornerShape(scale.dp(TvOs.DOCK_RADIUS))
    // 13_VISUAL_DESIGN_SYSTEM dock.focusGrowth: the band widens by 11 pt on
    // each side while the first row holds focus. Duration is UNKNOWN; it uses
    // the shared focus duration so the band and the tile grow together.
    val grow by animateFloatAsState(
        targetValue = if (focused) TvOs.DOCK_FOCUS_GROWTH else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "dockFocusGrowth",
    )
    Box(
        Modifier
            .offset {
                IntOffset(
                    x = scale.dp(TvOs.DOCK_X - grow).roundToPx(),
                    y = scale.dp(TvOs.DOCK_Y).roundToPx(),
                )
            }
            .size(scale.dp(TvOs.DOCK_WIDTH + grow * 2), scale.dp(TvOs.DOCK_HEIGHT))
            // tvOS draws the dock band only while the first row is at dock
            // position (dockMode == YES), so it leaves as soon as the grid
            // scrolls; in the library the favourites row is an ordinary row.
            // The shift and fades are read in the layer, so the library
            // animation does not recompose the band on every frame.
            .graphicsLayer {
                translationY = -shift() * scale.factor * density
                alpha = (1f - scroll.value / TvOs.ROW_PITCH).coerceIn(0f, 1f) * fade()
            }
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.045f)),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f)),
                ),
                shape = shape,
            ),
    )
}

fun launchApp(context: Context, app: TvApp): Boolean {
    if (!app.available) {
        Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        return false
    }
    val intent = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(app.packageName, app.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (error: Exception) {
        // The toast is for the viewer; the log keeps the reason diagnosable
        // without recording anything about the person (ST-22).
        Log.w(CHROME_TAG, "cannot launch ${app.packageName}", error)
        Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        false
    }
}
