package app.aure.tv.ui.components

import app.aure.tv.core.input.TvKeyRepeat
import app.aure.tv.core.input.rememberTvKeyRepeat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aure.tv.R
import app.aure.tv.ui.TvInk
import app.aure.tv.ui.TvRadius
import app.aure.tv.ui.TvOs
import app.aure.tv.ui.TvScale
import app.aure.tv.ui.TvType

/**
 * The on-screen keyboard is a project decision: tvOS builds Search's UI in TVML at
 * runtime and its keyboard is UNKNOWN (09_SEARCH §7), so nothing here can be traced
 * to a measured Apple value. The three-row layout follows the remote: Cyrillic and
 * Latin each get one row per hardware row.
 */
internal enum class KeyboardMode { LATIN, CYRILLIC, DIGITS }

internal object TvKeyboardLayout {
    const val MAX_LENGTH = 30

    val LATIN = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    val CYRILLIC = listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбюё")
    val DIGITS = listOf("1234567890", ".-'&:!?")

    fun letters(mode: KeyboardMode): List<String> = when (mode) {
        KeyboardMode.LATIN -> LATIN
        KeyboardMode.CYRILLIC -> CYRILLIC
        KeyboardMode.DIGITS -> DIGITS
    }

    fun clampColumn(column: Int, rowLength: Int): Int =
        if (rowLength <= 0) 0 else column.coerceIn(0, rowLength - 1)
}

// Panel geometry in tvOS points (1920x1080). The 84 pt letter key keeps a
// three-row hardware feel rather than a compact phone layout.
internal const val PANEL_WIDTH = 1200f
internal const val PANEL_HEIGHT = 540f
private const val PANEL_X = 360f
private const val PANEL_Y = 470f
private const val PANEL_RADIUS = TvRadius.panel
internal const val CONTENT_PADDING = 40f
private const val TITLE_TOP = 20f
private const val FIELD_TOP = 62f
private const val FIELD_HEIGHT = 64f
private const val KEYS_TOP = 150f
// The recovery note sits between the title and the field; the field and
// the keys shift down while it is shown.
private const val NOTE_TOP = 34f
private const val NOTE_FONT = TvType.callout
private const val NOTE_SHIFT = 36f
internal const val KEY_HEIGHT = 72f
internal const val KEY_GAP = 10f
private const val KEY_PITCH = KEY_HEIGHT + KEY_GAP
private const val KEY_RADIUS = TvRadius.keyCompact
private const val KEY_FOCUS_SCALE = 1.06f
private const val LETTER_KEY_WIDTH = 84f

// Lets the UI test compare key bounds with the panel that clips them.
internal const val KEYBOARD_PANEL_TAG = "keyboard_panel"

internal sealed interface KeyAction {
    data class Input(val value: String) : KeyAction
    data object Space : KeyAction
    data object Backspace : KeyAction
    data object Clear : KeyAction
    data object Done : KeyAction
    data object Shift : KeyAction
    data class Mode(val mode: KeyboardMode) : KeyAction
}

// `spoken` lets a mode key keep its short visual label while TalkBack and the
// walkthrough announce the layout it switches to.
internal data class KeyDef(
    val label: String,
    val width: Float,
    val action: KeyAction,
    val spoken: String? = null,
)

// Localized names travel with the keys so a spoken key never depends on the
// layout being Latin: the same KeyLabels feeds the walkthrough and TalkBack.
internal data class KeyLabels(
    val space: String,
    val clear: String,
    val done: String,
    val backspace: String,
    val shift: String,
    val shiftOn: String,
    val shiftOff: String,
    val modeLatin: String,
    val modeCyrillic: String,
    val modeDigits: String,
) {
    fun spoken(key: KeyDef): String = when (key.action) {
        is KeyAction.Input -> key.label
        KeyAction.Space -> space
        KeyAction.Backspace -> backspace
        KeyAction.Clear -> clear
        KeyAction.Done -> done
        KeyAction.Shift -> shift
        is KeyAction.Mode -> key.spoken ?: key.label
    }
}

@Composable
internal fun rememberKeyLabels(): KeyLabels {
    val context = LocalContext.current
    return remember(context) {
        KeyLabels(
            space = context.getString(R.string.keyboard_space),
            clear = context.getString(R.string.keyboard_clear),
            done = context.getString(R.string.keyboard_done),
            backspace = context.getString(R.string.keyboard_backspace),
            shift = context.getString(R.string.keyboard_shift),
            shiftOn = context.getString(R.string.keyboard_shift_on),
            shiftOff = context.getString(R.string.keyboard_shift_off),
            modeLatin = context.getString(R.string.keyboard_mode_latin),
            modeCyrillic = context.getString(R.string.keyboard_mode_cyrillic),
            modeDigits = context.getString(R.string.keyboard_mode_digits),
        )
    }
}

/**
 * Cursor plus text for one keyboard surface. Rename confirms the text; Search
 * reads it live, so Done and confirmation stay with the host and never here.
 */
internal class KeyboardState(initialText: String, initialMode: KeyboardMode) {
    var text by mutableStateOf(initialText.take(TvKeyboardLayout.MAX_LENGTH))
    var mode by mutableStateOf(initialMode)
    var shift by mutableStateOf(false)
    var row by mutableIntStateOf(0)
    var column by mutableIntStateOf(0)

    fun rows(labels: KeyLabels): List<List<KeyDef>> =
        letterRows(mode, shift) + listOf(actionRow(mode, labels))

    fun move(dx: Int, dy: Int, rows: List<List<KeyDef>>) {
        if (dx != 0) {
            column = TvKeyboardLayout.clampColumn(column + dx, rows[row].size)
        } else if (dy != 0) {
            val next = (row + dy).coerceIn(0, rows.lastIndex)
            if (next == row) return
            row = next
            column = TvKeyboardLayout.clampColumn(column, rows[row].size)
        }
    }

    fun apply(action: KeyAction) {
        when (action) {
            is KeyAction.Input -> type(action.value)
            KeyAction.Space -> type(" ")
            KeyAction.Backspace -> backspace()
            KeyAction.Clear -> text = ""
            KeyAction.Shift -> shift = !shift
            KeyAction.Done -> Unit
            is KeyAction.Mode -> {
                mode = action.mode
                shift = false
                // Land on the first letter of the new layout; the toggle itself
                // is a one-shot switch, not a place to stay.
                row = 0
                column = 0
            }
        }
    }

    fun type(value: String) {
        if (text.length >= TvKeyboardLayout.MAX_LENGTH) return
        text = (text + value).take(TvKeyboardLayout.MAX_LENGTH)
        shift = false
    }

    fun backspace() {
        text = text.dropLast(1)
    }
}

private fun letterRows(mode: KeyboardMode, shift: Boolean): List<List<KeyDef>> =
    TvKeyboardLayout.letters(mode).map { line ->
        line.map { char ->
            val label = if (shift) char.uppercaseChar().toString() else char.toString()
            KeyDef(label, LETTER_KEY_WIDTH, KeyAction.Input(label))
        }
    }

private fun actionRow(mode: KeyboardMode, labels: KeyLabels): List<KeyDef> {
    val keys = mutableListOf(
        KeyDef("\u21E7", 90f, KeyAction.Shift),
        KeyDef(labels.space, 210f, KeyAction.Space),
        KeyDef("\u232B", 110f, KeyAction.Backspace),
    )
    when (mode) {
        KeyboardMode.DIGITS -> {
            keys += KeyDef(
                "\u0430\u0431\u0432",
                100f,
                KeyAction.Mode(KeyboardMode.CYRILLIC),
                labels.modeCyrillic,
            )
            keys += KeyDef("abc", 100f, KeyAction.Mode(KeyboardMode.LATIN), labels.modeLatin)
        }

        KeyboardMode.CYRILLIC -> {
            keys += KeyDef("123", 100f, KeyAction.Mode(KeyboardMode.DIGITS), labels.modeDigits)
            keys += KeyDef("abc", 100f, KeyAction.Mode(KeyboardMode.LATIN), labels.modeLatin)
        }

        KeyboardMode.LATIN -> {
            keys += KeyDef("123", 100f, KeyAction.Mode(KeyboardMode.DIGITS), labels.modeDigits)
            keys += KeyDef(
                "\u0430\u0431\u0432",
                100f,
                KeyAction.Mode(KeyboardMode.CYRILLIC),
                labels.modeCyrillic,
            )
        }
    }
    keys += KeyDef(labels.clear, 120f, KeyAction.Clear)
    keys += KeyDef(labels.done, 160f, KeyAction.Done)
    return keys
}

@Composable
internal fun KeyboardPanel(
    scale: TvScale,
    width: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(scale.dp(PANEL_RADIUS))
    Box(
        modifier = modifier
            .testTag(KEYBOARD_PANEL_TAG)
            .size(scale.dp(width), scale.dp(PANEL_HEIGHT))
            .clip(shape)
            .background(TvInk.panel.copy(alpha = 0.97f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)),
                ),
                shape = shape,
            ),
        content = content,
    )
}

@Composable
internal fun BoxScope.KeyboardKeys(
    state: KeyboardState,
    labels: KeyLabels,
    scale: TvScale,
    top: Float,
    width: Float,
    showFocus: Boolean = true,
    onAction: (KeyAction) -> Unit,
) {
    val rows = state.rows(labels)
    // TalkBack on a TV speaks the node that holds Android's input focus, so the
    // engine cursor is mirrored onto real focus targets instead of staying
    // painted-only. Hosts that hide the keys (Search on the results zone) pass
    // showFocus = false and keep the focus on their own list.
    val keyRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    LaunchedEffect(showFocus, state.row, state.column, state.mode, state.shift) {
        if (showFocus) keyRequesters[state.row to state.column]?.requestFocus()
    }
    // A narrower host (Search) still gets the full three-row layout: one fit
    // factor shrinks every key equally, so the rows keep matching key widths
    // instead of spilling past the panel edge.
    val fit = rows.maxOf { keys ->
        keys.sumOf { it.width.toDouble() }.toFloat() + KEY_GAP * (keys.size - 1)
    }.let { widest -> if (widest > width) width / widest else 1f }
    rows.forEachIndexed { rowIndex, keys ->
        val rowWidth = keys.sumOf { it.width.toDouble() }.toFloat() +
            KEY_GAP * (keys.size - 1)
        var x = CONTENT_PADDING + (width - rowWidth * fit) / 2f
        keys.forEachIndexed { keyIndex, key ->
            val focused = showFocus && state.row == rowIndex && state.column == keyIndex
            val keyRequester = remember(rowIndex, keyIndex) { FocusRequester() }
            keyRequesters[rowIndex to keyIndex] = keyRequester
            KeyCap(
                key = key,
                label = labels.spoken(key),
                stateLabel = if (key.action == KeyAction.Shift) {
                    if (state.shift) labels.shiftOn else labels.shiftOff
                } else {
                    null
                },
                focused = focused,
                active = state.shift && key.action == KeyAction.Shift,
                scale = scale,
                width = key.width * fit,
                focusRequester = keyRequester,
                onActivate = {
                    state.row = rowIndex
                    state.column = keyIndex
                    onAction(key.action)
                },
                modifier = Modifier.offset(
                    x = scale.dp(x),
                    y = scale.dp(top + rowIndex * KEY_PITCH),
                ),
            )
            x += (key.width + KEY_GAP) * fit
        }
    }
}

internal fun keysHeight(rows: List<List<KeyDef>>): Float =
    rows.size * KEY_HEIGHT + (rows.size - 1) * KEY_GAP

@Composable
fun RenameKeyboardOverlay(
    title: String,
    initial: String,
    scale: TvScale,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
    placeholder: String? = null,
    // A second line under the title (the recovery flow explains what the
    // answer is for); the field and the keys shift down to make room.
    note: String? = null,
) {
    val configuration = LocalConfiguration.current
    val labels = rememberKeyLabels()
    val state = remember {
        val russian = configuration.locales[0].language == "ru"
        KeyboardState(initial, if (russian) KeyboardMode.CYRILLIC else KeyboardMode.LATIN)
    }
    val rows = state.rows(labels)
    val noteShift = if (note != null) NOTE_SHIFT else 0f
    val focusRequester = remember { FocusRequester() }
    val keyRepeat = rememberTvKeyRepeat()
    val scope = rememberCoroutineScope()

    fun activate(action: KeyAction) {
        if (action == KeyAction.Done) {
            val name = state.text.trim()
            if (name.isEmpty()) onCancel() else onDone(name)
        } else {
            state.apply(action)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler { onCancel() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    keyRepeat.release(event.key)
                    return@onKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                    return@onKeyEvent true
                }
                fun repeatMove(dx: Int, dy: Int) {
                    state.move(dx, dy, rows)
                    keyRepeat.startRepeat(scope) { state.move(dx, dy, rows) }
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        repeatMove(-1, 0)
                        true
                    }

                    Key.DirectionRight -> {
                        repeatMove(1, 0)
                        true
                    }

                    Key.DirectionUp -> {
                        repeatMove(0, -1)
                        true
                    }

                    Key.DirectionDown -> {
                        repeatMove(0, 1)
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        keyRepeat.stopRepeat()
                        activate(rows[state.row][state.column].action)
                        true
                    }

                    Key.Back -> {
                        keyRepeat.stopRepeat()
                        onCancel()
                        true
                    }

                    Key.Backspace, Key.Delete -> {
                        state.backspace()
                        true
                    }

                    else -> {
                        // A hardware keyboard types in any layout; the remote's
                        // digits arrive through the same path.
                        val code = event.nativeKeyEvent.unicodeChar
                        val char = code.toChar()
                        if (code > 0 && !char.isISOControl()) {
                            state.type(char.toString())
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            .focusable(),
    ) {
        Box(Modifier.fillMaxSize().background(TvInk.scrimModal))

        KeyboardPanel(
            scale = scale,
            width = PANEL_WIDTH,
            modifier = Modifier.offset(x = scale.dp(PANEL_X), y = scale.dp(PANEL_Y)),
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = scale.sp(TvType.body),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .offset(x = scale.dp(CONTENT_PADDING), y = scale.dp(TITLE_TOP))
                    .semantics { heading() },
            )

            if (note != null) {
                Text(
                    text = note,
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = scale.sp(NOTE_FONT),
                    modifier = Modifier
                        .offset(x = scale.dp(CONTENT_PADDING), y = scale.dp(TITLE_TOP + NOTE_TOP)),
                )
            }

            Box(
                Modifier
                    .offset(x = scale.dp(CONTENT_PADDING), y = scale.dp(FIELD_TOP + noteShift))
                    .size(scale.dp(PANEL_WIDTH - CONTENT_PADDING * 2), scale.dp(FIELD_HEIGHT))
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(scale.dp(TvRadius.row)))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(scale.dp(TvRadius.row)),
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Folders and profiles share this overlay; the hint must name
                // what is being typed instead of always saying "Folder name".
                val hint = placeholder ?: stringResource(R.string.keyboard_placeholder)
                Text(
                    text = if (state.text.isEmpty()) hint else "${state.text}|",
                    color = if (state.text.isEmpty()) {
                        Color.White.copy(alpha = 0.30f)
                    } else {
                        Color.White
                    },
                    fontSize = scale.sp(TvType.headline),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .offset(x = scale.dp(24f))
                        // The bar is a caret, not text: TalkBack must not read it.
                        .clearAndSetSemantics {
                            text = AnnotatedString(
                                if (state.text.isEmpty()) hint else state.text,
                            )
                        },
                )
            }

            val keysTop = KEYS_TOP + noteShift +
                ((PANEL_HEIGHT - KEYS_TOP - noteShift - 40f) - keysHeight(rows)) / 2f
            KeyboardKeys(
                state = state,
                labels = labels,
                scale = scale,
                top = keysTop,
                width = PANEL_WIDTH - CONTENT_PADDING * 2,
                onAction = ::activate,
            )
        }
    }
}

@Composable
private fun KeyCap(
    key: KeyDef,
    label: String,
    stateLabel: String?,
    focused: Boolean,
    active: Boolean,
    scale: TvScale,
    width: Float,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvKeyCap(
        text = key.label,
        description = label,
        focused = focused,
        scale = scale,
        width = width,
        height = KEY_HEIGHT,
        radius = KEY_RADIUS,
        fontSize = if (key.label.length > 2) TvType.body else TvType.subhead,
        focusScale = KEY_FOCUS_SCALE,
        focusRequester = focusRequester,
        onActivate = onActivate,
        modifier = modifier,
        active = active,
        stateLabel = stateLabel,
    )
}
