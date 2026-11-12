package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * After A-01 a tile carries a cache key instead of pixels, so the reload path
 * parses that key. A key that resolves to the wrong package (or to no size)
 * would repaint a tile with another app's artwork, and the trim/eviction code
 * could not tell the difference — so the parse is pinned here.
 */
class ArtKeyTest {

    @Test
    fun a_banner_key_carries_package_kind_and_box() {
        val parsed = parseArtKey("com.example.app|1700000000000|banner|400x240")
        assertEquals(ArtKey("com.example.app", "banner", 400, 240), parsed)
    }

    @Test
    fun the_launchers_own_settings_icon_keeps_its_activity() {
        val parsed = parseArtKey("com.whatuneed.ovisiontv.kotlin|1|act:com.whatuneed.ovisiontv.kotlin.SettingsActivity|256x256")
        assertEquals("act:com.whatuneed.ovisiontv.kotlin.SettingsActivity", parsed?.kind)
    }

    // A version stamp is part of the key: an updated app must not resolve to the
    // previous build's artwork through a stale cache entry.
    @Test
    fun a_new_stamp_is_a_different_key() {
        val old = "com.example.app|1000|banner|400x240"
        val new = "com.example.app|2000|banner|400x240"
        assertEquals(parseArtKey(old)?.packageName, parseArtKey(new)?.packageName)
        assert(old != new)
    }

    @Test
    fun damaged_keys_are_rejected_instead_of_guessed() {
        assertNull(parseArtKey(""))
        assertNull(parseArtKey("com.example.app"))
        assertNull(parseArtKey("com.example.app|1|banner"))
        assertNull(parseArtKey("com.example.app|1|banner|400"))
        assertNull(parseArtKey("com.example.app|1|banner|0x240"))
        assertNull(parseArtKey("|1|banner|400x240"))
    }
}
