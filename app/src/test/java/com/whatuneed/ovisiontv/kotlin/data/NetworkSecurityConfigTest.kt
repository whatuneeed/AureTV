package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No request may leave the device in the clear. The old Apple CDN exception
 * was removed together with the silent HTTPS-to-HTTP media downgrade; keeping
 * this test means a future compatibility workaround cannot reintroduce it
 * unnoticed.
 */
class NetworkSecurityConfigTest {

    private val config = File("src/main/res/xml/network_security_config.xml").readText()

    @Test
    fun cleartext_is_disabled_for_every_host() {
        assertTrue(config.contains("<base-config cleartextTrafficPermitted=\"false\">"))
        assertTrue(config.contains("<certificates src=\"system\" />"))
        assertEquals(
            1,
            Regex("cleartextTrafficPermitted=").findAll(config).count(),
        )
    }

    @Test
    fun no_domain_is_granted_a_cleartext_exception() {
        assertTrue(Regex("cleartextTrafficPermitted=\"true\"").findAll(config).count() == 0)
        assertTrue(!config.contains("<domain-config"))
    }
}
