package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Russian networks block port 443 to Apple's aerial CDN while port 80 serves
 * the same files, so sylvan.apple.com is the one cleartext exception. A silent
 * change here only shows up as a blank saver on a TV, so it is pinned.
 */
class NetworkSecurityConfigTest {

    private val config = File("src/main/res/xml/network_security_config.xml").readText()

    @Test
    fun cleartext_stays_disabled_for_every_host_by_default() {
        assertTrue(config.contains("<base-config cleartextTrafficPermitted=\"false\">"))
        assertTrue(config.contains("<certificates src=\"system\" />"))
    }

    @Test
    fun the_cdn_host_is_the_only_cleartext_exception() {
        assertTrue(config.contains("<domain-config cleartextTrafficPermitted=\"true\">"))
        assertTrue(config.contains("<domain includeSubdomains=\"true\">sylvan.apple.com</domain>"))
        assertEquals(
            1,
            Regex("cleartextTrafficPermitted=\"true\"").findAll(config).count(),
        )
    }
}
