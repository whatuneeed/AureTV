package com.whatuneed.ovisiontv.kotlin.online

import com.whatuneed.ovisiontv.kotlin.data.writeFileAtomically
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Apple's public Top Movies RSS, no account and no API key. The launcher sends
 * no cookie or identifier; it only asks for one country's public chart.
 */
internal class AppleRssClient(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 8_000,
) {
    suspend fun topMovies(country: String, limit: Int = 8): List<OnlineMovie> =
        withContext(Dispatchers.IO) {
            val safeCountry = country.lowercase(Locale.ROOT)
                .takeIf { it.length == 2 && it.all(Char::isLetter) } ?: "us"
            val safeLimit = limit.coerceIn(1, ONLINE_MAX_MOVIES)
            val url = "https://itunes.apple.com/$safeCountry/rss/topmovies/limit=$safeLimit/json"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "OVisionTV (Android TV)")
                setRequestProperty("Accept-Language", "en-US,en;q=0.8")
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                val bytes = connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    val output = java.io.ByteArrayOutputStream()
                    while (output.size() < ONLINE_CACHE_MAX_BYTES) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                if (bytes.size > ONLINE_CACHE_MAX_BYTES) return@withContext emptyList()
                parseOnlineFeed(bytes.toString(Charsets.UTF_8))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            } finally {
                connection.disconnect()
            }
        }
}

/**
 * A six-hour on-disk cache. The public chart changes slowly and a TV resume
 * must not hit Apple every time; a stale cache is still better than an empty
 * rail when the network is down, so it is returned on a failed refresh.
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
    private val client: AppleRssClient = AppleRssClient(),
) {
    private val cache = OnlineCacheStore(cacheFile)

    /**
     * Returns the chart for [country]. A fresh cache wins; otherwise the feed
     * is fetched once. If the fetch fails, the last known chart is returned
     * without pretending it is fresh, or an empty list if there is none.
     */
    suspend fun topMovies(
        country: String,
        nowUtc: Long = System.currentTimeMillis(),
    ): List<OnlineMovie> {
        cache.read(country, nowUtc)?.let { return it.movies }
        val fresh = client.topMovies(country)
        if (fresh.isNotEmpty()) {
            cache.write(OnlineCache(nowUtc, country, fresh))
            return fresh
        }
        return cache.readStale(country)?.movies.orEmpty()
    }
}
