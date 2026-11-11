package com.whatuneed.ovisiontv.kotlin.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// The engine decides what one press is: the emulator reports auto-repeats as
// fresh presses, and Android's own repeat rate is too fast for a TV grid.
@RunWith(AndroidJUnit4::class)
class TvKeyRepeatTest {

    private fun event(code: Int, time: Long, repeat: Int = 0) = KeyEvent(
        AndroidKeyEvent(time, time, AndroidKeyEvent.ACTION_DOWN, code, repeat),
    )

    @Test
    fun a_burst_of_key_downs_counts_as_one_press() {
        val repeat = TvKeyRepeat()
        val code = AndroidKeyEvent.KEYCODE_DPAD_LEFT

        assertTrue(repeat.firstPress(event(code, 1_000)))
        assertFalse(repeat.firstPress(event(code, 1_030)))
        assertFalse(repeat.firstPress(event(code, 1_060)))

        // The release ends the press, so the next one acts again.
        repeat.release(Key.DirectionLeft)
        assertTrue(repeat.firstPress(event(code, 1_400)))
    }

    @Test
    fun a_lost_release_recovers_after_the_stale_window() {
        val repeat = TvKeyRepeat()
        val code = AndroidKeyEvent.KEYCODE_DPAD_UP

        assertTrue(repeat.firstPress(event(code, 1_000)))
        assertFalse(repeat.firstPress(event(code, 1_100)))
        // The window is measured from the last event seen, not from the press.
        assertTrue(repeat.firstPress(event(code, 1_100 + TvKeyRepeat.STALE_MS + 1)))
    }

    @Test
    fun the_platform_repeat_count_is_not_trusted_alone() {
        val repeat = TvKeyRepeat()
        val code = AndroidKeyEvent.KEYCODE_DPAD_DOWN

        // Even the first event of a press may carry a repeat count when the
        // screen receives it mid-hold.
        assertFalse(repeat.firstPress(event(code, 1_000, repeat = 3)))
    }

    @Test
    fun text_keys_keep_their_native_repeat() {
        assertFalse(TvKeyRepeat.isOneShot(Key.A))
        assertFalse(TvKeyRepeat.isOneShot(Key.Backspace))
        assertTrue(TvKeyRepeat.isOneShot(Key.DirectionCenter))
        assertTrue(TvKeyRepeat.isOneShot(Key.Enter))
        assertTrue(TvKeyRepeat.isOneShot(Key.Back))
    }

    @Test
    fun a_held_arrow_repeats_slowly_and_stops_on_release() = runBlocking {
        val repeat = TvKeyRepeat()
        var steps = 0
        repeat.startRepeat(this) { steps++ }

        // Nothing before the initial delay, then one step per interval: the
        // first at 400 ms, so 800 ms is the fourth step.
        delay(300)
        assertEquals(0, steps)
        delay(500)
        repeat.stopRepeat()
        assertTrue("steps=$steps", steps in 3..5)

        val afterRelease = steps
        delay(250)
        assertEquals(afterRelease, steps)
    }
}
