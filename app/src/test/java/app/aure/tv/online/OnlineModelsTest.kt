package app.aure.tv.online

import app.aure.tv.discovery.DiscoveryReason
import app.aure.tv.discovery.DiscoverySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun theTallestPreviewEnclosureWinsWhenApplePublishesMoreThanOne() {
        val raw = """
            {"feed":{"entry":{
              "im:name":{"label":"A title"},
              "im:image":[{"label":"https://is1-ssl.mzstatic.com/poster.jpg","attributes":{"height":"170"}}],
              "im:releaseDate":{"label":"2026-01-01T00:00:00-07:00"},
              "id":{"label":"x","attributes":{"im:id":"1"}},
              "link":[
                {"attributes":{"rel":"enclosure","type":"video/x-m4v","href":"https://video-ssl.itunes.apple.com/preview_640x354.m4v"}},
                {"attributes":{"rel":"enclosure","type":"video/x-m4v","href":"https://video-ssl.itunes.apple.com/preview_1280x720.m4v"}}
              ]
            }}}
        """.trimIndent()

        assertEquals(
            "https://video-ssl.itunes.apple.com/preview_1280x720.m4v",
            parseOnlineFeed(raw).single().previewUrl,
        )
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
    fun aMovieKeepsItsPreviewForProviderMatchingWithoutInventingLandscapeArt() {
        val movie = OnlineMovie(
            id = "apple|1",
            title = "A title",
            year = "2025",
            posterUrl = "https://is1-ssl.mzstatic.com/image/thumb/poster/600x900bb.jpg",
            previewUrl = "https://video-ssl.itunes.apple.com/preview.m4v",
        )

        val card = movie.toDiscoveryItem(
            ownPackage = "app.aure.tv",
            sourceLabel = "JustWatch",
            infoLabel = "About",
        )

        assertEquals(DiscoverySource.ONLINE, card.source)
        assertEquals(DiscoveryReason.TRENDING, card.reason)
        assertEquals("A title", card.title)
        assertEquals("2025 · About", card.subtitle)
        assertEquals(movie.posterUrl, card.posterUri)
        // Apple's chart only publishes a portrait poster. Claiming it as a
        // landscape hero would crop or stretch it, so the card leaves the
        // landscape slot empty and TopShelf uses its honest poster fallback.
        assertEquals(null, card.landscapeUri)
        assertEquals(null, card.intentUri)
        assertEquals(movie.previewUrl, card.trailerUri)
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
    fun aJustWatchChartKeepsThePosterAndTheWideBackdrop() {
        val raw = """
            {"data":{"popularTitles":{"edges":[{"node":{"id":"tm1","content":{
              "title":"Дюна","originalTitle":"Dune","originalReleaseYear":2021,
              "shortDescription":"Полёт на планету Арракис.",
              "externalIds":{"tmdbId":438631},
              "posterUrl":"/poster/11/{profile}/dune.{format}",
              "backdrops":[{"backdropUrl":"/backdrop/22/{profile}/dune.{format}"}]}}}]}}}
        """.trimIndent()

        val movie = parseJustWatchChart(raw).single()

        assertEquals("justwatch|tm1", movie.id)
        assertEquals("Дюна", movie.title)
        assertEquals("Dune", movie.originalTitle)
        assertEquals("438631", movie.tmdbId)
        assertEquals("2021", movie.year)
        assertEquals("https://images.justwatch.com/poster/11/s592/dune.jpg", movie.posterUrl)
        assertEquals("https://images.justwatch.com/backdrop/22/s1920/dune.jpg", movie.landscapeUrl)
        assertEquals("Полёт на планету Арракис.", movie.synopsis)
        assertNull(movie.previewUrl)
    }

    @Test
    fun aJustWatchTitleWithoutAPosterUsesItsBackdropInsteadOfBeingDropped() {
        val raw = """
            {"data":{"popularTitles":{"edges":[{"node":{"id":"tm2","content":{
              "title":"Без постера","originalReleaseYear":2026,
              "backdrops":[{"backdropUrl":"/backdrop/33/{profile}/no-poster.{format}"}]}}}]}}}
        """.trimIndent()

        val movie = parseJustWatchChart(raw).single()

        assertEquals("https://images.justwatch.com/backdrop/33/s1920/no-poster.jpg", movie.posterUrl)
        assertEquals(movie.posterUrl, movie.landscapeUrl)
    }

    @Test
    fun aJustWatchTitleWithoutAnyArtIsDropped() {
        val raw = """
            {"data":{"popularTitles":{"edges":[{"node":{"id":"tm3","content":{
              "title":"Без картинки","originalReleaseYear":2026}}}]}}}
        """.trimIndent()

        assertTrue(parseJustWatchChart(raw).isEmpty())
    }

    @Test
    fun thePreviewIndexMatchesALocalizedOrOriginalTitleAndTheYear() {
        val preview = OnlineMovie(
            id = "apple|1",
            title = "Dune",
            year = "2021",
            posterUrl = "https://is1-ssl.mzstatic.com/dune.jpg",
            previewUrl = "https://video-ssl.itunes.apple.com/dune.m4v",
        )
        val index = buildOnlinePreviewIndex(listOf(preview))

        assertNotNull(index.match("Дюна", "Dune", "2021"))
        assertNotNull(index.match("Dune", null, "2021"))
        assertNull(index.match("Дюна", "Dune", "1984"))
    }

    @Test
    fun aJustWatchCardCarriesTheWideArtToTheShelf() {
        val movie = OnlineMovie(
            id = "justwatch|tm1",
            title = "Дюна",
            year = "2021",
            posterUrl = "https://images.justwatch.com/poster/11/s592/dune.jpg",
            previewUrl = "https://video-ssl.itunes.apple.com/dune.m4v",
            landscapeUrl = "https://images.justwatch.com/backdrop/22/s1920/dune.jpg",
            originalTitle = "Dune",
            synopsis = "Полёт на планету Арракис.",
        )

        val card = movie.toDiscoveryItem(
            ownPackage = "app.aure.tv",
            sourceLabel = "JustWatch",
            infoLabel = "About",
        )

        assertEquals(movie.landscapeUrl, card.landscapeUri)
        assertEquals(movie.posterUrl, card.posterUri)
        assertEquals(null, card.intentUri)
        assertEquals(movie.previewUrl, card.trailerUri)
        assertEquals("2021 · About", card.subtitle)
        assertEquals("Полёт на планету Арракис.", card.synopsis)
    }

    @Test
    fun aJustWatchCardWithoutAPreviewKeepsItsArtAndSynopsisForTheInfoOverlay() {
        val movie = OnlineMovie(
            id = "justwatch|tm4",
            title = "Без трейлера",
            year = "2026",
            posterUrl = "https://images.justwatch.com/poster/44/s592/no-trailer.jpg",
            landscapeUrl = "https://images.justwatch.com/backdrop/55/s1920/no-trailer.jpg",
            synopsis = "Описание фильма из JustWatch.",
        )

        val card = movie.toDiscoveryItem(
            ownPackage = "app.aure.tv",
            sourceLabel = "JustWatch",
            infoLabel = "About",
        )

        assertEquals(movie.landscapeUrl, card.landscapeUri)
        assertEquals(movie.posterUrl, card.posterUri)
        assertEquals(null, card.intentUri)
        assertEquals(null, card.trailerUri)
        assertEquals("2026 · About", card.subtitle)
        assertEquals("Описание фильма из JustWatch.", card.synopsis)
    }

    @Test
    fun aCacheWithLandscapeAndOriginalTitleRoundTrips() {
        val movie = OnlineMovie(
            id = "justwatch|tm1",
            title = "Дюна",
            year = "2021",
            posterUrl = "https://images.justwatch.com/poster/11/s592/dune.jpg",
            previewUrl = "https://video-ssl.itunes.apple.com/dune.m4v",
            landscapeUrl = "https://images.justwatch.com/backdrop/22/s1920/dune.jpg",
            originalTitle = "Dune",
            synopsis = "Полёт на планету Арракис.",
        )

        val decoded = decodeOnlineCache(encodeOnlineCache(OnlineCache(1234L, "ru", listOf(movie))))

        assertEquals(movie, decoded?.movies?.single())
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
