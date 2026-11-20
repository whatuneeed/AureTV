@file:OptIn(ExperimentalTestApi::class)

package app.aure.tv.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aure.tv.R
import app.aure.tv.ui.OVisionTheme
import app.aure.tv.ui.rememberTvScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The layout tests pin the rows; these drive the overlay the way the remote does,
 * because the rename field is the only text input in the launcher and a broken
 * key action silently loses a folder name.
 */
@RunWith(AndroidJUnit4::class)
class RenameKeyboardTest {

    @get:Rule
    val rule = createComposeRule()

    private var done: String? = null
    private var cancelled = false

    private fun setKeyboard(initial: String) {
        rule.setContent {
            OVisionTheme {
                RenameKeyboardOverlay(
                    title = "Название папки",
                    initial = initial,
                    // The panel is laid out in 1920x1080 points; on the test
                    // window a 1:1 scale puts the field below the visible area,
                    // so the screen's own scale is used like the launcher does.
                    scale = rememberTvScale(),
                    onDone = { done = it },
                    onCancel = { cancelled = true },
                )
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
    }

    // Key is a value class, so a vararg of it is not allowed.
    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
    }

    // Row order is letters… then the action row; DOWN clamps on the last row and
    // LEFT clamps on its first key, so the target index needs no row arithmetic.
    private fun actionKey(index: Int) {
        repeat(3) { press(Key.DirectionDown) }
        repeat(10) { press(Key.DirectionLeft) }
        repeat(index) { press(Key.DirectionRight) }
        press(Key.DirectionCenter)
    }

    private fun doneKey() = actionKey(DONE_INDEX)

    @Test
    fun done_returns_the_trimmed_name() {
        setKeyboard("Кино ")

        doneKey()

        assertEquals("Кино", done)
        assertFalse(cancelled)
    }

    @Test
    fun an_empty_name_cancels_instead_of_renaming() {
        setKeyboard("")

        doneKey()

        assertNull(done)
        assertTrue(cancelled)
    }

    @Test
    fun backspace_removes_one_character() {
        setKeyboard("ab")
        rule.onNodeWithText("ab").assertIsDisplayed()
        rule.onNodeWithText("ab|").assertDoesNotExist()

        actionKey(BACKSPACE_INDEX)
        rule.onNodeWithText("a").assertIsDisplayed()

        doneKey()
        assertEquals("a", done)
    }

    @Test
    fun the_mode_toggle_lands_on_the_first_digit() {
        setKeyboard("")

        actionKey(MODE_INDEX)
        press(Key.DirectionCenter)

        rule.onNodeWithText("1").assertIsDisplayed()
        doneKey()
        assertEquals("1", done)
    }

    @Test
    fun shift_makes_the_next_letter_a_capital() {
        setKeyboard("")

        actionKey(SHIFT_INDEX)
        repeat(3) { press(Key.DirectionUp) }
        press(Key.DirectionCenter)

        doneKey()
        assertEquals(1, done?.length ?: 0)
        assertTrue(done!![0].isUpperCase())
    }

    @Test
    fun shift_reports_its_state_instead_of_only_changing_colour() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setKeyboard("")
        val shift = rule.onNodeWithContentDescription(context.getString(R.string.keyboard_shift))

        shift.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            context.getString(R.string.keyboard_shift_off),
        ))
        actionKey(SHIFT_INDEX)
        shift.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            context.getString(R.string.keyboard_shift_on),
        ))
    }

    private companion object {
        // The action row is [Shift, Space, Backspace, mode, mode, Clear, Done]
        // in every language, so these indexes are stable.
        const val SHIFT_INDEX = 0
        const val BACKSPACE_INDEX = 2
        const val MODE_INDEX = 3
        const val DONE_INDEX = 6
    }
}
