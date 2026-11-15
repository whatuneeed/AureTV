package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafePreferencesTest {

    @Test
    fun a_corrupt_value_falls_back_to_the_caller_default() {
        var reports = 0

        val value = safePrefRead<String?>(
            key = "ui_language",
            fallback = "system",
            onCorrupt = { reports++ },
        ) {
            throw ClassCastException("Boolean cannot be read as String")
        }

        assertEquals("system", value)
        assertEquals(1, reports)
    }

    @Test
    fun a_missing_value_keeps_the_default_without_reporting_corruption() {
        var reports = 0

        val value = safePrefRead<String?>(
            key = "ui_language",
            fallback = null,
            onCorrupt = { reports++ },
        ) { null }

        assertNull(value)
        assertEquals(0, reports)
    }

    @Test
    fun a_readable_value_is_returned_unchanged() {
        var reports = 0

        val value = safePrefRead(
            key = "shelf_enabled",
            fallback = false,
            onCorrupt = { reports++ },
        ) { true }

        assertTrue(value)
        assertEquals(0, reports)
    }
}
