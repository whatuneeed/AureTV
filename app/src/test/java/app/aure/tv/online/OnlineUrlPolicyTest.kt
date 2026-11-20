package app.aure.tv.online

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
    fun onlineArtworkStaysOnTheChartImageCdn() {
        assertTrue(onlineImageUrlIsTrusted("https://images.justwatch.com/poster/1/s592"))
        assertTrue(onlineImageUrlIsTrusted("https://is1-ssl.mzstatic.com/image/thumb/x.jpg"))
        assertTrue(onlineImageUrlIsTrusted("https://mzstatic.com/image/thumb/x.jpg"))
        assertTrue(onlineImageUrlIsTrusted("https://example.apple.com/art/x.jpg"))

        assertFalse(onlineImageUrlIsTrusted("http://images.justwatch.com/poster/1/s592"))
        assertFalse(onlineImageUrlIsTrusted("https://images.justwatch.com.evil.test/poster"))
        assertFalse(onlineImageUrlIsTrusted("https://evil.test/poster"))
        assertFalse(onlineImageUrlIsTrusted("not a url"))
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
