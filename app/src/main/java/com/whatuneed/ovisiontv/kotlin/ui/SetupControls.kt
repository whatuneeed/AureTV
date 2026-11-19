package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.stringResource
import com.whatuneed.ovisiontv.kotlin.core.designsystem.SectionGlyph
import com.whatuneed.ovisiontv.kotlin.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

internal const val SETUP_ROW_HEIGHT = 92f
internal const val SETUP_ROW_HEIGHT_DETAIL = 112f
internal const val SETUP_ROW_RADIUS = 24f
internal const val SETUP_ROW_PADDING = 36f
internal const val SETUP_ROW_PADDING_END = 32f
internal const val SETUP_ROW_FONT = TvType.subhead
internal const val SETUP_ROW_DETAIL_FONT = TvType.callout
internal const val SETUP_ROW_VALUE_FONT = TvType.body
internal const val SETUP_ROW_MARK = 36f
internal const val SETUP_ROW_FOCUS_SCALE = 1.04f
internal const val BUTTON_HEIGHT = 80f
internal const val BUTTON_RADIUS = 40f
internal const val BUTTON_FONT = TvType.subhead
internal const val BUTTON_MIN_WIDTH = 300f
internal const val BUTTON_PADDING = 52f
internal const val BUTTON_GAP = 16f
internal const val BUTTON_FOCUS_SCALE = 1.08f

@Composable
internal fun SetupRowCard(
    row: SetupRow,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
) {
    val reduceMotion = LocalReduceMotion.current
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "setupRowFocus",
    )
    val shape = RoundedCornerShape(scale.dp(SETUP_ROW_RADIUS))
    // Java's palette: white plate at 15% grows to 95% white on focus, and the
    // body text flips to the same near-black the platform labels use.
    val titleColor = lerp(Color.White, TvInk.focusInk, focusAmount)
    val detailColor = lerp(
        Color.White.copy(alpha = 0.6f),
        TvInk.focusInk.copy(alpha = 0.6f),
        focusAmount,
    )
    val valueColor = detailColor
    val okColor = lerp(LauncherColors.success, LauncherColors.successPressed, focusAmount)
    val on = stringResource(R.string.settings_state_on)
    val off = stringResource(R.string.settings_state_off)
    val selected = stringResource(R.string.settings_selected)
    val announcement = listOfNotNull(row.title, row.detail, row.value).joinToString(", ")

    Box(
        Modifier
            .fillMaxWidth()
            .height(scale.dp(if (row.detail == null) SETUP_ROW_HEIGHT else SETUP_ROW_HEIGHT_DETAIL))
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = announcement
                when (row.kind) {
                    RowKind.TOGGLE -> {
                        stateDescription = if (row.checked) on else off
                        toggleableState = if (row.checked) ToggleableState.On else ToggleableState.Off
                        role = Role.Switch
                    }

                    RowKind.RADIO -> {
                        stateDescription = if (row.checked) selected else off
                        role = Role.RadioButton
                    }

                    RowKind.ACTION -> role = Role.Button
                }
                row.action?.let { action -> onClick(label = row.title) { action(); true } }
            }
            .graphicsLayer {
                val grow = 1f + (SETUP_ROW_FOCUS_SCALE - 1f) * focusAmount
                scaleX = grow
                scaleY = grow
            }
            .clip(shape)
            .background(lerp(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.95f), focusAmount))
            .padding(start = scale.dp(SETUP_ROW_PADDING), end = scale.dp(SETUP_ROW_PADDING_END)),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (row.kind == RowKind.ACTION && row.value != null) {
                    // Java keeps the state on the label's line and gives the
                    // detail the full row width; a status column of its own
                    // would cut the detail short.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.title,
                            color = titleColor,
                            fontSize = scale.sp(SETUP_ROW_FONT),
                            fontWeight = FontWeight.Medium,
                            lineHeight = scale.sp(TvLeading.row),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(scale.dp(TvSpace.xl)))
                        Text(
                            text = if (row.ok) "✓ ${row.value}" else row.value,
                            color = if (row.ok) okColor else valueColor,
                            fontSize = scale.sp(SETUP_ROW_VALUE_FONT),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(
                        text = row.title,
                        color = titleColor,
                        fontSize = scale.sp(SETUP_ROW_FONT),
                        fontWeight = FontWeight.Medium,
                        // The platform metrics alone push the detail past the row
                        // edge; the explicit heights keep both lines inside.
                        lineHeight = scale.sp(TvLeading.row),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.detail != null) {
                    Text(
                        text = row.detail,
                        color = detailColor,
                        fontSize = scale.sp(SETUP_ROW_DETAIL_FONT),
                        lineHeight = scale.sp(TvLeading.rowDetail),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (row.kind) {
                RowKind.TOGGLE -> {
                    Spacer(Modifier.width(scale.dp(14f)))
                    SwitchMark(on = row.checked, focused = focused, scale = scale)
                }

                RowKind.RADIO -> {
                    Spacer(Modifier.width(scale.dp(14f)))
                    SelectMark(selected = row.checked, focused = focused, scale = scale)
                }

                RowKind.ACTION -> {
                    // The value on the left is not enough: an action row must look
                    // like a place the remote can enter, not like a read-only stat.
                    Spacer(Modifier.width(scale.dp(10f)))
                    Image(
                        imageVector = SectionGlyph.chevron,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            lerp(
                                Color.White.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.5f),
                                focusAmount,
                            ),
                        ),
                        modifier = Modifier.size(scale.dp(22f)),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SwitchMark(on: Boolean, focused: Boolean, scale: TvScale) {
    // Java's switch: a green track when on, a faint white one when off, and a
    // white knob that always travels 28 points to the right.
    val track = when {
        on -> if (focused) LauncherColors.successPressed else LauncherColors.success
        focused -> TvInk.focusInk.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.25f)
    }
    val reduceMotion = LocalReduceMotion.current
    val knobOffset by animateFloatAsState(
        targetValue = if (on) 28f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "setupSwitch",
    )
    Box(
        Modifier
            .size(scale.dp(64f), scale.dp(36f))
            .clip(RoundedCornerShape(scale.dp(18f)))
            .background(track),
    ) {
        Box(
            Modifier
                // 4-point inset on every side; 64 − 28 − 2×4 is the travel.
                .offset {
                    IntOffset(
                        x = scale.dp(4f + knobOffset).roundToPx(),
                        y = scale.dp(4f).roundToPx(),
                    )
                }
                .size(scale.dp(28f))
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
internal fun SelectMark(selected: Boolean, focused: Boolean, scale: TvScale) {
    // Java's radio: a green disc with a white tick when chosen, a faint ring
    // otherwise. Focus only darkens the ring — the choice stays green.
    val ring = when {
        selected -> if (focused) LauncherColors.successPressed else LauncherColors.success
        focused -> TvInk.focusInk.copy(alpha = 0.5f)
        else -> Color.White.copy(alpha = 0.5f)
    }
    Box(
        Modifier
            .size(scale.dp(SETUP_ROW_MARK))
            .clip(CircleShape)
            .background(if (selected) ring else Color.Transparent)
            .border(width = scale.dp(3f), color = ring, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Image(
                imageVector = SectionGlyph.check,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(scale.dp(22f)),
            )
        }
    }
}

@Composable
internal fun SetupButton(
    text: String,
    primary: Boolean,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
) {
    val idle = if (primary) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f)
    // Java grows a focused button by 8% (its medium focus scale), more than a row.
    val reduceMotion = LocalReduceMotion.current
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "setupButtonFocus",
    )
    Box(
        Modifier
            // Russian labels run longer than the English ones; the pill grows
            // instead of clipping "Открыть Главную" to "Открыть".
            .widthIn(min = scale.dp(BUTTON_MIN_WIDTH))
            .height(scale.dp(BUTTON_HEIGHT))
            .focusRequester(focusRequester)
            .focusable()
            .graphicsLayer {
                val grow = 1f + (BUTTON_FOCUS_SCALE - 1f) * focusAmount
                scaleX = grow
                scaleY = grow
            }
            .clip(RoundedCornerShape(scale.dp(BUTTON_RADIUS)))
            .background(if (focused) Color.White else idle)
            .padding(horizontal = scale.dp(BUTTON_PADDING))
            .clearAndSetSemantics {
                contentDescription = text
                role = Role.Button
                // TalkBack must run the same action as OK; the key handler is
                // not reachable from a semantic click (AQ-23).
                onClick(label = text) { onActivate(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else Color.White,
            fontSize = scale.sp(BUTTON_FONT),
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
