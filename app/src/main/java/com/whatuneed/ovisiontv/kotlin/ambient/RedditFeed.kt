package com.whatuneed.ovisiontv.kotlin.ambient

import android.content.Context
import android.util.Log
import com.whatuneed.ovisiontv.kotlin.data.SafePreferences
import com.whatuneed.ovisiontv.kotlin.data.writeFileAtomically
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/** A parsed public Reddit listing and when it was fetched. */
data class RedditFeed(
    val items: List<RedditWallpaper>,
    val fetchedAt: Long,
    val stale: Boolean,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Parses the public Atom feed. Reddit's JSON endpoint requires OAuth on many
 * networks; the feed is the no-account path and is therefore not a stable API.
 * Only image URLs on Reddit's own image hosts are accepted, so a feed cannot
 * turn the launcher into an arbitrary URL fetcher.
 */
object RedditFeedParser {
    const val MAX_ITEMS = 100
    private val imageSrc = Regex(
        """<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["']""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val linkHref = Regex(
        """<a\b[^>]*\bhref\s*=\s*["']([^"']+)["']""",
        setOf(RegexOption.IGNORE_CASE),
    )

    fun parse(xml: String, community: String): List<RedditWallpaper> {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            // The feed is untrusted input; never resolve a DTD or an external
            // entity while parsing it.
            runCatching {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false,
                )
            }
            runCatching {
                factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false,
                )
            }
            // Android's built-in factory does not implement both setters; an
            // unsupported feature must not turn a valid feed into a failure.
            runCatching { factory.isXIncludeAware = false }
            runCatching { factory.isExpandEntityReferences = false }
            val builder = factory.newDocumentBuilder()
            // Even if a platform ignores the feature flags above, an external
            // entity must never turn a fetched feed into a second request.
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            builder.parse(InputSource(StringReader(xml)))
        } catch (error: Exception) {
            // Log is not available in plain JVM tests; never let logging turn
            // untrusted input into a crash.
            runCatching { Log.w("RedditFeed", "feed parse failed", error) }
            return emptyList()
        }

        val entries = document.getElementsByTagName("entry")
        val found = ArrayList<RedditWallpaper>()
        val seen = HashSet<String>()
        for (index in 0 until entries.length) {
            if (found.size >= MAX_ITEMS) break
            val entry = entries.item(index) as? Element ?: continue
            val id = childText(entry, "id").ifBlank { "entry-$index" }
            if (!seen.add(id)) continue
            val content = childText(entry, "content")
            if (content.isBlank()) continue
            val preview = imageSrc.findAll(content)
                .mapNotNull { it.groupValues.getOrNull(1) }
                .mapNotNull(::safeImageUrl)
                .firstOrNull { uriHost(it) in PREVIEW_HOSTS }
                ?: imageSrc.findAll(content)
                    .mapNotNull { it.groupValues.getOrNull(1) }
                    .mapNotNull(::safeImageUrl)
                    .firstOrNull { uriHost(it) in IMAGE_HOSTS }
            val direct = linkHref.findAll(content)
                .mapNotNull { it.groupValues.getOrNull(1) }
                .mapNotNull(::safeImageUrl)
                .firstOrNull { uriHost(it) in IMAGE_HOSTS }
            val previewUrl = preview ?: direct ?: continue
            val imageUrl = direct ?: previewUrl
            val postUrl = childElements(entry, "link")
                .firstOrNull { it.getAttribute("rel").equals("alternate", true) }
                ?.getAttribute("href")
                ?.takeIf { safePostUrl(it) }
                .orEmpty()
            val title = childText(entry, "title")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(200)
                .ifBlank { "Reddit image" }
            val author = childElements(entry, "author")
                .firstOrNull()
                ?.let { childText(it, "name") }
                .orEmpty()
                .removePrefix("/u/")
                .removePrefix("u/")
                .trim()
                .take(80)
            found += RedditWallpaper(
                id = id,
                title = title,
                author = author,
                community = community,
                previewUrl = previewUrl,
                imageUrl = imageUrl,
                postUrl = postUrl,
            )
        }
        return found
    }

    private fun childElements(parent: Element, name: String): List<Element> {
        val children = parent.childNodes
        val result = ArrayList<Element>()
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.tagName.equals(name, ignoreCase = true)) {
                result += child
            } else if (child.tagName.equals("${parent.tagName}:$name", ignoreCase = true)) {
                result += child
            }
        }
        return result
    }

    private fun childText(parent: Element, name: String): String =
        childElements(parent, name).firstOrNull()?.textContent.orEmpty()

    private fun uriHost(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()

    internal fun safeImageUrl(raw: String): String? {
        val value = raw.trim().replace("&amp;", "&")
        if (value.length > 2048) return null
        val parsed = runCatching { URI(value) }.getOrNull() ?: return null
        if (parsed.scheme?.equals("https", ignoreCase = true) != true) return null
        if (parsed.userInfo != null) return null
        if (parsed.port != -1 && parsed.port != 443) return null
        val host = parsed.host?.lowercase() ?: return null
        if (host !in IMAGE_HOSTS && host !in PREVIEW_HOSTS) return null
        return parsed.toASCIIString()
    }

    internal fun safePostUrl(raw: String): Boolean {
        if (raw.length > 2048) return false
        val parsed = runCatching { URI(raw) }.getOrNull() ?: return false
        if (parsed.scheme?.equals("https", ignoreCase = true) != true) return false
        if (parsed.port != -1 && parsed.port != 443) return false
        return parsed.host?.lowercase() in setOf("www.reddit.com", "reddit.com")
    }

    private val IMAGE_HOSTS = setOf("i.redd.it")
    private val PREVIEW_HOSTS = setOf("preview.redd.it", "external-preview.redd.it")
}

/**
 * Small on-disk feed cache, keyed by subreddit. It is a cache, not user data:
 * deleting it only costs one network request. Only one file per subreddit is
 * retained, and the directory is capped so arbitrary subreddit names cannot
 * create an unbounded file collection.
 */
internal object RedditFeedStore {
    private const val DIR = "reddit_feeds"
    private const val KEEP_FILES = 12

    fun load(context: Context, subreddit: String): RedditFeed? {
        val file = file(context, subreddit) ?: return null
        if (!file.isFile) return null
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val fetchedAt = root.optLong("fetchedAt", 0L)
            val items = root.optJSONArray("items") ?: JSONArray()
            val parsed = ArrayList<RedditWallpaper>(items.length())
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val preview = RedditFeedParser.safeImageUrl(item.optString("previewUrl"))
                    ?: continue
                val image = RedditFeedParser.safeImageUrl(item.optString("imageUrl"))
                    ?: preview
                parsed += RedditWallpaper(
                    id = item.optString("id").ifBlank { "cached-$index" },
                    title = item.optString("title").take(200),
                    author = item.optString("author").take(80),
                    community = item.optString("community").ifBlank { subreddit },
                    previewUrl = preview,
                    imageUrl = image,
                    postUrl = item.optString("postUrl"),
                )
            }
            if (fetchedAt <= 0L || parsed.isEmpty()) null
            else RedditFeed(parsed, fetchedAt, stale = true)
        } catch (error: Exception) {
            Log.w("RedditFeed", "feed cache unreadable", error)
            null
        }
    }

    fun save(context: Context, subreddit: String, items: List<RedditWallpaper>, fetchedAt: Long) {
        val file = file(context, subreddit) ?: return
        val root = JSONObject()
        root.put("fetchedAt", fetchedAt)
        val array = JSONArray()
        items.take(RedditFeedParser.MAX_ITEMS).forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("author", item.author)
                    .put("community", item.community)
                    .put("previewUrl", item.previewUrl)
                    .put("imageUrl", item.imageUrl)
                    .put("postUrl", item.postUrl),
            )
        }
        root.put("items", array)
        if (!writeFileAtomically(file, root.toString().toByteArray(Charsets.UTF_8))) return
        prune(file.parentFile)
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.cacheDir, DIR).deleteRecursively() }
    }

    private fun file(context: Context, subreddit: String): File? {
        val dir = File(context.applicationContext.cacheDir, DIR)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(subreddit.lowercase().toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(dir, "$digest.json")
    }

    private fun prune(dir: File?) {
        val files = dir?.listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: return
        files.sortedByDescending { it.lastModified() }
            .drop(KEEP_FILES)
            .forEach { it.delete() }
    }
}

/**
 * Loads the feed at the chosen 6/12/24-hour interval. A stale cache is still
 * useful on a TV without working TLS, but it is never presented as fresh.
 * 429/other failures back off instead of retrying on every screen resume.
 */
object RedditFeedLoader {
    private const val TIMEOUT_MS = 20_000
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val COPY_CHUNK = 64 * 1024
    private const val USER_AGENT = "OVisionTV/0.5.7 (Android TV launcher)"
    private const val KEY_LAST_ATTEMPT = "ambient_reddit_last_attempt"
    private const val KEY_FAILURES = "ambient_reddit_failures"
    private const val MAX_FAILURES = 6
    private const val TAG = "RedditFeed"

    suspend fun load(
        context: Context,
        subreddit: String,
        refresh: RedditRefresh,
        force: Boolean = false,
    ): RedditFeed = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val cached = RedditFeedStore.load(app, subreddit)
        val now = System.currentTimeMillis()
        if (!force && cached != null && now - cached.fetchedAt < refresh.intervalMs()) {
            return@withContext cached.copy(stale = false)
        }
        val waitMs = nextAttemptAt(app) - now
        // Refresh Now is an explicit request and must not silently do nothing
        // just because an automatic retry is backing off. The server still
        // enforces its own limit, and a failed manual try extends the backoff.
        if (!force && waitMs > 0L) {
            return@withContext cached?.copy(stale = true)
                ?: RedditFeed(emptyList(), 0L, stale = true)
        }
        prefs(app).edit().putLong(KEY_LAST_ATTEMPT, now).apply()
        try {
            val xml = download(subreddit)
            val items = RedditFeedParser.parse(xml, subreddit)
            if (items.isEmpty()) throw IllegalStateException("empty feed")
            val fetchedAt = System.currentTimeMillis()
            RedditFeedStore.save(app, subreddit, items, fetchedAt)
            prefs(app).edit().remove(KEY_FAILURES).apply()
            RedditFeed(items, fetchedAt, stale = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "reddit feed load failed", error)
            val failures = (SafePreferences.int(prefs(app), KEY_FAILURES, 0) + 1)
                .coerceAtMost(MAX_FAILURES)
            prefs(app).edit().putInt(KEY_FAILURES, failures).apply()
            cached?.copy(stale = true) ?: RedditFeed(emptyList(), 0L, stale = true)
        }
    }

    fun clearBackoff(context: Context) {
        prefs(context.applicationContext).edit()
            .remove(KEY_LAST_ATTEMPT)
            .remove(KEY_FAILURES)
            .apply()
    }

    private fun nextAttemptAt(context: Context): Long {
        val last = SafePreferences.long(prefs(context), KEY_LAST_ATTEMPT, 0L)
        val failures = SafePreferences.int(prefs(context), KEY_FAILURES, 0)
        if (last <= 0L || failures <= 0) return 0L
        val delay = (15_000L shl (failures - 1).coerceIn(0, 5))
            .coerceAtMost(900_000L)
        return last + delay
    }

    private suspend fun download(subreddit: String): String {
        currentCoroutineContext().ensureActive()
        val encoded = URLEncoder.encode(subreddit, "UTF-8")
        val url = "https://www.reddit.com/r/$encoded/hot.rss?limit=100"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/atom+xml, application/xml;q=0.9")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("reddit HTTP $code")
            }
            if (connection.contentLengthLong > MAX_BYTES) {
                throw IllegalStateException("reddit feed too large")
            }
            val out = ByteArrayOutputStream(COPY_CHUNK)
            connection.inputStream.use { input ->
                val buffer = ByteArray(COPY_CHUNK)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw IllegalStateException("reddit feed too large")
                    out.write(buffer, 0, read)
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs.NAME,
            Context.MODE_PRIVATE,
        )
}
