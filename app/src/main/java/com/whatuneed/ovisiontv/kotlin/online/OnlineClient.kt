package com.whatuneed.ovisiontv.kotlin.online

import com.whatuneed.ovisiontv.kotlin.data.resolveRedirectUrl
import com.whatuneed.ovisiontv.kotlin.data.writeFileAtomically
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.whatuneed.ovisiontv.kotlin.data.LauncherUserAgent
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * JustWatch's public popular-titles chart. No account and no API key are used;
 * the launcher sends one ordinary JSON GraphQL request for a country's chart.
 * The endpoint is undocumented, so the client has a short timeout, a response
 * cap and no retry loop: a failed request leaves the previous cache in place.
 */
internal class JustWatchClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
) {
    suspend fun topMovies(
        country: String,
        limit: Int = JUSTWATCH_CHART_LIMIT,
    ): List<OnlineMovie> = withContext(Dispatchers.IO) {
        val safeCountry = country.uppercase(Locale.ROOT)
            .takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "RU"
        val safeLimit = limit.coerceIn(1, JUSTWATCH_CHART_LIMIT)
        val language = if (safeCountry == "RU") "ru" else "en"
        val variables = JSONObject()
            .put("country", safeCountry)
            .put("language", language)
            .put("first", safeLimit)
            .put("filter", JSONObject().put("objectTypes", JSONArray().put("MOVIE")))
            .put("sort", "TRENDING")
        val body = JSONObject()
            .put("query", QUERY)
            .put("variables", variables)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val connection = (URL(JUSTWATCH_GRAPHQL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            doOutput = true
            setFixedLengthStreamingMode(body.size)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", language)
            setRequestProperty("User-Agent", LauncherUserAgent.value)
        }
        try {
            if (!onlineUrlIsTrusted(JUSTWATCH_GRAPHQL_URL, setOf("apis.justwatch.com"))) {
                return@withContext emptyList()
            }
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            // The endpoint is a fixed GraphQL POST. A redirect would need the
            // body replayed and could leave the host, so it is a failure.
            if (code in 300..399) return@withContext emptyList()
            if (code !in 200..299) return@withContext emptyList()
            if (connection.contentLengthLong > ONLINE_CACHE_MAX_BYTES) return@withContext emptyList()
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (output.size() < ONLINE_CACHE_MAX_BYTES) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            if (bytes.size > ONLINE_CACHE_MAX_BYTES) return@withContext emptyList()
            parseJustWatchChart(bytes.toString(Charsets.UTF_8), safeLimit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /**
         * The fields are deliberately limited to what a card needs. In
         * particular, clips are not requested: their YouTube ids are not a
         * media URL the launcher can hand to ExoPlayer.
         */
        val QUERY = "query P(\$country: Country!, \$language: Language!, \$first: Int!, " +
            "\$filter: TitleFilter, \$sort: PopularTitlesSorting!) { " +
            "popularTitles(country: \$country, filter: \$filter, first: \$first, " +
            "sortBy: \$sort, sortRandomSeed: 0) { edges { node { id " +
            "content(country: \$country, language: \$language) { title originalTitle " +
            "originalReleaseYear shortDescription posterUrl " +
            "externalIds { tmdbId } backdrops { backdropUrl } " +
            "} } } } }"
    }
}

/**
 * Apple's public Top Movies RSS, used only for official previews and, when a
 * JustWatch poster is absent, as a poster fallback. The launcher sends no
 * cookie or identifier; it only asks for one country's public chart.
 */
internal class AppleRssClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
) {
    suspend fun topMovies(country: String, limit: Int = 8): List<OnlineMovie> =
        withContext(Dispatchers.IO) {
            val safeCountry = country.lowercase(Locale.ROOT)
                .takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "us"
            val safeLimit = limit.coerceIn(1, JUSTWATCH_CHART_LIMIT)
            val url = "https://itunes.apple.com/$safeCountry/rss/topmovies/limit=$safeLimit/json"
            val bytes = fetch(url, redirects = 0) ?: return@withContext emptyList()
            parseOnlineFeed(bytes.toString(Charsets.UTF_8), safeLimit)
        }

    private suspend fun fetch(url: String, redirects: Int): ByteArray? {
        currentCoroutineContext().ensureActive()
        if (!onlineUrlIsTrusted(url, APPLE_HOSTS)) return null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", LauncherUserAgent.value)
            setRequestProperty("Accept-Language", "en-US,en;q=0.8")
        }
        return try {
            val code = connection.responseCode
            if (code in 300..399) {
                if (redirects >= MAX_REDIRECTS) return null
                val location = connection.getHeaderField("Location") ?: return null
                val next = resolveRedirectUrl(url, location) ?: return null
                return fetch(next, redirects + 1)
            }
            if (code !in 200..299) return null
            if (connection.contentLengthLong > ONLINE_CACHE_MAX_BYTES) return null
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (output.size() < ONLINE_CACHE_MAX_BYTES) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            return bytes.takeIf { it.size <= ONLINE_CACHE_MAX_BYTES }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 3
        val APPLE_HOSTS = setOf("itunes.apple.com")
    }

    /**
     * Builds one index from several Apple storefronts. The local storefront is
     * asked first, then US and GB, because a RU-localized JustWatch title often
     * keeps its original English title in those charts.
     */
    suspend fun previewIndex(countries: List<String>): OnlinePreviewIndex =
        OnlinePreviewIndex(
            countries
                .map { it.lowercase(Locale.ROOT) }
                .filter { it.length == 2 && it.all(Char::isLetter) }
                .distinct()
                .flatMap { topMovies(it, JUSTWATCH_CHART_LIMIT) },
        )
}

/** Online requests may only reach the fixed HTTPS chart hosts. */
internal fun onlineUrlIsTrusted(raw: String, allowedHosts: Set<String>): Boolean {
    val uri = runCatching { URI(raw) }.getOrNull() ?: return false
    if (uri.scheme?.equals("https", ignoreCase = true) != true) return false
    if (uri.port != -1 && uri.port != 443) return false
    return uri.host?.lowercase() in allowedHosts
}

/**
 * A six-hour on-disk cache. The public chart changes slowly and a TV resume
 * must not hit the network every time; a stale cache is still better than an
 * empty rail when the network is down, so it is returned on a failed refresh.
 */
internal class OnlineCacheStore(private val file: File) {

    fun read(country: String, nowUtc: Long): OnlineCache? {
        val raw = runCatching { file.takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull()
        val cache = decodeOnlineCache(raw) ?: return null
        if (!cache.country.equals(country, ignoreCase = true)) return null
        if (nowUtc - cache.fetchedAtUtc !in 0..CACHE_TTL_MS) return null
        return cache
    }

    fun readStale(country: String): OnlineCache? {
        val raw = runCatching { file.takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull()
        val cache = decodeOnlineCache(raw) ?: return null
        return cache.takeIf { it.country.equals(country, ignoreCase = true) }
    }

    fun write(cache: OnlineCache) {
        runCatching {
            file.parentFile?.mkdirs()
            writeFileAtomically(file, encodeOnlineCache(cache).toByteArray(Charsets.UTF_8))
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    }
}

internal class OnlineRepository(
    cacheFile: File,
    private val justWatch: JustWatchClient = JustWatchClient(),
    private val apple: AppleRssClient = AppleRssClient(),
) {
    private val cache = OnlineCacheStore(cacheFile)

    /**
     * Returns a chart of cards for [country]. JustWatch supplies the rank,
     * localized title, both image types and a synopsis; Apple supplies the
     * preview only when the same title and year are found in a public Apple
     * chart. A title without a preview stays as an information card, so its OK
     * still opens a real in-launcher synopsis instead of a dead card.
     */
    /** The last written chart, read without a network request and without a TTL check. */
    fun cachedMovies(country: String): List<OnlineMovie> =
        cache.readStale(country)?.movies.orEmpty()

    suspend fun topMovies(
        country: String,
        nowUtc: Long = System.currentTimeMillis(),
    ): List<OnlineMovie> {
        cache.read(country, nowUtc)?.let { return it.movies }
        val fresh = refresh(country)
        if (fresh.isNotEmpty()) {
            cache.write(OnlineCache(nowUtc, country, fresh))
            return fresh
        }
        return cache.readStale(country)?.movies.orEmpty()
    }

    private suspend fun refresh(country: String): List<OnlineMovie> {
        val chart = justWatch.topMovies(country)
        if (chart.isEmpty()) return emptyList()
        val previews = apple.previewIndex(listOf(country, "us", "gb"))
        val withTrailer = ArrayList<OnlineMovie>(ONLINE_MAX_TRAILER_MOVIES)
        val infoOnly = ArrayList<OnlineMovie>(ONLINE_MAX_INFO_MOVIES)
        chart.forEach { movie ->
            if (withTrailer.size >= ONLINE_MAX_TRAILER_MOVIES &&
                infoOnly.size >= ONLINE_MAX_INFO_MOVIES
            ) {
                return@forEach
            }
            val preview = previews.match(movie.title, movie.originalTitle, movie.year)
            val poster = movie.posterUrl.ifBlank { preview?.posterUrl.orEmpty() }
            if (poster.isBlank()) return@forEach
            val card = movie.copy(posterUrl = poster, previewUrl = preview?.previewUrl)
            if (!card.previewUrl.isNullOrBlank()) {
                if (withTrailer.size < ONLINE_MAX_TRAILER_MOVIES) withTrailer += card
            } else {
                if (infoOnly.size < ONLINE_MAX_INFO_MOVIES) infoOnly += card
            }
        }
        return (withTrailer + infoOnly)
            .distinctBy { it.id }
            .take(ONLINE_MAX_MOVIES)
    }
}
