package app.aure.tv.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Some remotes (and the emulator forwarding a host keyboard) send a held
 * button as repeated Down/Up pairs. These tests pin the merge rule that keeps
 * such a hold one press for every screen, while a real auto-repeat and a
 * sequence of separate presses still reach the screen.
 */
class TvKeyBurstFilterTest {

    private class FakeScheduler {
        var now = 0L
        private var nextId = 0
        private val tasks = mutableMapOf<Int, Task>()
        private class Task(val due: Long, val action: () -> Unit)

        fun schedule(delayMs: Long, action: () -> Unit): Any {
            val id = nextId++
            tasks[id] = Task(now + delayMs, action)
            return id
        }

        fun unschedule(token: Any) {
            tasks.remove(token as Int)
        }

        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val due = tasks.entries
                    .filter { it.value.due <= target }
                    .minByOrNull { it.value.due } ?: break
                now = due.value.due
                tasks.remove(due.key)
                due.value.action()
            }
            now = target
        }
    }

    private val scheduler = FakeScheduler()
    private val sent = mutableListOf<String>()

    private fun filter(settleMs: Long = 60L) = TvKeyBurstFilter(
        settleMs = settleMs,
        schedule = scheduler::schedule,
        unschedule = scheduler::unschedule,
        now = { scheduler.now },
    )

    private fun TvKeyBurstFilter.press(keyCode: Int, name: String = "press") {
        assertTrue(consume("$name-down", KeyEvent.ACTION_DOWN, keyCode) { sent += it })
        assertTrue(consume("$name-up", KeyEvent.ACTION_UP, keyCode) { sent += it })
    }

    private fun TvKeyBurstFilter.down(keyCode: Int, name: String = "press") {
        assertTrue(consume("$name-down", KeyEvent.ACTION_DOWN, keyCode) { sent += it })
    }

    private fun TvKeyBurstFilter.up(keyCode: Int, name: String = "press") {
        assertTrue(consume("$name-up", KeyEvent.ACTION_UP, keyCode) { sent += it })
    }

    private val ok = KeyEvent.KEYCODE_DPAD_CENTER

    @Test
    fun a_plain_press_reaches_the_screen_once_after_the_settle() {
        val burst = filter()
        burst.press(ok)
        assertEquals(listOf("press-down"), sent)

        scheduler.advance(59)
        assertEquals(listOf("press-down"), sent)
        scheduler.advance(1)
        assertEquals(listOf("press-down", "press-up"), sent)
    }

    @Test
    fun a_held_button_sent_as_pairs_is_one_press() {
        val burst = filter()
        burst.down(ok)
        repeat(5) {
            scheduler.advance(20)
            burst.up(ok)
            scheduler.advance(20)
            burst.down(ok)
        }
        scheduler.advance(20)
        burst.up(ok)
        scheduler.advance(60)

        assertEquals(listOf("press-down", "press-up"), sent)
    }

    @Test
    fun a_held_arrow_sent_as_pairs_moves_once() {
        val burst = filter()
        val right = KeyEvent.KEYCODE_DPAD_RIGHT
        burst.down(right, "right")
        repeat(4) {
            burst.up(right, "right")
            burst.down(right, "right")
        }
        burst.up(right, "right")
        scheduler.advance(60)

        assertEquals(listOf("right-down", "right-up"), sent)
    }

    @Test
    fun two_presses_with_a_pause_are_two_presses() {
        val burst = filter()
        burst.press(ok, "first")
        scheduler.advance(60)
        burst.press(ok, "second")
        scheduler.advance(60)

        assertEquals(
            listOf("first-down", "first-up", "second-down", "second-up"),
            sent,
        )
    }

    @Test
    fun a_real_auto_repeat_is_passed_through() {
        val burst = filter()
        burst.down(ok, "first")
        burst.down(ok, "repeat-1")
        burst.down(ok, "repeat-2")
        burst.up(ok, "first")
        scheduler.advance(60)

        assertEquals(
            listOf("first-down", "repeat-1-down", "repeat-2-down", "first-up"),
            sent,
        )
    }

    @Test
    fun text_keys_are_not_merged() {
        val burst = filter()
        val a = KeyEvent.KEYCODE_A
        assertFalse(burst.consume("t", KeyEvent.ACTION_DOWN, a) { sent += it })
        assertFalse(burst.consume("t", KeyEvent.ACTION_UP, a) { sent += it })
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun a_lost_release_is_closed_before_a_new_press() {
        val burst = filter(settleMs = 2_000L)
        burst.down(ok, "lost")
        burst.up(ok, "lost")
        // The settle callback never ran, so the press looks still open.
        scheduler.advance(1_200)
        burst.down(ok, "next")
        burst.up(ok, "next")
        scheduler.advance(2_000)

        assertEquals(listOf("lost-down", "lost-up", "next-down", "next-up"), sent)
    }
}
