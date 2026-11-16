package com.whatuneed.ovisiontv.kotlin.online

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineModelsTest {

    private fun feed(entry: String) = """
        {
          "feed": {
            "entry": $entry
          }
        }
    """.trimIndent()

    private fun entry(
        name: String = "Heart of the Beast",
        id: String = "6808370288",
        image: String = "https://is1-ssl.mzstatic.com/image/thumb/poster/115x170bb.png",
        preview: String = "https://video-ssl.itunes.apple.com/preview.m4v",
        date: String = "2026-01-01T00:00:00-07:00",
    ) = """
        {
          "im:name": {"label": "$name"},
          "im:image": [
            {"label": "$image", "attributes": {"height": "60"}},
            {"label": "$image", "attributes": {"height": "170"}}
          ],
          "im:releaseDate": {"label": "$date"},
          "id": {"label": "https://itunes.apple.com/us/movie/x/id$id", "attributes": {"im:id": "$id"}},
          "link": [
            {"attributes": {"rel": "alternate", "type": "text/html", "href": "https://itunes.apple.com/us/movie/x/id$id"}},
            {"attributes": {"rel": "enclosure", "type": "video/x-m4v", "href": "$preview"}}
          ]
        }
    """.trimIndent()

    @Test
    fun anEntryBecomesARealCardWithAPlayablePreview() {
        val movies = parseOnlineFeed(feed("[${entry()}]"))

        assertEquals(1, movies.size)
        val movie = movies.single()
        assertEquals("apple|6808370288", movie.id)
        assertEquals("Heart of the Beast", movie.title)
        assertEquals("2026", movie.year)
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/poster/600x900bb.jpg",
            movie.posterUrl,
        )
        assertEquals("https://video-ssl.itunes.apple.com/preview.m4v", movie.previewUrl)
    }

    @Test
    fun appleReturnsASingleObjectForOneEntryAndItIsStillParsed() {
        val movies = parseOnlineFeed(feed(entry()))

        assertEquals(1, movies.size)
        assertEquals("apple|6808370288", movies.single().id)
    }

    @Test
    fun aCleartextPreviewIsDroppedInsteadOfBecomingACard() {
        val movies = parseOnlineFeed(
            feed(entry(preview = "http://video-ssl.itunes.apple.com/preview.m4v")),
        )

        assertTrue(movies.isEmpty())
    }

    @Test
    fun aPreviewFromAForeignHostIsDropped() {
        val movies = parseOnlineFeed(
            feed(entry(preview = "https://example.com/preview.m4v")),
        )

        assertTrue(movies.isEmpty())
    }

    @Test
    fun duplicateIdsAreCollapsed() {
        val first = entry(name = "First")
        val second = entry(name = "Second")
        val movies = parseOnlineFeed(feed("[$first, $second]"))

        assertEquals(1, movies.size)
        assertEquals("First", movies.single().title)
    }

    @Test
    fun malformedJsonIsAnEmptyChartNotACrash() {
        assertTrue(parseOnlineFeed("{").isEmpty())
        assertTrue(parseOnlineFeed("").isEmpty())
        assertTrue(parseOnlineFeed("{\"feed\":{}}").isEmpty())
    }

    @Test
    fun aMovieBecomesAPlayableCardWithoutInventingLandscapeArt() {
        val movie = OnlineMovie(
            id = "apple|1",
            title = "A title",
            year = "2025",
            posterUrl = "https://is1-ssl.mzstatic.com/image/thumb/poster/600x900bb.jpg",
            previewUrl = "https://video-ssl.itunes.apple.com/preview.m4v",
        )

        val card = movie.toDiscoveryItem(
            ownPackage = "com.whatuneed.ovisiontv.kotlin",
            sourceLabel = "Apple iTunes",
            previewLabel = "Official preview",
        )

        assertEquals(DiscoverySource.ONLINE, card.source)
        assertEquals(DiscoveryReason.TRENDING, card.reason)
        assertEquals("A title", card.title)
        assertEquals("2025 · Official preview", card.subtitle)
        assertEquals(movie.posterUrl, card.posterUri)
        // Apple's chart only publishes a portrait poster. Claiming it as a
        // landscape hero would crop or stretch it, so the card leaves the
        // landscape slot empty and TopShelf uses its honest poster fallback.
        assertEquals(null, card.landscapeUri)
        assertEquals(movie.previewUrl, card.intentUri)
    }

    @Test
    fun cacheRoundTripsOnlyTheFieldsTheCardNeeds() {
        val movie = OnlineMovie(
            id = "apple|1",
            title = "A title",
            year = "2025",
            posterUrl = "https://is1-ssl.mzstatic.com/image/thumb/poster/600x900bb.jpg",
            previewUrl = "https://video-ssl.itunes.apple.com/preview.m4v",
        )
        val cache = OnlineCache(1234L, "ru", listOf(movie))

        val decoded = decodeOnlineCache(encodeOnlineCache(cache))

        assertEquals(cache, decoded)
    }

    @Test
    fun aCacheWithACleartextPreviewIsRejected() {
        val raw = """
            {"v":1,"at":1234,"cc":"us","movies":[{
              "id":"apple|1","title":"A title","poster":"https://is1-ssl.mzstatic.com/a.jpg",
              "preview":"http://video-ssl.itunes.apple.com/a.m4v"
            }]}
        """.trimIndent()

        val decoded = decodeOnlineCache(raw)

        assertNull(decoded)
    }
}
