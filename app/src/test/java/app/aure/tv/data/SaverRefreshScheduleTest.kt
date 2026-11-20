package app.aure.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverRefreshScheduleTest {

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun the_first_check_after_install_is_due() {
        SaverRefresh.entries
            .filter { it != SaverRefresh.NEVER }
            .forEach { assertTrue(SaverRefreshSchedule.isDue(it, 0L, 1_000L)) }
    }

    @Test
    fun every_frequency_waits_for_its_own_interval() {
        val last = 10 * day
        assertFalse(SaverRefreshSchedule.isDue(SaverRefresh.DAILY, last, last + day - 1))
        assertTrue(SaverRefreshSchedule.isDue(SaverRefresh.DAILY, last, last + day))

        assertFalse(SaverRefreshSchedule.isDue(SaverRefresh.WEEKLY, last, last + 6 * day))
        assertTrue(SaverRefreshSchedule.isDue(SaverRefresh.WEEKLY, last, last + 7 * day))

        assertFalse(SaverRefreshSchedule.isDue(SaverRefresh.MONTHLY, last, last + 29 * day))
        assertTrue(SaverRefreshSchedule.isDue(SaverRefresh.MONTHLY, last, last + 30 * day))
    }

    @Test
    fun never_never_refreshes_even_without_an_attempt() {
        assertFalse(SaverRefreshSchedule.isDue(SaverRefresh.NEVER, 0L, Long.MAX_VALUE / 2))
        assertEquals(null, SaverRefreshSchedule.intervalMs(SaverRefresh.NEVER))
    }
}
