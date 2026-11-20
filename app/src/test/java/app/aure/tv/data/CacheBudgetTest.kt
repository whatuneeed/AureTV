package app.aure.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheBudgetTest {

    @Test
    fun a_partial_trim_lowers_the_capacity() {
        assertEquals(750, trimmedCapacity(1000, 25))
        assertEquals(500, trimmedCapacity(1000, 50))
        assertEquals(1, trimmedCapacity(1, 50))
    }

    @Test
    fun a_full_trim_keeps_a_tenth_of_the_capacity() {
        assertEquals(100, trimmedCapacity(1000, 100))
        assertEquals(1, trimmedCapacity(1, 100))
    }

    @Test
    fun a_zero_percent_keeps_the_capacity() {
        assertEquals(1000, trimmedCapacity(1000, 0))
    }
}
