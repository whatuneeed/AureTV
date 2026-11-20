package com.whatuneed.ovisiontv.kotlin.ui.components

import com.whatuneed.ovisiontv.kotlin.core.input.TvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.core.input.rememberTvKeyRepeat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.KidsMode
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.PinDigits
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.SurfaceEasing
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import com.whatuneed.ovisiontv.kotlin.ui.TvType

/**
 * The PIN gate is a project surface: tvOS asks for a passcode with its own
 * full-screen pad (tvos 13_PASSCODE), and the shape follows it, but every
 * measurement here is the launcher's own — Apple's pad is UNKNOWN in research.
 * The Java build used a four-row digit grid; the same grid stays, drawn with
 * the launcher's key caps so focus looks like every other surface.
 */
internal enum class PinPadMode { VERIFY, SET, RECOVER }

/** Grid of the pad, pure so the layout is testable without Compose. */
internal object PinPadLayout {
    const val COLUMNS = 3
    // ⌫, 0, OK — the bottom row of the Java pad, in the same order.
    val ROWS = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("\u232B", "0", "OK"),
    )

    fun isDigit(label: String): Boolean = label.length == 1 && label[0] in '0'..'9'

    fun move(row: Int, column: Int, dRow: Int, dColumn: Int): Pair<Int, Int> {
        val nextRow = (row + dRow).coerceIn(0, ROWS.lastIndex)
        return nextRow to (column + dColumn).coerceIn(0, ROWS[nextRow].lastIndex)
    }
}

// Panel geometry in tvOS points (1920x1080). The pad is a centred card, not a
// bottom sheet: the gate must read as its own page over the dimmed Home.
// The height must hold title + subtitle + dots + four key rows + BOTH note
// lines (error/lockout AND "Forgot PIN") at once: with the subtitle present
// that content measures ~895, and a shorter card clips the last line out of
// both the card and the accessibility tree.
private const val PAD_WIDTH = 560f
private const val PAD_HEIGHT = 920f
private const val PAD_RADIUS = TvRadius.panel
private const val PAD_CONTENT = 48f
private const val TITLE_FONT = TvType.headline
private const val SUBTITLE_FONT = TvType.body
private const val SUBTITLE_TOP = 18f
private const val DOTS_TOP = 40f
private const val DOT = 18f
private const val DOT_GAP = 22f
private const val KEYS_TOP = 150f
private const val KEY_SIZE = 110f
private const val PAD_KEY_GAP = 18f
private const val KEY_RADIUS = TvRadius.key
private const val KEY_FONT = TvType.headline
private const val PIN_KEY_FOCUS_SCALE = 1.08f
private const val NOTE_TOP = 34f
private const val NOTE_FONT = TvType.body

private const val LOCK_TICK_MS = 1_000L

/**
 * Full-screen PIN gate. VERIFY checks the stored PIN (auto-submit at four
 * digits, like the Java pad), SET collects a new one and reports it on OK.
 * RECOVER follows Java's forgot flow: the word is typed on the text keyboard
 * (the digit grid cannot spell "Фима"), a correct answer clears the failures
 * and asks for a fresh PIN, and finishing closes the gate WITHOUT performing
 * the gated action — recovery resets the PIN, it does not bypass the gate.
 *
 * The host owns the store: the pad only reports success, so the same surface
 * gates Settings, profile switching and the set/recovery flows in Settings.
 */
@Composable
internal fun PinPadOverlay(
    mode: PinPadMode,
    profileId: String,
    reduceMotion: Boolean,
    onPassed: () -> Unit,
    // Recovery completion: the PIN has been replaced. The host closes the
    // gate without running the gated action (Java closes the pad and stays).
    onRecovered: (() -> Unit)? = null,
    onCancel: () -> Unit,
    onForgot: (() -> Unit)? = null,
    // The mode's default subtitle explains the launcher's exit gate; a host
    // that gates a different surface (Settings) names that surface instead.
    subtitle: String? = null,
) {
    if (mode == PinPadMode.RECOVER) {
        RecoveryFlow(
            profileId = profileId,
            reduceMotion = reduceMotion,
            onRecovered = onRecovered,
            onCancel = onCancel,
        )
        return
    }
    val context = LocalContext.current
    val scale = rememberTvScale()
    val store = remember(context) { KidsStore(context) }
    var digits by remember(mode) { mutableStateOf(PinDigits()) }
    var row by remember { mutableIntStateOf(0) }
    var column by remember { mutableIntStateOf(0) }
    // The forgot link sits below the grid; the pad Box owns the D-pad, so it
    // routes focus to the link itself (Java made the link a focusable button).
    var forgotFocused by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteIsError by remember { mutableStateOf(false) }
    var lockSeconds by remember { mutableLongStateOf(0L) }
    // PBKDF2 runs off the main thread (audit A-03). While it does, the pad
    // refuses input: a second OK during the hash would start a parallel check
    // and could count one guess as two failures.
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Job?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    val requesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    fun requester(r: Int, c: Int): FocusRequester = requesters.getOrPut(r to c) { FocusRequester() }
    val forgotRequester = remember { FocusRequester() }
    val keyRepeat = rememberTvKeyRepeat()
    val scope = rememberCoroutineScope()

    val title = stringResource(
        when (mode) {
            PinPadMode.VERIFY -> R.string.kids_pin_title
            PinPadMode.SET -> R.string.kids_pin_new
            PinPadMode.RECOVER -> R.string.kids_recovery_answer_title
        },
    )
    val subtitleText = subtitle ?: when (mode) {
        PinPadMode.VERIFY -> stringResource(R.string.kids_pin_leave)
        PinPadMode.SET -> null
        PinPadMode.RECOVER -> store.recoveryHint(profileId)
            .ifEmpty { stringResource(R.string.kids_recovery_answer_title) }
    }
    val need4 = stringResource(R.string.kids_pin_need4)
    val wrong = stringResource(R.string.kids_pin_wrong)
    val saved = stringResource(R.string.kids_pin_saved)
    val damaged = stringResource(R.string.kids_pin_damaged)
    val lockTemplate = stringResource(R.string.kids_lock_wait)
    val forgot = stringResource(R.string.kids_forgot)

    fun showNote(text: String, error: Boolean) {
        note = text
        noteIsError = error
    }

    fun refreshLock() {
        val ms = KidsMode.lockRemaining(context, profileId)
        lockSeconds = if (ms > 0) (ms + 999) / 1000 else 0L
    }

    LaunchedEffect(mode, profileId, lockSeconds > 0) {
        refreshLock()
        while (lockSeconds > 0) {
            kotlinx.coroutines.delay(LOCK_TICK_MS)
            refreshLock()
        }
    }

    fun dismiss(action: () -> Unit) {
        if (dismissed) return
        dismissed = true
        pending?.cancel()
        keyRepeat.stopRepeat()
        action()
    }

    fun submit() {
        if (busy || dismissed) return
        val value = digits.value
        // A short code can never match the stored four digits, so OK on an
        // empty or partial pad is a nudge, not a wrong guess: counting it would
        // let stray OK presses feed the lockout.
        if (!digits.complete) {
            showNote(need4, true)
            return
        }
        if (mode == PinPadMode.VERIFY && KidsMode.lockRemaining(context, profileId) > 0) {
            digits = PinDigits()
            refreshLock()
            return
        }
        if (mode == PinPadMode.VERIFY && store.pinDamaged(profileId)) {
            digits = PinDigits()
            showNote(damaged, true)
            return
        }
        busy = true
        pending = scope.launch {
            try {
                when (mode) {
                    PinPadMode.SET -> {
                        store.setPin(profileId, value)
                        showNote(saved, false)
                        dismiss(onPassed)
                    }

                    PinPadMode.VERIFY -> {
                        when (store.verifyPin(profileId, value)) {
                            KidsStore.Verification.PASSED -> dismiss(onPassed)
                            KidsStore.Verification.WRONG -> {
                                digits = PinDigits()
                                refreshLock()
                                showNote(wrong, true)
                            }
                            KidsStore.Verification.LOCKED, KidsStore.Verification.CHANGED -> {
                                digits = PinDigits()
                                refreshLock()
                            }
                        }
                    }

                    // Unreachable: RECOVER is routed to RecoveryFlow before the
                    // digit pad is built; the branch only satisfies the compiler.
                    PinPadMode.RECOVER -> Unit
                }
            } finally {
                busy = false
            }
        }
    }

    fun press(label: String) {
        if (busy || dismissed) return
        when {
            label == "\u232B" -> digits = digits.backspace()
            label == "OK" -> submit()
            PinPadLayout.isDigit(label) -> {
                val next = digits.append(label[0])
                digits = next
                // VERIFY and RECOVER submit on the fourth digit; SET waits for
                // OK so a mistyped digit can still be removed.
                if (next.complete && mode != PinPadMode.SET) submit()
            }
        }
    }

    // Cold starts (task restore, adb) compose the pad BEFORE the window gains
    // input focus, and a requestFocus made that early is silently lost — the
    // pad would render but swallow every key except Back. Retry on the next
    // frames instead of waiting a blind 400 ms.
    LaunchedEffect(Unit) {
        requesters[0 to 0]?.requestWhenAttached()
    }

    // The visible cursor is the source of truth; Android focus must follow every
    // step so TalkBack reads the digit the user is actually on, and the forgot
    // link is a real target too.
    LaunchedEffect(row, column, forgotFocused, mode, onForgot) {
        if (dismissed) return@LaunchedEffect
        if (forgotFocused && mode == PinPadMode.VERIFY && onForgot != null) {
            forgotRequester.requestWhenAttached()
        } else {
            requesters[row to column]?.requestWhenAttached()
        }
    }

    BackHandler { dismiss(onCancel) }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion), easing = SurfaceEasing),
        label = "pinPadAlpha",
    )
    val enterSlide by animateFloatAsState(
        targetValue = if (entered) 0f else TvOs.SURFACE_SLIDE,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion), easing = SurfaceEasing),
        label = "pinPadSlide",
    )

    Box(
        Modifier
            .fillMaxSize()
            .testTag(if (busy) "pin-pad-busy" else "pin-pad-ready")
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    keyRepeat.release(event.key)
                    return@onKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (dismissed || (busy && event.key != Key.Back)) return@onKeyEvent true
                if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                    return@onKeyEvent true
                }
                fun repeatMove(dRow: Int, dColumn: Int) {
                    val (r, c) = PinPadLayout.move(row, column, dRow, dColumn)
                    row = r
                    column = c
                    keyRepeat.startRepeat(scope) {
                        val (nr, nc) = PinPadLayout.move(row, column, dRow, dColumn)
                        row = nr
                        column = nc
                    }
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (forgotFocused) {
                            keyRepeat.stopRepeat()
                        } else {
                            repeatMove(0, -1)
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        if (forgotFocused) {
                            keyRepeat.stopRepeat()
                        } else {
                            repeatMove(0, 1)
                        }
                        true
                    }

                    Key.DirectionUp -> {
                        if (forgotFocused) {
                            keyRepeat.stopRepeat()
                            forgotFocused = false
                            row = PinPadLayout.ROWS.lastIndex
                        } else {
                            repeatMove(-1, 0)
                        }
                        true
                    }

                    Key.DirectionDown -> {
                        if (forgotFocused) {
                            keyRepeat.stopRepeat()
                        } else if (row == PinPadLayout.ROWS.lastIndex &&
                            mode == PinPadMode.VERIFY &&
                            onForgot != null
                        ) {
                            keyRepeat.stopRepeat()
                            forgotFocused = true
                        } else {
                            repeatMove(1, 0)
                        }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        keyRepeat.stopRepeat()
                        if (forgotFocused) {
                            onForgot?.let { dismiss(it) }
                        } else {
                            press(PinPadLayout.ROWS[row][column])
                        }
                        true
                    }

                    Key.Back -> {
                        dismiss(onCancel)
                        true
                    }

                    Key.Backspace, Key.Delete -> {
                        digits = digits.backspace()
                        true
                    }

                    else -> {
                        // The remote's number keys arrive as plain digits, the
                        // same path the Java pad used.
                        val code = event.nativeKeyEvent.unicodeChar
                        val char = code.toChar()
                        if (code > 0 && char in '0'..'9') {
                            forgotFocused = false
                            press(char.toString())
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
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = enterAlpha
                    translationY = enterSlide * density
                }
                .size(scale.dp(PAD_WIDTH), scale.dp(PAD_HEIGHT))
                .clip(RoundedCornerShape(scale.dp(PAD_RADIUS)))
                .background(TvInk.panel.copy(alpha = 0.97f))
                .border(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)),
                    ),
                    shape = RoundedCornerShape(scale.dp(PAD_RADIUS)),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(scale.dp(PAD_CONTENT)))
            Text(
                text = title,
                color = LauncherColors.textPrimary,
                fontSize = scale.sp(TITLE_FONT),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics { heading(); contentDescription = title },
            )
            if (subtitleText != null) {
                Spacer(Modifier.height(scale.dp(SUBTITLE_TOP)))
                Text(
                    text = subtitleText,
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(SUBTITLE_FONT),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(scale.dp(DOTS_TOP)))
            Row(horizontalArrangement = Arrangement.spacedBy(scale.dp(DOT_GAP))) {
                repeat(PinDigits.PIN_LENGTH) { index ->
                    val filled = index < digits.value.length
                    Box(
                        Modifier
                            .size(scale.dp(DOT))
                            .clip(CircleShape)
                            .background(
                                if (filled) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.25f)
                                },
                            ),
                    )
                }
            }
            Spacer(Modifier.height(scale.dp(KEYS_TOP - DOTS_TOP - DOT)))
            PinPadLayout.ROWS.forEachIndexed { rowIndex, labels ->
                Row(horizontalArrangement = Arrangement.spacedBy(scale.dp(PAD_KEY_GAP))) {
                    labels.forEachIndexed { keyIndex, label ->
                        PinKey(
                            label = label,
                            focused = row == rowIndex && column == keyIndex,
                            scale = scale,
                            focusRequester = requester(rowIndex, keyIndex),
                            onActivate = {
                                row = rowIndex
                                column = keyIndex
                                press(label)
                            },
                        )
                    }
                }
                if (rowIndex != PinPadLayout.ROWS.lastIndex) {
                    Spacer(Modifier.height(scale.dp(PAD_KEY_GAP)))
                }
            }
            val noteText = when {
                lockSeconds > 0 -> String.format(java.util.Locale.ROOT, lockTemplate, lockSeconds)
                note != null -> note
                else -> null
            }
            val showForgot = mode == PinPadMode.VERIFY && onForgot != null
            // Both lines stay visible together: an error must not hide the way
            // out, and the link must not hide the error (Java kept the forgot
            // button below the grid at all times).
            if (noteText != null || showForgot) {
                Spacer(Modifier.height(scale.dp(NOTE_TOP)))
                if (noteText != null) {
                    Text(
                        text = noteText,
                        color = if (noteIsError || lockSeconds > 0) {
                            LauncherColors.destructive
                        } else {
                            LauncherColors.textSecondary
                        },
                        fontSize = scale.sp(NOTE_FONT),
                        textAlign = TextAlign.Center,
                    )
                }
                if (showForgot) {
                    if (noteText != null) {
                        Spacer(Modifier.height(scale.dp(6f)))
                    }
                    Text(
                        text = forgot,
                        color = if (forgotFocused) {
                            LauncherColors.textPrimary
                        } else {
                            LauncherColors.textSecondary
                        },
                        fontWeight = if (forgotFocused) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = scale.sp(NOTE_FONT),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .focusRequester(forgotRequester)
                            .focusable()
                            .clearAndSetSemantics {
                                contentDescription = forgot
                                role = Role.Button
                                onClick(label = forgot) {
                                    onForgot?.let { dismiss(it) }
                                    true
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    focused: Boolean,
    scale: TvScale,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
) {
    TvKeyCap(
        text = label,
        description = label,
        focused = focused,
        scale = scale,
        width = KEY_SIZE,
        height = KEY_SIZE,
        radius = KEY_RADIUS,
        fontSize = KEY_FONT,
        focusScale = PIN_KEY_FOCUS_SCALE,
        focusRequester = focusRequester,
        onActivate = onActivate,
    )
}

/**
 * Java's forgot flow: the recovery word is typed on the text keyboard (the
 * digit grid cannot spell "Фима"), a correct answer clears the failures and
 * opens the new-PIN pad; finishing there reports success WITHOUT performing
 * the gated action — recovery resets the PIN, it does not bypass the gate.
 * The stored hint is shown in the note line; a wrong answer counts as a pad
 * failure and feeds the same lockout.
 */
@Composable
private fun RecoveryFlow(
    profileId: String,
    reduceMotion: Boolean,
    onRecovered: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scale = rememberTvScale()
    val store = remember(context) { KidsStore(context) }
    var setPinPhase by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(false) }
    var lockSeconds by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Job?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun cancel() {
        if (dismissed) return
        dismissed = true
        pending?.cancel()
        onCancel()
    }

    val title = stringResource(R.string.kids_gate_recover_title)
    val wrongAnswer = stringResource(R.string.kids_recovery_wrong)
    val lockTemplate = stringResource(R.string.kids_lock_wait)
    val placeholder = stringResource(R.string.kids_recovery_answer_placeholder)

    fun refreshLock() {
        val ms = KidsMode.lockRemaining(context, profileId)
        lockSeconds = if (ms > 0) (ms + 999) / 1000 else 0L
    }

    if (setPinPhase) {
        PinPadOverlay(
            mode = PinPadMode.SET,
            profileId = profileId,
            reduceMotion = reduceMotion,
            onPassed = onRecovered ?: onCancel,
            onCancel = onCancel,
        )
        return
    }

    LaunchedEffect(profileId, lockSeconds > 0) {
        refreshLock()
        while (lockSeconds > 0) {
            kotlinx.coroutines.delay(LOCK_TICK_MS)
            refreshLock()
        }
    }

    val hint = store.recoveryHint(profileId)
    val recoveryDamaged = store.recoveryDamaged(profileId)
    val damagedNote = stringResource(R.string.kids_pin_damaged)
    val note = when {
        recoveryDamaged -> damagedNote
        lockSeconds > 0 -> String.format(java.util.Locale.ROOT, lockTemplate, lockSeconds)
        wrong && hint.isNotEmpty() -> "$hint\n$wrongAnswer"
        wrong -> wrongAnswer
        hint.isNotEmpty() -> hint
        else -> null
    }

    // key(attempt) gives each try a fresh keyboard: the remembered field would
    // otherwise carry the rejected answer into the next attempt (the same
    // trick the two-step recovery setup in Settings uses).
    key(attempt) {
        RenameKeyboardOverlay(
            title = title,
            initial = "",
            scale = scale,
            note = note,
            placeholder = placeholder,
            onDone = { answer ->
                if (busy || dismissed) return@RenameKeyboardOverlay
                if (recoveryDamaged) {
                    // A broken recovery hash can never pass; retyping the same
                    // answer would only add lockout attempts.
                    attempt++
                    return@RenameKeyboardOverlay
                }
                if (KidsMode.lockRemaining(context, profileId) > 0) {
                    wrong = false
                    attempt++
                } else {
                    busy = true
                    pending = scope.launch {
                        try {
                            when (store.verifyRecovery(profileId, answer)) {
                                KidsStore.Verification.PASSED -> setPinPhase = true
                                KidsStore.Verification.WRONG -> wrong = true
                                KidsStore.Verification.LOCKED, KidsStore.Verification.CHANGED -> wrong = false
                            }
                            refreshLock()
                            attempt++
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onCancel = { cancel() },
        )
    }
}

// A requester created in this composition is attached in the next layout pass;
// retrying on the following frames avoids a blind millisecond delay and keeps
// the request safe after a data change removed the previous node.
private suspend fun FocusRequester.requestWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
