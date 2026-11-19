package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Redirect targets are resolved once; host policy stays with each client. */
class RedirectUrlTest {

    @Test
    fun aRelativeLocationResolvesAgainstTheCurrentUrl() {
        assertEquals(
            "https://api.example.com/v1/next",
            resolveRedirectUrl("https://api.example.com/v1/current", "next"),
        )
        assertEquals(
            "https://api.example.com/root",
            resolveRedirectUrl("https://api.example.com/v1/current", "/root"),
        )
    }

    @Test
    fun anAbsoluteLocationIsResolvedWithoutJudgingItsHost() {
        assertEquals(
            "https://cdn.example.net/file",
            resolveRedirectUrl("https://api.example.com/v1/current", "https://cdn.example.net/file"),
        )
    }

    @Test
    fun aMissingBlankOrOversizedLocationIsRejected() {
        assertNull(resolveRedirectUrl("https://api.example.com/a", null))
        assertNull(resolveRedirectUrl("https://api.example.com/a", "   "))
        assertNull(resolveRedirectUrl("https://api.example.com/a", "https://x/" + "a".repeat(4_096)))
        assertNull(resolveRedirectUrl("https://api.example.com/a", "http://[::1"))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(
            "https://api.example.com/next",
            resolveRedirectUrl("https://api.example.com/current", "  next \n"),
        )
    }
}
