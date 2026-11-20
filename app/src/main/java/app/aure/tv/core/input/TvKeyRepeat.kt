package app.aure.tv.core.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launcher owns key repeat instead of following the keyboard.
 *
 * Android repeats a held key 30+ times a second with no initial pause, and the
 * emulator forwards host keys as fresh presses with repeatCount 0. Both used to
 * restart the long-OK timers on every repeat, so edit mode and the saver
 * preview never opened. Here a physical press is recognised by asking whether
 * the same key was seen recently, and arrows repeat at a slow, fixed pace.
 */
@Stable
class TvKeyRepeat {

    // eventTime of the last KeyDown per key. A KeyUp removes the entry, so the
    // next press is first again; a lost KeyUp recovers after [STALE_MS].
    private val lastDown = mutableMapOf<Key, Long>()
    private var repeatJob: Job? = null

    /** True only for the first KeyDown of one physical press. */
    fun firstPress(event: KeyEvent): Boolean {
        val time = event.nativeKeyEvent.eventTime
        val previous = lastDown[event.key]
        lastDown[event.key] = time
        if (event.nativeKeyEvent.repeatCount > 0) return false
        return previous == null || time - previous > STALE_MS
    }

    fun release(key: Key) {
        lastDown.remove(key)
        stopRepeat()
    }

    fun startRepeat(scope: CoroutineScope, action: () -> Unit) {
        stopRepeat()
        repeatJob = scope.launch {
            delay(REPEAT_DELAY_MS)
            while (true) {
                action()
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    fun stopRepeat() {
        repeatJob?.cancel()
        repeatJob = null
    }

    fun reset() {
        stopRepeat()
        lastDown.clear()
    }

    companion object {
        /** Pause before a held arrow starts moving, as on a TV remote. */
        const val REPEAT_DELAY_MS = 400L

        /** One step per repeat; the untouched system rate is ~3x faster. */
        const val REPEAT_INTERVAL_MS = 100L

        /** No events for this long means the KeyUp was lost, not a repeat. */
        const val STALE_MS = 1200L

        /** Keys that act once per press; text keys keep their own repeat. */
        fun isOneShot(key: Key): Boolean = when (key) {
            Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
            Key.Back, Key.Menu, Key.Search, Key.Assist,
            -> true

            else -> false
        }
    }
}

@Composable
fun rememberTvKeyRepeat(): TvKeyRepeat {
    val repeat = remember { TvKeyRepeat() }
    DisposableEffect(repeat) {
        onDispose { repeat.reset() }
    }
    return repeat
}
