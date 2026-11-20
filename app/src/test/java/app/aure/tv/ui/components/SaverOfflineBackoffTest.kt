package app.aure.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SaverOfflineBackoffTest {

    @Test
    fun offline_retry_waits_progressively_longer_and_stops_growing() {
        assertEquals(15_000L, saverOfflineRetryDelay(0))
        assertEquals(30_000L, saverOfflineRetryDelay(1))
        assertEquals(60_000L, saverOfflineRetryDelay(2))
        assertEquals(120_000L, saverOfflineRetryDelay(3))
        assertEquals(120_000L, saverOfflineRetryDelay(20))
    }
}
