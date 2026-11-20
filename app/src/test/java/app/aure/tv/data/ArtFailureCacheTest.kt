package app.aure.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtFailureCacheTest {

    @Test
    fun a_failed_url_is_skipped_until_the_retry_window_expires() {
        var now = 1_000L
        val cache = ArtFailureCache(ttlMs = 60_000L, now = { now })

        cache.markFailed("https://host/poster")
        assertTrue(cache.isFailed("https://host/poster"))

        now += 59_999L
        assertTrue(cache.isFailed("https://host/poster"))

        now += 1L
        assertFalse(cache.isFailed("https://host/poster"))
    }

    @Test
    fun a_success_clears_the_negative_entry() {
        val cache = ArtFailureCache(now = { 1_000L })

        cache.markFailed("poster")
        cache.clear("poster")

        assertFalse(cache.isFailed("poster"))
    }

    @Test
    fun the_cache_does_not_grow_past_its_limit() {
        var now = 1_000L
        val cache = ArtFailureCache(ttlMs = 60_000L, maxEntries = 2, now = { now++ })

        cache.markFailed("a")
        cache.markFailed("b")
        cache.markFailed("c")

        // "a" is the oldest and is evicted; "b" and "c" still fit.
        assertFalse(cache.isFailed("a"))
        assertTrue(cache.isFailed("b"))
        assertTrue(cache.isFailed("c"))
    }
}
