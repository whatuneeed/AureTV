package app.aure.tv.ui.components

import app.aure.tv.data.PinDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pad grid is shared by verify, set and recovery; the layout is pinned here. */
class PinPadLayoutTest {

    @Test
    fun theGridMatchesTheJavaPad() {
        assertEquals(4, PinPadLayout.ROWS.size)
        assertEquals(listOf("1", "2", "3"), PinPadLayout.ROWS[0])
        assertEquals(listOf("4", "5", "6"), PinPadLayout.ROWS[1])
        assertEquals(listOf("7", "8", "9"), PinPadLayout.ROWS[2])
        assertEquals(listOf("\u232B", "0", "OK"), PinPadLayout.ROWS[3])
    }

    @Test
    fun everyRowHasThreeKeys() {
        PinPadLayout.ROWS.forEach { assertEquals(3, it.size) }
    }

    @Test
    fun digitsAreRecognisedAndOtherKeysAreNot() {
        PinPadLayout.ROWS.flatten().forEach { label ->
            assertEquals(label[0] in '0'..'9', PinPadLayout.isDigit(label))
        }
    }

    @Test
    fun arrowsStopAtTheGridEdges() {
        assertEquals(0 to 0, PinPadLayout.move(0, 0, -1, -1))
        assertEquals(3 to 2, PinPadLayout.move(3, 2, 1, 1))
        assertEquals(0 to 2, PinPadLayout.move(1, 2, -1, 0))
        assertEquals(3 to 0, PinPadLayout.move(2, 0, 1, -1))
    }

    @Test
    fun verticalMovesKeepTheColumn() {
        assertEquals(1 to 1, PinPadLayout.move(0, 1, 1, 0))
        assertEquals(2 to 1, PinPadLayout.move(3, 1, -1, 0))
    }
}

/** The digits buffer is the state behind the dots; its four-cap rule is pinned. */
class PinDigitsTest {

    @Test
    fun fourDigitsFillAndFurtherDigitsAreIgnored() {
        var digits = PinDigits()
        "12345".forEach { digits = digits.append(it) }
        assertEquals("1234", digits.value)
        assertTrue(digits.complete)
    }

    @Test
    fun lettersNeverEnterTheBuffer() {
        var digits = PinDigits()
        "a1b2c3d4".forEach { digits = digits.append(it) }
        assertEquals("1234", digits.value)
    }

    @Test
    fun backspaceOnEmptyStaysEmpty() {
        val digits = PinDigits("12").backspace().backspace().backspace()
        assertEquals("", digits.value)
        assertFalse(digits.complete)
    }
}
