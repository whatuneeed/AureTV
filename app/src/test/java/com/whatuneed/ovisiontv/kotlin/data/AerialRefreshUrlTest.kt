package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AerialRefreshUrlTest {

    @Test
    fun the_catalogue_source_is_apple_over_tls() {
        assertTrue(trustedCatalogueUrl("https://sylvan.apple.com/Aerials/resources.tar"))
        assertTrue(trustedCatalogueUrl("https://SYLVAN.APPLE.COM/Aerials/resources.tar"))
        assertTrue(trustedCatalogueUrl("https://sylvan.apple.com:443/Aerials/resources.tar"))
    }

    @Test
    fun cleartext_and_foreign_hosts_are_rejected() {
        assertFalse(trustedCatalogueUrl("http://sylvan.apple.com/Aerials/resources.tar"))
        assertFalse(trustedCatalogueUrl("https://evil.example/Aerials/resources.tar"))
        assertFalse(trustedCatalogueUrl("https://sylvan.apple.com.evil.example/x"))
        assertFalse(trustedCatalogueUrl("https://sylvan.apple.com:8443/x"))
        assertFalse(trustedCatalogueUrl(""))
        assertFalse(trustedCatalogueUrl("not a url"))
    }

    @Test
    fun a_relative_redirect_stays_on_the_trusted_host() {
        assertEquals(
            "https://sylvan.apple.com/Aerials/next.tar",
            resolveCatalogueRedirect(
                "https://sylvan.apple.com/Aerials/resources.tar",
                "/Aerials/next.tar",
            ),
        )
    }

    @Test
    fun a_redirect_may_not_downgrade_or_leave_the_host() {
        val current = "https://sylvan.apple.com/Aerials/resources.tar"
        assertNull(resolveCatalogueRedirect(current, "http://sylvan.apple.com/Aerials/x.tar"))
        assertNull(resolveCatalogueRedirect(current, "https://evil.example/x.tar"))
        assertNull(resolveCatalogueRedirect(current, "//evil.example/x.tar"))
        assertNull(resolveCatalogueRedirect(current, "https://sylvan.apple.com:8443/x.tar"))
    }
}
