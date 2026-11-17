package com.whatuneed.ovisiontv.kotlin.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FluxDeepLinkTest {

    @Test
    fun aNumericTmdbIdBuildsTheMovieDeepLink() {
        assertEquals("flux://movie/438631", fluxMovieUri("438631"))
    }

    @Test
    fun anEmptyOrMalformedIdIsRejected() {
        assertNull(fluxMovieUri(null))
        assertNull(fluxMovieUri(""))
        assertNull(fluxMovieUri("abc"))
        assertNull(fluxMovieUri("12/34"))
        assertNull(fluxMovieUri("1234567890123"))
    }
}
