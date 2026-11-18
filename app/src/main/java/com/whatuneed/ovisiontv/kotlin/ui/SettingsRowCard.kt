package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ui.components.SectionGlyph
import com.whatuneed.ovisiontv.kotlin.ui.components.TvListRowSurface

internal data class SettingsRow(
    val title: String,
    val value: String? = null,
    val checked: Boolean = false,
    // One-of-many list: TalkBack announces it as a radio button, not a plain
    // button whose tick it has to infer from the label.
    val choice: Boolean = false,
    // An on/off row without a mark: null is "not a toggle", otherwise TalkBack
    // gets a Switch role and the On/Off state instead of a bare Button.
    val toggleState: Boolean? = null,
    val chevron: Boolean = false,
    val destructive: Boolean = false,
    val action: (() -> Unit)? = null,
    // A row whose dependency is off stays visible for context, but it is not
    // an enabled action and must not take focus or announce a tap action.
    val enabled: Boolean = true,
    // TalkBack reads the click action as "double-tap to …"; a row whose title is
    // a noun (an app name) needs the verb stated separately.
    val actionLabel: String? = null,
)


@Composable
internal fun SettingsRowCard(
    row: SettingsRow,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // Rows are drawn as separate Texts; a single cleared node keeps TalkBack from
    // stopping twice on the label and its value. Compose 1.7 does not map the
    // `selected` semantics to AccessibilityNodeInfo, so the check is announced
    // through stateDescription instead.
    val announcement = listOfNotNull(row.title, row.value).joinToString(", ")
    val selectedState = stringResource(R.string.settings_selected)
    val onState = stringResource(R.string.settings_state_on)
    val offState = stringResource(R.string.settings_state_off)

    TvListRowSurface(
        focused = focused && row.enabled,
        scale = scale,
        modifier = modifier
            .size(scale.dp(SETTINGS_LIST_WIDTH), scale.dp(SETTINGS_ROW_HEIGHT))
            .then(
                if (row.enabled) {
                    Modifier.focusRequester(focusRequester).focusable()
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics {
                contentDescription = announcement
                if (!row.enabled) {
                    disabled()
                    return@clearAndSetSemantics
                }
                row.toggleState?.let { on ->
                    stateDescription = if (on) onState else offState
                    toggleableState = if (on) ToggleableState.On else ToggleableState.Off
                }
                if (row.checked) {
                    stateDescription = selectedState
                    selected = true
                }
                // Read-only facts must not promise an action that OK and
                // TalkBack cannot perform.
                row.action?.let { action ->
                    role = when {
                        row.toggleState != null -> Role.Switch
                        row.choice -> Role.RadioButton
                        row.checked -> Role.Checkbox
                        else -> Role.Button
                    }
                    // TalkBack's double-tap does not reach the key handler above.
                    onClick(label = row.actionLabel ?: row.title) { action(); true }
                }
            },
    ) { focusAmount ->
        val titleColor = when {
            !row.enabled -> Color.White.copy(alpha = 0.35f)
            row.destructive -> LauncherColors.destructive
            else -> lerp(Color.White.copy(alpha = 0.95f), Color.Black, focusAmount)
        }
        val valueColor = if (!row.enabled) {
            Color.White.copy(alpha = 0.3f)
        } else {
            lerp(
                Color.White.copy(alpha = 0.5f),
                Color.Black.copy(alpha = 0.6f),
                focusAmount,
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                color = titleColor,
                fontSize = scale.sp(SETTINGS_ROW_FONT),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            row.value?.let { value ->
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = scale.sp(SETTINGS_ROW_VALUE_FONT),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(
                        max = scale.dp(SETTINGS_LIST_WIDTH - SETTINGS_ROW_PADDING * 2f - 200f),
                    ),
                )
            }
            if (row.checked) {
                Spacer(Modifier.width(scale.dp(14f)))
                RowCheck(focused = focused, scale = scale)
            }
            if (row.chevron) {
                Spacer(Modifier.width(scale.dp(10f)))
                Image(
                    imageVector = SectionGlyph.chevron,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(
                        if (!row.enabled) {
                            Color.White.copy(alpha = 0.25f)
                        } else {
                            lerp(
                                Color.White.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.5f),
                                focusAmount,
                            )
                        },
                    ),
                    modifier = Modifier.size(scale.dp(22f)),
                )
            }
        }
    }
}

@Composable
private fun RowCheck(focused: Boolean, scale: TvScale) {
    val fill = if (focused) Color.Black else Color.White
    val tint = if (focused) Color.White else Color.Black
    Box(
        Modifier
            .size(scale.dp(SETTINGS_ROW_CHECK))
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = SectionGlyph.check,
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(scale.dp(SETTINGS_ROW_CHECK - 4f)),
        )
    }
}

