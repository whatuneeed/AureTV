package com.whatuneed.ovisiontv.kotlin.online

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.Normalizer
import java.util.Locale

/**
 * A real title from a public chart. The launcher keeps only what it can show:
 * a title, its poster, an optional wide backdrop and an optional official
 * preview. Nothing is invented; a missing URL stays missing.
 *
 * JustWatch supplies the RU chart and its two image types. Apple's public
 * preview is attached only when the same title is found in an Apple chart; a
 * card with a preview can be published as a Flux "For You" card.
 */
data class OnlineMovie(
    val id: String,
    val title: String,
    val year: String?,
    val posterUrl: String,
    val previewUrl: String? = null,
    val landscapeUrl: String? = null,
    val originalTitle: String? = null,
    /** JustWatch's external id, used only to match a provider deep link. */
    val tmdbId: String? = null,
    val synopsis: String? = null,
)

/** One cached chart. [country] is part of the key: ru and us lists differ. */
data class OnlineCache(
    val fetchedAtUtc: Long,
    val country: String,
    val movies: List<OnlineMovie>,
)

internal const val ONLINE_CACHE_MAX_BYTES = 512 * 1024
internal const val ONLINE_MAX_MOVIES = 24
internal const val ONLINE_MAX_TRAILER_MOVIES = 12
internal const val ONLINE_MAX_INFO_MOVIES = 12
internal const val ONLINE_IMAGE_WIDTH = 600
internal const val ONLINE_IMAGE_HEIGHT = 900
internal const val JUSTWATCH_CHART_LIMIT = 100
internal const val JUSTWATCH_POSTER_PROFILE = "s592"
internal const val JUSTWATCH_BACKDROP_PROFILE = "s1920"
internal const val JUSTWATCH_GRAPHQL_URL = "https://apis.justwatch.com/graphql"

/**
 * Pure parser for Apple's RSS JSON, kept as the preview source. Apple returns
 * `feed.entry` as an array for several entries and as a single object for one.
 * Anything without an HTTPS poster and an HTTPS preview is dropped.
 */
internal fun parseOnlineFeed(raw: String, limit: Int = ONLINE_MAX_MOVIES): List<OnlineMovie> {
    if (raw.isBlank() || raw.length > ONLINE_CACHE_MAX_BYTES) return emptyList()
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    val feed = root.optJSONObject("feed") ?: return emptyList()
    val entries = when (val value = feed.opt("entry")) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
        is JSONObject -> listOf(value)
        else -> emptyList()
    }
    val result = ArrayList<OnlineMovie>(entries.size)
    entries.forEach { entry ->
        val movie = entry.toOnlineMovie() ?: return@forEach
        if (result.none { it.id == movie.id }) result += movie
        if (result.size >= limit.coerceIn(1, JUSTWATCH_CHART_LIMIT)) return result
    }
    return result
}

/**
 * Pure parser for JustWatch's RU popular-titles chart. It only reads fields
 * that the endpoint actually returned: a localized title, the original title,
 * the release year, a 2:3 poster and 0..n wide backdrops. The first backdrop
 * is the wide hero; a missing poster falls back to that backdrop and a title
 * with neither is dropped instead of being rendered as invented art.
 */
internal fun parseJustWatchChart(raw: String, limit: Int = JUSTWATCH_CHART_LIMIT): List<OnlineMovie> {
    if (raw.isBlank() || raw.length > ONLINE_CACHE_MAX_BYTES) return emptyList()
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
    val edges = root.optJSONObject("data")
        ?.optJSONObject("popularTitles")
        ?.optJSONArray("edges")
        ?: return emptyList()
    val result = ArrayList<OnlineMovie>(minOf(edges.length(), limit))
    for (index in 0 until edges.length()) {
        val content = edges.optJSONObject(index)
            ?.optJSONObject("node")
            ?.optJSONObject("content")
            ?: continue
        val title = content.optString("title").trim().takeIf { it.isNotEmpty() } ?: continue
        val id = edges.optJSONObject(index)
            ?.optJSONObject("node")
            ?.optString("id")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: continue
        val poster = justWatchImage(content.optString("posterUrl"), JUSTWATCH_POSTER_PROFILE)
        val landscape = firstBackdrop(content.optJSONArray("backdrops"))
        val usablePoster = poster ?: landscape ?: continue
        val year = content.optInt("originalReleaseYear", 0)
            .takeIf { it in 1900..2100 }
            ?.toString()
        result += OnlineMovie(
            id = "justwatch|$id",
            title = title.take(160),
            year = year,
            posterUrl = usablePoster,
            previewUrl = null,
            landscapeUrl = landscape,
            originalTitle = content.optString("originalTitle").trim().takeIf { it.isNotEmpty() },
            tmdbId = content.optJSONObject("externalIds")
                ?.optInt("tmdbId", 0)
                ?.takeIf { it > 0 }
                ?.toString(),
            synopsis = content.optString("shortDescription").trim()
                .takeIf { it.isNotEmpty() }
                ?.take(1_200),
        )
        if (result.size >= limit.coerceIn(1, JUSTWATCH_CHART_LIMIT)) break
    }
    return result
}

private fun firstBackdrop(backdrops: JSONArray?): String? {
    for (index in 0 until (backdrops?.length() ?: 0)) {
        val path = backdrops?.optJSONObject(index)?.optString("backdropUrl").orEmpty()
        val url = justWatchImage(path, JUSTWATCH_BACKDROP_PROFILE) ?: continue
        return url
    }
    return null
}

/** JustWatch serves one fixed set of profiles; an unknown size is a 400, not a crop. */
private fun justWatchImage(path: String, profile: String): String? {
    val value = path.trim()
    if (value.isEmpty()) return null
    val absolute = if (value.startsWith("/")) "https://images.justwatch.com$value" else value
    val sized = absolute
        .replace("{profile}", profile)
        .replace("{format}", "jpg")
    return secureUrl(sized, "justwatch.com")
}

private fun JSONObject.toOnlineMovie(): OnlineMovie? {
    val title = optJSONObject("im:name")?.optString("label")
        ?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val poster = artworkUrl(opt("im:image"))
    val preview = previewUrl(opt("link"))
    if (poster == null || preview == null) return null
    val id = onlineId(optJSONObject("id")) ?: return null
    val year = optJSONObject("im:releaseDate")?.optString("label")
        ?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    return OnlineMovie(
        id = id,
        title = title.take(160),
        year = year,
        posterUrl = poster,
        previewUrl = preview,
        landscapeUrl = null,
        originalTitle = null,
    )
}

private fun artworkUrl(value: Any?): String? {
    val images = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
        is JSONObject -> listOf(value)
        else -> emptyList()
    }
    val source = images.mapNotNull { image ->
        val url = image.optString("label").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val height = image.optJSONObject("attributes")?.optString("height")?.toIntOrNull() ?: 0
        height to url
    }.maxByOrNull { it.first }?.second ?: return null
    val base = source.substringBeforeLast('/', "")
    if (base.isBlank()) return null
    val target = "$base/${ONLINE_IMAGE_WIDTH}x${ONLINE_IMAGE_HEIGHT}bb.jpg"
    return secureUrl(target, "mzstatic.com")
}

private fun previewUrl(value: Any?): String? {
    val links = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { value.optJSONObject(it) }
        is JSONObject -> listOf(value)
        else -> emptyList()
    }
    var best: Pair<Int, String>? = null
    links.forEach { link ->
        val attributes = link.optJSONObject("attributes") ?: return@forEach
        if (attributes.optString("rel") != "enclosure") return@forEach
        if (!attributes.optString("type").startsWith("video/")) return@forEach
        val href = attributes.optString("href").takeIf { it.isNotBlank() } ?: return@forEach
        val url = secureUrl(href, "apple.com", "mzstatic.com") ?: return@forEach
        // Apple currently publishes one 640-wide progressive file. If a
        // storefront ever adds an HD enclosure, prefer the tallest one instead
        // of silently taking whichever link happened to be first.
        val height = Regex("(?:^|[._-])(\\d{3,4})x(\\d{3,4})(?:[._-]|$)")
            .find(url)
            ?.groupValues
            ?.getOrNull(2)
            ?.toIntOrNull()
            ?: 0
        if (best == null || height > best!!.first) best = height to url
    }
    return best?.second
}

private fun onlineId(id: JSONObject?): String? {
    val digits = id?.optJSONObject("attributes")?.optString("im:id")
        ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    if (digits != null) return "apple|$digits"
    val label = id?.optString("label")?.takeIf { it.isNotBlank() } ?: return null
    return "apple|${label.hashCode().toUInt().toString(16)}"
}

private fun secureUrl(value: String, vararg allowedHostSuffixes: String): String? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    val host = uri.host?.lowercase() ?: return null
    if (allowedHostSuffixes.none { host == it || host.endsWith(".$it") }) return null
    return value
}

/**
 * An index of Apple's official previews by normalized title and, when both
 * sides have one, by release year. This deliberately does not guess a match
 * from a partial or fuzzy title: a wrong trailer is worse than no trailer.
 */
internal class OnlinePreviewIndex(movies: List<OnlineMovie>) {
    private val exact = HashMap<String, OnlineMovie>()
    private val loose = HashMap<String, OnlineMovie>()

    init {
        movies.forEach { movie ->
            if (movie.previewUrl.isNullOrBlank()) return@forEach
            keys(movie.title, movie.originalTitle).forEach { key ->
                movie.year?.let { exact.putIfAbsent("$key|$it", movie) }
                loose.putIfAbsent(key, movie)
            }
        }
    }

    fun match(title: String, originalTitle: String?, year: String?): OnlineMovie? {
        val candidateKeys = keys(title, originalTitle)
        if (year != null) {
            candidateKeys.firstNotNullOfOrNull { exact["$it|$year"] }?.let { return it }
        }
        return candidateKeys.firstNotNullOfOrNull { key ->
            loose[key]?.takeIf { movie ->
                year == null || movie.year == null || movie.year == year
            }
        }
    }

    private fun keys(title: String, originalTitle: String?): List<String> =
        listOf(title, originalTitle.orEmpty())
            .map(::normalizeOnlineTitle)
            .filter { it.isNotEmpty() }
            .distinct()
}

internal fun normalizeOnlineTitle(raw: String): String {
    val withoutYear = raw.replace(Regex("\\((?:19|20)\\d{2}\\)"), " ")
    val decomposed = Normalizer.normalize(withoutYear, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    return decomposed
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9]+"), " ")
        .trim()
}

internal fun buildOnlinePreviewIndex(movies: List<OnlineMovie>): OnlinePreviewIndex =
    OnlinePreviewIndex(movies)

// ---------------------------------------------------------------------------
// Cache codec
// ---------------------------------------------------------------------------

private const val CACHE_VERSION = 3

internal fun encodeOnlineCache(cache: OnlineCache): String {
    val root = JSONObject()
        .put("v", CACHE_VERSION)
        .put("at", cache.fetchedAtUtc)
        .put("cc", cache.country)
    val movies = JSONArray()
    cache.movies.take(ONLINE_MAX_MOVIES).forEach { movie ->
        movies.put(
            JSONObject()
                .put("id", movie.id)
                .put("title", movie.title)
                .put("poster", movie.posterUrl)
                .apply {
                    movie.year?.let { put("year", it) }
                    movie.previewUrl?.let { put("preview", it) }
                    movie.landscapeUrl?.let { put("landscape", it) }
                    movie.originalTitle?.let { put("original", it) }
                    movie.tmdbId?.let { put("tmdb", it) }
                    movie.synopsis?.let { put("synopsis", it) }
                },
        )
    }
    root.put("movies", movies)
    return root.toString()
}

internal fun decodeOnlineCache(raw: String?): OnlineCache? {
    if (raw.isNullOrBlank() || raw.length > ONLINE_CACHE_MAX_BYTES) return null
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (root.optInt("v", 0) != CACHE_VERSION) return null
    val fetchedAt = root.optLong("at", 0L)
    val country = root.optString("cc").takeIf { it.length in 2..8 } ?: return null
    if (fetchedAt <= 0L) return null
    val array = root.optJSONArray("movies") ?: return null
    val movies = ArrayList<OnlineMovie>(minOf(array.length(), ONLINE_MAX_MOVIES))
    for (index in 0 until minOf(array.length(), ONLINE_MAX_MOVIES)) {
        val entry = array.optJSONObject(index) ?: continue
        val id = entry.optString("id").takeIf { it.isNotBlank() } ?: continue
        val title = entry.optString("title").takeIf { it.isNotBlank() } ?: continue
        val poster = secureUrl(entry.optString("poster"), "mzstatic.com", "justwatch.com") ?: continue
        val preview = entry.optString("preview")
            .takeIf { it.isNotBlank() }
            ?.let { secureUrl(it, "apple.com", "mzstatic.com") }
        val landscape = entry.optString("landscape")
            .takeIf { it.isNotBlank() }
            ?.let { secureUrl(it, "justwatch.com") }
        movies += OnlineMovie(
            id = id.take(256),
            title = title.take(160),
            year = entry.optString("year").takeIf { it.length == 4 },
            posterUrl = poster,
            previewUrl = preview,
            landscapeUrl = landscape,
            originalTitle = entry.optString("original").takeIf { it.isNotBlank() }?.take(160),
            tmdbId = entry.optString("tmdb")
                .takeIf { it.isNotBlank() && it.length <= 12 && it.all(Char::isDigit) },
            synopsis = entry.optString("synopsis").takeIf { it.isNotBlank() }?.take(1_200),
        )
    }
    return movies.takeIf { it.isNotEmpty() }?.let { OnlineCache(fetchedAt, country, it) }
}
