package app.aure.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleForTest {

    @Test
    fun an_apple_still_passes_without_downsampling() {
        assertEquals(1, sampleFor(900, 580, 1))
    }

    @Test
    fun the_thumbnail_minimum_is_kept() {
        assertEquals(2, sampleFor(900, 580, 2))
    }

    @Test
    fun an_oversized_frame_is_sampled_into_the_budget() {
        assertEquals(4, sampleFor(8000, 8000, 1))
    }

    @Test
    fun extreme_dimensions_do_not_overflow() {
        assertEquals(64, sampleFor(100000, 100000, 1))
    }

    @Test
    fun a_tiny_frame_is_never_upsampled() {
        assertEquals(1, sampleFor(1, 1, 1))
    }
}