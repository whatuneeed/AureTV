package com.whatuneed.ovisiontv.kotlin.ui.components

import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusLightTest {

    @Test
    fun at_rest_the_light_is_off() {
        assertEquals(0f, focusLightAlpha(1f), 0.0001f)
    }

    @Test
    fun at_the_focused_size_the_light_is_full() {
        assertEquals(1f, focusLightAlpha(TvOs.FOCUS_SCALE), 0.0001f)
    }

    @Test
    fun the_light_follows_the_growth_between_the_ends() {
        val half = focusLightAlpha((1f + TvOs.FOCUS_SCALE) / 2f)

        assertEquals(0.5f, half, 0.0001f)
    }

    @Test
    fun outside_the_growth_the_light_is_clamped() {
        assertEquals(0f, focusLightAlpha(0.5f), 0.0001f)
        assertEquals(1f, focusLightAlpha(TvOs.FOCUS_SCALE + 1f), 0.0001f)
    }
}
