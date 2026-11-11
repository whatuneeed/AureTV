package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DeviceFormatTest {

    private val units = listOf("B", "KB", "MB", "GB", "TB")
    private val dot = Locale.US
    private val comma = Locale.GERMANY

    @Test
    fun bytes_below_a_kilobyte_stay_in_bytes() {
        assertEquals("512 B", DeviceFormat.size(512, units, dot))
    }

    @Test
    fun sizes_step_up_through_binary_magnitudes() {
        assertEquals("1.0 KB", DeviceFormat.size(1_024, units, dot))
        assertEquals("1.5 MB", DeviceFormat.size(1_572_864, units, dot))
        assertEquals("2.0 GB", DeviceFormat.size(2L * 1024 * 1024 * 1024, units, dot))
    }

    @Test
    fun two_digit_values_drop_the_fraction() {
        assertEquals("16 GB", DeviceFormat.size(16L * 1024 * 1024 * 1024, units, dot))
    }

    @Test
    fun the_last_unit_absorbs_any_remaining_magnitude() {
        assertEquals(
            "2048 TB",
            DeviceFormat.size(2L * 1024 * 1024 * 1024 * 1024 * 1024, units, dot),
        )
    }

    @Test
    fun uptime_switches_between_days_hours_and_minutes() {
        assertEquals("1 d 2 h", DeviceFormat.uptime(26L * 60 * 60 * 1000, "d", "h", "m"))
        assertEquals("3 h 5 m", DeviceFormat.uptime((3L * 60 + 5) * 60 * 1000, "d", "h", "m"))
        assertEquals("7 m", DeviceFormat.uptime(7L * 60 * 1000, "d", "h", "m"))
        assertEquals("0 m", DeviceFormat.uptime(-5, "d", "h", "m"))
    }

    @Test
    fun hertz_prints_whole_numbers_without_a_fraction() {
        assertEquals("60 Hz", DeviceFormat.hertz(60f, dot))
        assertEquals("59.9 Hz", DeviceFormat.hertz(59.94f, dot))
        assertEquals("—", DeviceFormat.hertz(0f, dot))
    }

    @Test
    fun an_unknown_refresh_rate_is_not_zero_hertz() {
        assertEquals("—", DeviceFormat.hertz(null, dot))
    }

    @Test
    fun the_caller_locale_controls_the_decimal_separator() {
        // The launcher language decides the form, not the system locale: a
        // Russian screen must not start printing dots because the TV is English.
        assertEquals("1.5 MB", DeviceFormat.size(1_572_864, units, dot))
        assertEquals("1,5 MB", DeviceFormat.size(1_572_864, units, comma))
        assertEquals("59,9 Hz", DeviceFormat.hertz(59.94f, comma))
    }
}
