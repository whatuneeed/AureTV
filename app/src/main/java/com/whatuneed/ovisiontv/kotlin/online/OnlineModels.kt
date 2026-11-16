package com.whatuneed.ovisiontv.kotlin.online

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * A real title from Apple's public iTunes Top Movies RSS feed. The launcher
 * only keeps what it can show and play: a title, its poster and the official
 * preview URL. No synopsis is invented, no genre is guessed.
 */
data class OnlineMovie(
    val id: String,
    val title: String,
    val year: String?,
    val posterUrl: String,
    val previewUrl: String,
)

/** One cached feed. [country] is part of the key: ru and us lists differ. */
data class OnlineCache(
    val fetchedAtUtc: Long,
    val country: String,
    val movies: List<OnlineMovie>,
)

internal const val ONLINE_CACHE_MAX_BYTES = 512 * 1024
internal const val ONLINE_MAX_MOVIES = 12
internal const val ONLINE_IMAGE_WIDTH = 600
internal const val ONLINE_IMAGE_HEIGHT = 900

/**
 * Pure parser for the RSS JSON. Apple returns `feed.entry` as an array for
 * several entries and as a single object for one entry; both shapes are real.
 * Anything without an HTTPS poster and an HTTPS preview is dropped, so a card
 * can never open a cleartext URL or a foreign package.
 */
internal fun parseOnlineFeed(raw: String): List<OnlineMovie> {
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
        if (result.size >= ONLINE_MAX_MOVIES) return result
    }
    return result
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
    links.forEach { link ->
        val attributes = link.optJSONObject("attributes") ?: return@forEach
        if (attributes.optString("rel") != "enclosure") return@forEach
        val href = attributes.optString("href").takeIf { it.isNotBlank() } ?: return@forEach
        val url = secureUrl(href, "apple.com", "mzstatic.com") ?: return@forEach
        if (attributes.optString("type").startsWith("video/")) return url
    }
    return null
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

// ---------------------------------------------------------------------------
// Cache codec
// ---------------------------------------------------------------------------

internal fun encodeOnlineCache(cache: OnlineCache): String {
    val root = JSONObject()
        .put("v", 1)
        .put("at", cache.fetchedAtUtc)
        .put("cc", cache.country)
    val movies = JSONArray()
    cache.movies.take(ONLINE_MAX_MOVIES).forEach { movie ->
        movies.put(
            JSONObject()
                .put("id", movie.id)
                .put("title", movie.title)
                .put("poster", movie.posterUrl)
                .put("preview", movie.previewUrl)
                .apply { movie.year?.let { put("year", it) } },
        )
    }
    root.put("movies", movies)
    return root.toString()
}

internal fun decodeOnlineCache(raw: String?): OnlineCache? {
    if (raw.isNullOrBlank() || raw.length > ONLINE_CACHE_MAX_BYTES) return null
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (root.optInt("v", 0) != 1) return null
    val fetchedAt = root.optLong("at", 0L)
    val country = root.optString("cc").takeIf { it.length in 2..8 } ?: return null
    if (fetchedAt <= 0L) return null
    val array = root.optJSONArray("movies") ?: return null
    val movies = ArrayList<OnlineMovie>(minOf(array.length(), ONLINE_MAX_MOVIES))
    for (index in 0 until minOf(array.length(), ONLINE_MAX_MOVIES)) {
        val entry = array.optJSONObject(index) ?: continue
        val id = entry.optString("id").takeIf { it.isNotBlank() } ?: continue
        val title = entry.optString("title").takeIf { it.isNotBlank() } ?: continue
        val poster = secureUrl(entry.optString("poster"), "mzstatic.com") ?: continue
        val preview = secureUrl(entry.optString("preview"), "apple.com", "mzstatic.com") ?: continue
        movies += OnlineMovie(
            id = id.take(256),
            title = title.take(160),
            year = entry.optString("year").takeIf { it.length == 4 },
            posterUrl = poster,
            previewUrl = preview,
        )
    }
    return movies.takeIf { it.isNotEmpty() }?.let { OnlineCache(fetchedAt, country, it) }
}
