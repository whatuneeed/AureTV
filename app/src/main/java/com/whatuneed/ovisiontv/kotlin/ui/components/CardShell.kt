package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.LocalReduceMotion
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import kotlin.math.PI
import kotlin.math.sin

// tvOS resolves its focus timing at runtime (03_FOCUS_ENGINE §4), so this
// duration is a project decision rather than a measured Apple value.

// 14_MOTION_DESIGN §2.2: 0.325 s cycle, ±1.616° (0.02820452 rad).
private const val JIGGLE_CYCLE_MS = 325
private const val JIGGLE_DEGREES = 1.616f
private const val JIGGLE_POINT = 1f
// M7: 0.0875 s, rounded to whole milliseconds.
private const val JIGGLE_SETTLE_MS = 88

// 14_MOTION_DESIGN M8: the hovered app confirms a folder drop by scaling to 0.7.
private const val FOLDER_CONFIRM_SCALE = 0.7f
private const val FOLDER_CONFIRM_MS = 300

// The measured idle shadow is a raster; Modifier.blur needs API 31 and a GPU layer
// per tile, so it is painted as stacked rounded rects that work on minSdk 28 too.
// The layers feather inward, so the shadow keeps the 268×169 footprint and never
// grows past the 9/9.5 pt protrusion the research measured. Quadratic insets put
// most layers near the rim, which keeps the visible band as dark as the blur did.
// 11 composited layers of 0.0675 darken the centre to 0.537, the same value
// the original 15 × 0.05 produced, with four fewer blend passes per tile.
private const val SHADOW_LAYER_ALPHA = 0.0675f
private const val SHADOW_FEATHER_DP = 6f
private const val SHADOW_RADIUS = 34f

/**
 * Shared 250×150 cell: shadow, focus growth, jiggle, drag lift and the folder
 * confirmation shrink. App and folder tiles differ only in their artwork.
 */
@Composable
internal fun CardShell(
    focused: Boolean,
    scale: TvScale,
    label: String,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    pickedUp: Boolean = false,
    confirming: Boolean = false,
    stateLabel: String? = null,
    // Cards parked on a hidden folder page still exist in the layout tree; an
    // empty semantics node keeps TalkBack from reading them off-panel.
    semanticsEnabled: Boolean = true,
    // TalkBack on a TV reads the input focus, so a tile driven only by the
    // launcher's custom engine is silent. A requester makes the cell a real
    // focus target; the engine still owns the geometry and every key move.
    focusRequester: FocusRequester? = null,
    onActivate: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val iconScale by animateFloatAsState(
        targetValue = if (focused) TvOs.FOCUS_SCALE else 1f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "iconFocusScale",
    )
    val confirmScale by animateFloatAsState(
        targetValue = if (confirming) FOLDER_CONFIRM_SCALE else 1f,
        animationSpec = tween(motionDuration(FOLDER_CONFIRM_MS, reduceMotion)),
        label = "folderConfirmScale",
    )
    // The clock exists only in edit mode: an always-running transition would
    // invalidate every tile's layer at 60 fps while the grid is idle (R7).
    // It outlives edit mode by the settle window so the rotation can fade out.
    val jiggleActive = editing && !pickedUp && !confirming && !reduceMotion
    val jiggleAmount by animateFloatAsState(
        targetValue = if (jiggleActive) 1f else 0f,
        animationSpec = if (jiggleActive) snap() else tween(JIGGLE_SETTLE_MS),
        label = "jiggleAmount",
    )
    val jigglePhase = if (jiggleActive || jiggleAmount > 0f) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(JIGGLE_CYCLE_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "jigglePhase",
        )
    } else {
        null
    }
    val shape = RoundedCornerShape(scale.dp(TvOs.ICON_RADIUS))
    // 0..1 while the tile grows; the light layers read the same curve inside their
    // graphicsLayer, so an animation frame updates an alpha instead of recomposing
    // the card and re-recording its gradients.
    val lightAlpha = { focusLightAlpha(iconScale) }
    // Composed only while the tile is grown or growing: settled tiles keep no light
    // nodes, and the fade-out survives until the growth returns to 1.
    val lightVisible by remember(focused) {
        derivedStateOf { focused || iconScale > 1f }
    }

    Box(
        modifier = modifier
            // The tile is one control; clearing descendants stops TalkBack from
            // stopping on the fallback letter or the folder's preview icons.
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester).focusable()
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                if (!semanticsEnabled) return@clearAndSetSemantics
                contentDescription = label
                stateLabel?.let { stateDescription = it }
                // The drag proxy repeats the label without being activatable;
                // a button role there would advertise an action that does nothing.
                if (onActivate != null) {
                    role = Role.Button
                    // A TalkBack double-tap has no key event, so it gets the same
                    // short-press action as OK on the remote.
                    onClick(label = label) { onActivate(); true }
                }
            }
            .zIndex(
                when {
                    pickedUp -> 5f
                    focused -> 2f
                    else -> 1f
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 15_ICONS_AND_ARTWORK §3: the 268×169 shadow is its own layer and never
        // scales with the tile, so focus cannot make it jump.
        Box(
            Modifier
                .size(scale.dp(TvOs.SHADOW_WIDTH), scale.dp(TvOs.SHADOW_HEIGHT))
                .offset(y = scale.dp(TvOs.SHADOW_OFFSET_Y))
                .drawBehind { drawSoftShadow(SHADOW_FEATHER_DP.dp.toPx()) },
        )

        Box(
            Modifier
                .size(scale.dp(TvOs.ICON_WIDTH), scale.dp(TvOs.ICON_HEIGHT))
                .graphicsLayer {
                    val phase = jigglePhase
                    if (phase != null) {
                        val angle = phase.value * 2f * PI.toFloat()
                        // M7: position is removed at once, rotation settles.
                        rotationZ = sin(angle) * JIGGLE_DEGREES * jiggleAmount
                        if (jiggleActive) {
                            translationX = sin(angle) * JIGGLE_POINT * scale.factor * density
                            translationY = sin(angle * 2f) * JIGGLE_POINT * scale.factor * density
                        } else {
                            translationX = 0f
                            translationY = 0f
                        }
                    } else {
                        rotationZ = 0f
                        translationX = 0f
                        translationY = 0f
                    }
                    val lift = if (pickedUp) 1.04f else 1f
                    scaleX = iconScale * confirmScale * lift
                    scaleY = iconScale * confirmScale * lift
                }
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(LauncherColors.cardFallbackTop, LauncherColors.cardFallbackBottom),
                    ),
                ),
        ) {
            content()
            if (lightVisible) IconEdgeLight(lightAlpha)
        }

        // Sibling of the card, not its child: the measured specular is 300×300
        // and must spill past the 250×150 tile.
        if (lightVisible) IconSpecular(lightAlpha, scale)
    }
}

private fun DrawScope.drawSoftShadow(feather: Float) {
    val baseRadius = size.height * (SHADOW_RADIUS / TvOs.SHADOW_HEIGHT)
    // Fewer layers on a small TV cost less per tile; the shadow gets slightly
    // lighter, so LOW stays a shadow rather than a hard drop.
    val steps = Adaptive.budgets.shadowSteps
    for (step in 1 until steps) {
        val t = step.toFloat() / steps
        val inset = feather * t * t
        drawRoundRect(
            color = Color.Black.copy(alpha = SHADOW_LAYER_ALPHA),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius((baseRadius - inset).coerceAtLeast(0f)),
        )
    }
}
