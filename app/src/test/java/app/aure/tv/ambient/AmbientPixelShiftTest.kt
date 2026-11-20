package app.aure.tv.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientPixelShiftTest {

    @Test
    fun the_clock_returns_to_the_center_and_stays_within_the_safe_offset() {
        assertEquals(AmbientShift(0f, 0f), AmbientPixelShift.offset(0))
        assertEquals(AmbientShift(0f, 0f), AmbientPixelShift.offset(2))

        val shifts = (0 until AmbientPixelShift.cycle).map { AmbientPixelShift.offset(it.toLong()) }
        assertTrue(shifts.all { kotlin.math.abs(it.xDp) <= 9f })
        assertTrue(shifts.all { kotlin.math.abs(it.yDp) <= 6f })
    }

    @Test
    fun the_cycle_is_stable_for_every_minute() {
        assertEquals(
            AmbientPixelShift.offset(0),
            AmbientPixelShift.offset(AmbientPixelShift.cycle.toLong()),
        )
        assertEquals(
            AmbientPixelShift.offset(1),
            AmbientPixelShift.offset(1L + AmbientPixelShift.cycle),
        )
    }
}
