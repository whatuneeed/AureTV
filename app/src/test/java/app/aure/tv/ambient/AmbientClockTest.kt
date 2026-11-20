package app.aure.tv.ambient

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class AmbientClockTest {

    private val now = ZonedDateTime.of(2026, 11, 14, 9, 5, 0, 0, ZoneId.of("UTC"))

    @Test
    fun a_24_hour_clock_keeps_the_leading_zero() {
        assertEquals("09:05", AmbientClock.formatTime(now, is24Hour = true, Locale.US))
    }

    @Test
    fun a_12_hour_clock_drops_the_leading_zero() {
        assertEquals("9:05 AM", AmbientClock.formatTime(now, is24Hour = false, Locale.US))
    }

    @Test
    fun the_date_is_localized_and_starts_with_a_capital() {
        assertEquals(
            "Суббота, 14 ноября",
            AmbientClock.formatDate(now, Locale("ru")),
        )
    }
}
