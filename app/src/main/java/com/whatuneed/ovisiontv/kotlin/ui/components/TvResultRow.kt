package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.ui.LocalReduceMotion
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration

private const val RESULT_WIDTH = 720f
private const val RESULT_HEIGHT = 96f
private const val REST_FILL = 0.06f
private const val REST_EDGE = 0.09f

/**
 * One focus grammar for a result row: search results and the setup city list
 * used to carry two copies of the same fill, border, easing and semantics.
 * The content stays with the caller because the two lists lead with different
 * things — artwork in search, a chosen-city tick in the wizard.
 */
@Composable
fun TvResultRow(
    scale: TvScale,
    focused: Boolean,
    focusRequester: FocusRequester,
    contentDescription: String,
    onClickLabel: String,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    // The fill turns white; content inks fade to focusInk on the same amount.
    content: @Composable BoxScope.(focus: Float) -> Unit,
) {
    val shape = RoundedCornerShape(scale.dp(TvRadius.result))
    val reduceMotion = LocalReduceMotion.current
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "resultRowFocus",
    )
    Box(
        modifier = modifier
            .size(scale.dp(RESULT_WIDTH), scale.dp(RESULT_HEIGHT))
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                role = Role.Button
                onClick(label = onClickLabel) { onActivate(); true }
            }
            .background(lerp(Color.White.copy(alpha = REST_FILL), Color.White, focusAmount), shape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = REST_EDGE * (1f - focusAmount)),
                shape = shape,
            ),
        contentAlignment = Alignment.CenterStart,
        content = { content(focusAmount) },
    )
}
