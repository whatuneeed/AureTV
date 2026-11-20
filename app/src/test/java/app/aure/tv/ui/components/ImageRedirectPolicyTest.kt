package app.aure.tv.ui.components

import app.aure.tv.core.network.imageRedirectIsTrusted
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Image redirects may not leave the host they started on. */
class ImageRedirectPolicyTest {

    @Test
    fun aRedirectStaysOnTheSameHost() {
        assertTrue(
            imageRedirectIsTrusted(
                "https://is1-ssl.mzstatic.com/image/thumb/a.jpg",
                "https://is1-ssl.mzstatic.com/image/thumb/b.jpg",
            ),
        )
        assertTrue(
            imageRedirectIsTrusted(
                "https://images.justwatch.com/poster/a/s592",
                "https://images.justwatch.com/poster/b/s592",
            ),
        )
    }

    @Test
    fun aDifferentHostIsRejected() {
        assertFalse(
            imageRedirectIsTrusted(
                "https://images.justwatch.com/poster/a/s592",
                "https://evil.test/poster/a/s592",
            ),
        )
        assertFalse(
            imageRedirectIsTrusted(
                "https://images.justwatch.com/poster/a/s592",
                "https://images.justwatch.com.evil.test/poster/a/s592",
            ),
        )
        assertFalse(
            imageRedirectIsTrusted(
                "https://is1-ssl.mzstatic.com/a.jpg",
                "https://user@is1-ssl.mzstatic.com/a.jpg",
            ),
        )
    }

    @Test
    fun cleartextIsOnlyAllowedForTheAerialHost() {
        assertTrue(
            imageRedirectIsTrusted(
                "http://sylvan.apple.com/videos/a.mov",
                "http://sylvan.apple.com/videos/b.mov",
            ),
        )
        assertFalse(
            imageRedirectIsTrusted(
                "https://images.justwatch.com/a",
                "http://images.justwatch.com/a",
            ),
        )
        assertFalse(
            imageRedirectIsTrusted(
                "https://images.justwatch.com/a",
                "https://images.justwatch.com:444/a",
            ),
        )
    }
}
