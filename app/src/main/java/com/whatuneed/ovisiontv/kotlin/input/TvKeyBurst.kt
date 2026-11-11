package com.whatuneed.ovisiontv.kotlin.input

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity

/**
 * A held button arrives in two different shapes. A TV remote keeps one KeyDown
 * and follows it with repeats that carry `repeatCount > 0`; some remotes and
 * the emulator forwarding a host keyboard send a complete Down/Up pair per
 * repeat instead. Screens read the second shape as a stream of fresh presses:
 * a held toggle flipped back and forth, a held OK restarted the long-press
 * timer and launched the tile, and a held OK in the saver picker toggled the
 * clip instead of opening the preview.
 *
 * This filter merges a burst into one press: the first KeyDown is delivered,
 * the following pairs are swallowed, and the last KeyUp is delivered after
 * [settleMs] of silence. A plain press only gains that short settle delay; a
 * real auto-repeat is passed through untouched.
 */
class TvKeyBurstFilter(
    private val settleMs: Long = SETTLE_MS,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Any,
    private val unschedule: (token: Any) -> Unit,
    private val now: () -> Long = { SystemClock.uptimeMillis() },
) {

    private class Held(val token: Any, val at: Long, val release: () -> Unit)

    // Keys whose first KeyDown reached the screen and whose KeyUp is not out
    // yet. A KeyDown that finds its key here is a repeat, not a new press.
    private val pressed = mutableSetOf<Int>()

    // Releases waiting for the settle window, one per key.
    private val held = mutableMapOf<Int, Held>()

    /**
     * Returns true when this event belongs to the filtered stream, so the
     * caller must not dispatch it again. [dispatch] is called with the events
     * the screen is allowed to see, at the moment it may see them.
     */
    fun <T> consume(event: T, action: Int, keyCode: Int, dispatch: (T) -> Unit): Boolean {
        if (!isMerged(keyCode)) return false
        when (action) {
            KeyEvent.ACTION_DOWN -> {
                val pending = held[keyCode]
                if (pending != null) {
                    if (now() - pending.at <= LOST_UP_MS) {
                        // The next pair of the same burst: the press never
                        // ended, so both its release and this press are noise.
                        unschedule(pending.token)
                        held.remove(keyCode)
                        return true
                    }
                    // No release for over a second: the KeyUp was lost. Close
                    // that press before this genuinely new one.
                    held.remove(keyCode)
                    unschedule(pending.token)
                    pending.release()
                }
                if (keyCode in pressed) {
                    // Real auto-repeat (repeatCount > 0): keep it, screens
                    // already know how to ignore or pace it.
                    dispatch(event)
                    return true
                }
                pressed += keyCode
                dispatch(event)
                return true
            }

            KeyEvent.ACTION_UP -> {
                if (keyCode !in pressed) return false
                held.remove(keyCode)?.let { unschedule(it.token) }
                val release = {
                    if (pressed.remove(keyCode)) dispatch(event)
                }
                lateinit var token: Any
                token = schedule(settleMs) {
                    held.remove(keyCode)
                    release()
                }
                held[keyCode] = Held(token, now(), release)
                return true
            }

            else -> return false
        }
    }

    /** Drops everything pending; releases are not delivered after shutdown. */
    fun reset() {
        held.values.forEach { unschedule(it.token) }
        held.clear()
        pressed.clear()
    }

    companion object {
        /**
         * Silence after a release that marks the end of a burst. Host keys
         * repeat in pairs tens of milliseconds apart, so a press that is still
         * held never waits this long between its parts.
         */
        const val SETTLE_MS = 60L

        /** A held release older than this is treated as a lost KeyUp. */
        const val LOST_UP_MS = 1_000L

        fun isMerged(keyCode: Int): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST,
            -> true

            else -> false
        }
    }
}

/**
 * All launcher activities go through the burst filter, so a held button
 * behaves the same on Home, in Settings and in the first-run wizard.
 *
 * Overriding and continuing `ComponentActivity.dispatchKeyEvent` is the point
 * of this class; lint marks that internal androidx seam as restricted.
 */
@SuppressLint("RestrictedApi")
abstract class TvRemoteActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val burst = TvKeyBurstFilter(
        schedule = { delayMs, action ->
            val runnable = Runnable { action() }
            handler.postDelayed(runnable, delayMs)
            runnable
        },
        unschedule = { handler.removeCallbacks(it as Runnable) },
    )

    private fun dispatchToWindow(event: KeyEvent): Boolean =
        if (isFinishing || isDestroyed) false else super.dispatchKeyEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (burst.consume(event, event.action, event.keyCode) { dispatchToWindow(it) }) return true
        return dispatchToWindow(event)
    }

    override fun onDestroy() {
        burst.reset()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
