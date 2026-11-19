package com.whatuneed.ovisiontv.kotlin.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/** One geocoding hit: the city name and the "region, country" line under it. */
data class CityPlace(
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
)

/**
 * City search for the wizard's weather page and later for Settings, ported from
 * the Java launcher's Weather.search: Open-Meteo geocoding, no API key. The
 * parsing is kept separate from the network call so the dedup and the Russian
 * region rule are testable without a socket.
 */
object CitySearch {
    private const val TAG = "CitySearch"
    private const val ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_TOTAL_MS = 15_000L
    private const val MAX_BYTES = 512 * 1024
    private const val MAX_REDIRECTS = 3
    internal const val HOST = "geocoding-api.open-meteo.com"

    /** Open-Meteo lists villages that share a name and a country as separate rows; on
     *  screen they read as one and the same city repeated, so keep the first of each. */
    fun parse(body: String): List<CityPlace> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<CityPlace>()
        val seen = HashSet<String>()
        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            val name = row.optString("name")
            if (name.isEmpty()) continue
            val detail = listOf("admin1", "country")
                .map { row.optString(it, "") }
                .filter { it.isNotEmpty() }
                .joinToString(", ") { region(it) }
            val key = "$name\u0000$detail"
            if (!seen.add(key)) continue
            val lat = row.optDouble("latitude")
            val lon = row.optDouble("longitude")
            if (lat.isNaN() || lon.isNaN()) continue
            out.add(CityPlace(name, detail, lat, lon))
        }
        return out
    }

    /** Open-Meteo writes "Самарская Область" beside "Ленинградская область": Russian
     *  wants the type word lower-case, exactly as the Java launcher corrected it. */
    fun region(source: String): String {
        val words = source.split(" ")
        val builder = StringBuilder(source.length)
        words.forEachIndexed { index, word ->
            if (index > 0) builder.append(' ')
            val lower = word.lowercase()
            if (index > 0 && (lower == "область" || lower == "край" || lower == "автономный" || lower == "округ")) {
                builder.append(word.replaceFirstChar { it.lowercase() })
            } else {
                builder.append(word)
            }
        }
        return builder.toString()
    }

    /** Network call; null means the request itself failed (offline, timeout). */
    suspend fun search(query: String, language: String): List<CityPlace>? {
        val url = "$ENDPOINT?count=8&language=$language&name=" +
            URLEncoder.encode(query.trim(), "UTF-8")
        val body = download(url) ?: return null
        return runCatching { parse(body) }
            .onFailure { Log.w(TAG, "city search parse failed", it) }
            .getOrNull()
    }

    private suspend fun download(url: String): String? {
        return try {
            downloadOnce(url, redirects = 0, deadline = System.nanoTime() + MAX_TOTAL_MS * 1_000_000L)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "city search failed", error)
            null
        }
    }

    private suspend fun downloadOnce(url: String, redirects: Int, deadline: Long): String? {
        currentCoroutineContext().ensureActive()
        if (!citySearchUrlIsTrusted(url)) return null
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        // Every hop is revalidated: the geocoder has no reason to send us
        // anywhere except its own HTTPS host.
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                if (redirects >= MAX_REDIRECTS) return null
                val location = connection.getHeaderField("Location") ?: return null
                val next = resolveRedirectUrl(url, location) ?: return null
                return downloadOnce(next, redirects + 1, deadline)
            }
            if (code != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    // A cancelled search must stop reading and close the socket.
                    currentCoroutineContext().ensureActive()
                    if (System.nanoTime() > deadline) return null
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) return null
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

}

/** Redirects may only stay on the geocoder's own HTTPS host. */
internal fun citySearchUrlIsTrusted(raw: String): Boolean {
    val uri = runCatching { URI(raw) }.getOrNull() ?: return false
    if (uri.scheme?.equals("https", ignoreCase = true) != true) return false
    if (uri.host?.equals(CitySearch.HOST, ignoreCase = true) != true) return false
    return uri.port == -1 || uri.port == 443
}
