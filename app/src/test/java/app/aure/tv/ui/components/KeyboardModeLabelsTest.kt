package app.aure.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardModeLabelsTest {

    private val labels = KeyLabels(
        space = "Пробел",
        clear = "Стереть",
        done = "Готово",
        backspace = "Удалить",
        shift = "Регистр",
        shiftOn = "включён",
        shiftOff = "выключен",
        modeLatin = "Латинские буквы",
        modeCyrillic = "Кириллица",
        modeDigits = "Цифры и символы",
    )

    private fun modeKeys(mode: KeyboardMode) =
        KeyboardState("", mode).rows(labels).flatten().filter { it.action is KeyAction.Mode }

    @Test
    fun every_layout_switch_has_its_own_spoken_name() {
        val spoken = KeyboardMode.entries.flatMap { modeKeys(it) }.map { it.spoken }.toSet()

        assertEquals(
            setOf("Латинские буквы", "Кириллица", "Цифры и символы"),
            spoken,
        )
    }

    @Test
    fun a_mode_key_without_a_spoken_name_falls_back_to_its_label() {
        val key = KeyDef("abc", 100f, KeyAction.Mode(KeyboardMode.LATIN))

        assertEquals("abc", labels.spoken(key))
    }
}
