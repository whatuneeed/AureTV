package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cleartext stays disabled for every host except the public Apple Aerial CDN.
 * That exception is deliberately one host, not a global switch: the clips have
 * no account data, and some TV trust stores reject Apple's certificate chain.
 */
class NetworkSecurityConfigTest {

    private val config = File("src/main/res/xml/network_security_config.xml").readText()

    @Test
    fun cleartext_is_disabled_by_default() {
        assertTrue(config.contains("<base-config cleartextTrafficPermitted=\"false\">"))
        assertTrue(config.contains("<certificates src=\"system\" />"))
    }

    @Test
    fun the_only_cleartext_exception_is_the_apple_aerial_cdn() {
        assertEquals(
            2,
            Regex("cleartextTrafficPermitted=").findAll(config).count(),
        )
        assertTrue(config.contains("<domain-config cleartextTrafficPermitted=\"true\">"))
        assertTrue(config.contains("<domain includeSubdomains=\"false\">sylvan.apple.com</domain>"))
        assertEquals(1, Regex("<domain-config").findAll(config).count())
        assertEquals(1, Regex("cleartextTrafficPermitted=\"true\"").findAll(config).count())
        assertFalse(config.contains("cdn.example"))
        assertFalse(config.contains("<domain includeSubdomains=\"true\">"))
    }
}
