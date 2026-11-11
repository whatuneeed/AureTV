package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 12_SYSTEM_TRANSITIONS T12 records that a context menu exists on a long OK,
// but its classes and metrics were never captured (20_LIMITATIONS U19). The
// panel below is therefore a project layout: it reuses the folder panel's dark
// glass and the settings row metrics, so a menu cannot read as another material.
private const val MENU_WIDTH = 470f
private const val MENU_EDGE = 60f
private const val MENU_RADIUS = TvRadius.menu
private const val MENU_PADDING = 16f
private const val MENU_GAP = 8f
private const val MENU_ROW_HEIGHT = 74f
private const val MENU_ROW_RADIUS = TvRadius.row
private const val MENU_TITLE_HEIGHT = 58f
// Half a tile plus a gap: the panel opens beside the focused tile, not on it.
private const val MENU_ANCHOR_OFFSET = 160f
private const val MENU_APPEAR_MS = 140
private const val MENU_EXIT_MS = 140
// A remote that sends every auto-repeat as a press and release would arm OK on
// the release of a repeat. The panel waits for a quiet gap instead: a repeat
// pair is far shorter than this, and a person releasing the key is far longer.
private const val OK_ARM_QUIET_MS = 220L

private val DestructiveColor = LauncherColors.destructive

data class TvMenuEntry(
    val label: String,
    val destructive: Boolean = false,
    val onSelect: () -> Unit,
)

/**
 * A modal list of actions for the focused tile. tvOS opens it with a long OK,
 * and so does the grid; the remote's MENU key opens the same panel.
 * [suppressOkUntilRelease] is for a menu that appeared under a still-held OK:
 * its auto-repeat must not pick the first row before the user lets go.
 * Rows are driven by the remote and exposed to TalkBack as buttons; the panel
 * is anchored to the tile and pulled inside the screen.
 */
@Composable
fun TvMenu(
    title: String,
    entries: List<TvMenuEntry>,
    anchor: Offset,
    scale: TvScale,
    suppressOkUntilRelease: Boolean = false,
    onDismiss: () -> Unit,
) {
    var focusIndex by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    var okArmed by remember { mutableStateOf(!suppressOkUntilRelease) }
    var armJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val keyRepeat = rememberTvKeyRepeat()

    val rows = entries.size
    val height = MENU_PADDING * 2 + MENU_TITLE_HEIGHT + rows * MENU_ROW_HEIGHT +
        (rows - 1).coerceAtLeast(0) * MENU_GAP
    val left = (anchor.x + MENU_ANCHOR_OFFSET).let {
        if (it + MENU_WIDTH > TvOs.SCREEN_WIDTH - MENU_EDGE) {
            anchor.x - MENU_ANCHOR_OFFSET - MENU_WIDTH
        } else {
            it
        }
    }.coerceIn(MENU_EDGE, TvOs.SCREEN_WIDTH - MENU_EDGE - MENU_WIDTH)
    val top = (anchor.y - height / 2f)
        .coerceIn(MENU_EDGE, TvOs.SCREEN_HEIGHT - MENU_EDGE - height)

    // The panel fades out before it unmounts; the action runs after the exit
    // step, still inside this coroutine, so a disappearing menu cannot leave
    // the remote on a half-closed overlay.
    fun close(then: (() -> Unit)? = null) {
        if (closing) return
        closing = true
        scope.launch {
            appear.animateTo(0f, tween(MENU_EXIT_MS))
            then?.invoke()
            onDismiss()
        }
    }

    fun choose(index: Int) {
        if (closing) return
        val entry = entries.getOrNull(index) ?: return
        close { entry.onSelect() }
    }

    // The menu's own engine still owns the arrows; this mirrors its cursor
    // into Android focus so TalkBack announces the entry the user is on.
    val rowRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    LaunchedEffect(focusIndex) {
        delay(60)
        rowRequesters[focusIndex]?.requestFocus() ?: focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        appear.animateTo(1f, tween(MENU_APPEAR_MS))
    }

    BackHandler { close() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear.value }
            .background(TvInk.scrimMenu)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) keyRepeat.release(event.key)
                if (closing) return@onKeyEvent true
                // Every key the menu uses is claimed on KeyUp as well: the parent
                // grid launches on OK-KeyUp, and a release that outlives the menu
                // node must not start whatever tile holds focus behind it.
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                            return@onKeyEvent true
                        }
                        when (event.key) {
                            Key.DirectionUp -> {
                                if (focusIndex > 0) focusIndex--
                                keyRepeat.startRepeat(scope) {
                                    if (focusIndex > 0) focusIndex--
                                }
                                true
                            }

                            Key.DirectionDown -> {
                                if (focusIndex < rows - 1) focusIndex++
                                keyRepeat.startRepeat(scope) {
                                    if (focusIndex < rows - 1) focusIndex++
                                }
                                true
                            }

                            Key.DirectionLeft, Key.DirectionRight -> true

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                // A pending arming belongs to a release that may
                                // still be followed by another repeat; a press
                                // cancels it, so only a quiet key arms the panel.
                                armJob?.cancel()
                                armJob = null
                                // The hold that opened this panel is still
                                // repeating; wait for its release so the first
                                // repeat cannot activate the first row.
                                if (!okArmed) return@onKeyEvent true
                                keyRepeat.stopRepeat()
                                choose(focusIndex)
                                true
                            }

                            Key.Back, Key.Menu -> {
                                // A held key would otherwise close the menu and
                                // immediately close or reopen what lies behind it.
                                keyRepeat.stopRepeat()
                                close()
                                true
                            }

                            else -> false
                        }
                    }

                    KeyEventType.KeyUp -> {
                        keyRepeat.release(event.key)
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                // Arm only after the key stays up for a moment:
                                // a repeat pair releases and presses again far
                                // faster, so it can never arm the panel.
                                armJob?.cancel()
                                armJob = scope.launch {
                                    delay(OK_ARM_QUIET_MS)
                                    okArmed = true
                                }
                                true
                            }

                            Key.DirectionUp, Key.DirectionDown,
                            Key.DirectionLeft, Key.DirectionRight,
                            Key.Back, Key.Menu,
                            -> true

                            else -> false
                        }
                    }

                    else -> false
                }
            }
            .semantics { paneTitle = title }
            .focusable(),
    ) {
        Column(
            Modifier
                .offset(x = scale.dp(left), y = scale.dp(top))
                .size(scale.dp(MENU_WIDTH), scale.dp(height))
                .graphicsLayer {
                    scaleX = 0.96f + 0.04f * appear.value
                    scaleY = scaleX
                }
                .clip(RoundedCornerShape(scale.dp(MENU_RADIUS)))
                .background(TvInk.panel.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)),
                    ),
                    shape = RoundedCornerShape(scale.dp(MENU_RADIUS)),
                )
                .padding(scale.dp(MENU_PADDING)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(scale.dp(MENU_TITLE_HEIGHT))
                    .padding(horizontal = scale.dp(18f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = scale.sp(TvType.body),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(scale.dp(MENU_GAP)))
                val rowRequester = remember(index) { FocusRequester() }
                rowRequesters[index] = rowRequester
                TvMenuRow(
                    entry = entry,
                    focused = index == focusIndex,
                    scale = scale,
                    focusRequester = rowRequester,
                    onClick = { choose(index) },
                )
            }
        }
    }
}

@Composable
private fun TvMenuRow(
    entry: TvMenuEntry,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(scale.dp(MENU_ROW_RADIUS))
    val focusAmount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "menuRowFocus",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(scale.dp(MENU_ROW_HEIGHT))
            .focusRequester(focusRequester)
            .focusable()
            // TalkBack bypasses the key handler, so the row carries the same
            // single action as its remote path.
            .clearAndSetSemantics {
                contentDescription = entry.label
                role = Role.Button
                onClick(label = entry.label) {
                    onClick()
                    true
                }
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f + 0.12f * focusAmount))
            .border(1.5.dp, Color.White.copy(alpha = 0.5f * focusAmount), shape)
            .padding(horizontal = scale.dp(20f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = entry.label,
            color = if (entry.destructive) DestructiveColor else Color.White.copy(alpha = 0.95f),
            fontSize = scale.sp(TvType.subhead),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
