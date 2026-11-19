package com.whatuneed.ovisiontv.kotlin.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chart clients may only follow HTTPS requests on their own host. */
class OnlineUrlPolicyTest {

    @Test
    fun justwatchStaysOnItsExactHttpsHost() {
        val hosts = setOf("apis.justwatch.com")

        assertTrue(onlineUrlIsTrusted("https://apis.justwatch.com/graphql", hosts))
        assertTrue(onlineUrlIsTrusted("https://apis.justwatch.com:443/graphql", hosts))

        assertFalse(onlineUrlIsTrusted("http://apis.justwatch.com/graphql", hosts))
        assertFalse(onlineUrlIsTrusted("https://apis.justwatch.com.evil.test/graphql", hosts))
        assertFalse(onlineUrlIsTrusted("https://evil.test/graphql", hosts))
        assertFalse(onlineUrlIsTrusted("https://apis.justwatch.com:444/graphql", hosts))
        assertFalse(onlineUrlIsTrusted("https://apis.justwatch.com@evil.test/graphql", hosts))
        assertFalse(onlineUrlIsTrusted("not a url", hosts))
    }

    @Test
    fun appleRssStaysOnItunesHttpsHost() {
        val hosts = setOf("itunes.apple.com")

        assertTrue(onlineUrlIsTrusted("https://itunes.apple.com/us/rss/topmovies/limit=8/json", hosts))
        assertFalse(onlineUrlIsTrusted("http://itunes.apple.com/us/rss/topmovies/limit=8/json", hosts))
        assertFalse(onlineUrlIsTrusted("https://itunes.apple.com.evil.test/rss", hosts))
        assertFalse(onlineUrlIsTrusted("https://evil.test/rss", hosts))
    }
}
