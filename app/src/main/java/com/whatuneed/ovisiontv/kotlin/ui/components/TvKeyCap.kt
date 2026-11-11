package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale

/**
 * Focus grammar of an on-screen key. The pad's digit keys are square and grow
 * a little more than the text keyboard's caps; the latter also carry an active
 * state and a spoken state label. Everything else — white focus fill, border
 * fade, label ink — is one contract for both.
 */
@Composable
internal fun TvKeyCap(
    text: String,
    description: String,
    focused: Boolean,
    scale: TvScale,
    width: Float,
    height: Float,
    radius: Float,
    fontSize: Float,
    focusScale: Float,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    stateLabel: String? = null,
) {
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "keyCapFocus",
    )
    val grow = 1f + (focusScale - 1f) * focusAmount
    val restFill = if (active) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.10f)
    val restBorderAlpha = if (active) 0.60f else 0.10f
    val shape = RoundedCornerShape(scale.dp(radius))
    Box(
        modifier = modifier
            .size(scale.dp(width), scale.dp(height))
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = description
                // A toggle that only changes colour is invisible to TalkBack.
                stateLabel?.let { stateDescription = it }
                role = Role.Button
                onClick(label = description) {
                    onActivate()
                    true
                }
            }
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .background(
                lerp(restFill, Color.White, focusAmount),
                shape,
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = restBorderAlpha * (1f - focusAmount)),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = lerp(Color.White.copy(alpha = 0.92f), TvInk.focusInk, focusAmount),
            fontSize = scale.sp(fontSize),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
