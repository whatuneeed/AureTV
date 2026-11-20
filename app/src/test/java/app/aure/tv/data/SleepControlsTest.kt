package app.aure.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepControlsTest {

    @Test
    fun timerStartsAtFifteenAndCountsDown() {
        assertEquals(15, SleepControls.nextTimer(left = 0))
        assertEquals(30, SleepControls.nextTimer(left = 15))
        assertEquals(60, SleepControls.nextTimer(left = 30))
        assertEquals(90, SleepControls.nextTimer(left = 60))
        // Past 90 the cycle returns to Off, so one more press really cancels.
        assertEquals(0, SleepControls.nextTimer(left = 90))
        assertEquals(0, SleepControls.nextTimer(left = 200))
    }

    @Test
    fun aRemainderNearAStepSkipsIt() {
        // Java's rule is `step > left + 1`: a timer about to fire on 30 never
        // restarts 30, so a press always changes what the tile says.
        assertEquals(60, SleepControls.nextTimer(left = 29))
        assertEquals(60, SleepControls.nextTimer(left = 30))
        assertEquals(90, SleepControls.nextTimer(left = 59))
        assertEquals(0, SleepControls.nextTimer(left = 89))
    }

    @Test
    fun timerLeftRoundsUpPartialMinutes() {
        val now = 1_000_000L
        assertEquals(0, SleepControls.timerLeft(at = 0L, now = now))
        assertEquals(0, SleepControls.timerLeft(at = now - 1, now = now))
        assertEquals(1, SleepControls.timerLeft(at = now + 1, now = now))
        assertEquals(1, SleepControls.timerLeft(at = now + 60_000L, now = now))
        assertEquals(2, SleepControls.timerLeft(at = now + 60_001L, now = now))
        assertEquals(15, SleepControls.timerLeft(at = now + 15 * 60_000L, now = now))
    }

    @Test
    fun visibleSsidDropsQuotesAndUnknown() {
        assertEquals("Home 5G", DeviceFactsReader.visibleSsid("\"Home 5G\""))
        assertEquals("Home 5G", DeviceFactsReader.visibleSsid("Home 5G"))
        assertEquals(null, DeviceFactsReader.visibleSsid("<unknown ssid>"))
        assertEquals(null, DeviceFactsReader.visibleSsid(null))
        assertEquals(null, DeviceFactsReader.visibleSsid("\"\""))
        assertEquals(null, DeviceFactsReader.visibleSsid(""))
    }
}
