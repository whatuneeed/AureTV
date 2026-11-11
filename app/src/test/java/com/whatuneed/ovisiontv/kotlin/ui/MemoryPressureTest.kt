package com.whatuneed.ovisiontv.kotlin.ui

import android.content.ComponentCallbacks2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPressureTest {

    @Test
    fun running_moderate_drops_a_quarter_but_keeps_the_shelf() {
        val plan = MemoryPressure.plan(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        assertEquals(25, plan.percent)
        assertTrue(plan.shelf)
    }

    @Test
    fun running_low_drops_half() {
        val plan = MemoryPressure.plan(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(50, plan.percent)
        assertTrue(plan.shelf)
    }

    @Test
    fun running_critical_drops_everything_except_the_shelf() {
        val plan = MemoryPressure.plan(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(100, plan.percent)
        assertTrue(plan.shelf)
    }

    // UI_HIDDEN arrives every time an app or Settings comes to the front, so it
    // must not cost the posters the user sees again a second later.
    @Test
    fun ui_hidden_releases_the_saver_frames_but_not_the_shelf() {
        val plan = MemoryPressure.plan(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(100, plan.percent)
        assertTrue(plan.shelf)
    }

    @Test
    fun background_and_severe_levels_drop_the_shelf_too() {
        val levels = listOf(
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        )
        for (level in levels) {
            val plan = MemoryPressure.plan(level)
            assertEquals(100, plan.percent)
            assertFalse("level $level must drop the shelf", plan.shelf)
        }
    }

    @Test
    fun unknown_levels_trim_lightly_and_keep_the_shelf() {
        val plan = MemoryPressure.plan(12345)
        assertEquals(25, plan.percent)
        assertTrue(plan.shelf)
    }
}
