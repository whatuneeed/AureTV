package app.aure.tv.ambient

import android.content.Context
import java.net.URI
import app.aure.tv.data.LauncherPrefs
import app.aure.tv.data.SafePreferences
import org.json.JSONArray

/** How often the public Reddit listing may be checked while Reddit is the mode. */
enum class RedditRefresh(val key: String, val hours: Int) {
    SIX("six", 6),
    TWELVE("twelve", 12),
    TWENTY_FOUR("twenty_four", 24),
    ;

    fun intervalMs(): Long = hours * 60L * 60L * 1000L

    companion object {
        fun fromKey(raw: String?): RedditRefresh =
            entries.firstOrNull { it.key == raw } ?: TWELVE
    }
}

/** Whole listing or only the posts the viewer explicitly picked. */
enum class RedditSelection(val key: String) {
    ALL("all"),
    SELECTED("selected"),
    ;

    companion object {
        fun fromKey(raw: String?): RedditSelection =
            entries.firstOrNull { it.key == raw } ?: ALL
    }
}

/**
 * One image post from a public Reddit Atom feed. [imageUrl] is the original
 * i.redd.it image when the feed exposes it; [previewUrl] is the Reddit CDN
 * thumbnail and is also the fallback. No Reddit account, cookie or token is
 * involved.
 */
data class RedditWallpaper(
    val id: String,
    val title: String,
    val author: String,
    val community: String,
    val previewUrl: String,
    val imageUrl: String,
    val postUrl: String,
)

/**
 * Device-local Reddit preference. The subreddit is intentionally not restricted
 * to an allowlist: the viewer controls the source. Kids Mode still never reads
 * this data, and the UI states that the feed is unmoderated third-party content.
 * The feed and images are cache only, not backup data.
 */
object AmbientRedditStore {
    const val DEFAULT_SUBREDDIT = "TVWallpapersSFW"
    const val KEY_SUBREDDIT = "ambient_reddit_subreddit"
    const val KEY_REFRESH = "ambient_reddit_refresh"
    const val KEY_SELECTION = "ambient_reddit_selection"
    const val KEY_SELECTED_IDS = "ambient_reddit_selected_ids"
    private const val MAX_SELECTED = 200
    private val nameRule = Regex("^[A-Za-z0-9_]{2,21}$")

    fun subreddit(context: Context): String =
        SafePreferences.string(prefs(context), KEY_SUBREDDIT, null)
            ?.let(::normalizeSubreddit)
            ?: DEFAULT_SUBREDDIT

    /**
     * Accepts `TVWallpapersSFW`, `r/TVWallpapersSFW`, `/r/TVWallpapersSFW/` and
     * the full Reddit URL. Returns null for anything that is not a subreddit
     * name, so a pasted URL cannot become a request to another host.
     */
    fun normalizeSubreddit(raw: String): String? {
        var value = raw.trim()
        if (value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
        ) {
            val parsed = runCatching { URI(value) }.getOrNull() ?: return null
            if (parsed.host?.equals("www.reddit.com", ignoreCase = true) != true &&
                parsed.host?.equals("reddit.com", ignoreCase = true) != true
            ) {
                return null
            }
            val parts = parsed.path.orEmpty().split('/').filter { it.isNotEmpty() }
            if (parts.any { it == "." || it == ".." }) return null
            val rIndex = parts.indexOfFirst { it.equals("r", ignoreCase = true) }
            value = if (rIndex >= 0) parts.getOrNull(rIndex + 1) ?: return null else return null
        }
        value = value.trim().trim('/')
        if (value.startsWith("r/", ignoreCase = true)) value = value.substring(2)
        if (!nameRule.matches(value)) return null
        return value
    }

    fun setSubreddit(context: Context, raw: String): Boolean {
        val normalized = normalizeSubreddit(raw) ?: return false
        val changed = normalized != subreddit(context)
        prefs(context).edit().putString(KEY_SUBREDDIT, normalized).apply()
        if (changed) setSelectedIds(context, emptySet())
        return true
    }

    fun refresh(context: Context): RedditRefresh =
        RedditRefresh.fromKey(
            SafePreferences.string(prefs(context), KEY_REFRESH, null),
        )

    fun setRefresh(context: Context, refresh: RedditRefresh) {
        prefs(context).edit().putString(KEY_REFRESH, refresh.key).apply()
    }

    fun selection(context: Context): RedditSelection =
        RedditSelection.fromKey(
            SafePreferences.string(prefs(context), KEY_SELECTION, null),
        )

    fun setSelection(context: Context, selection: RedditSelection) {
        prefs(context).edit().putString(KEY_SELECTION, selection.key).apply()
    }

    fun selectedIds(context: Context): Set<String> {
        val raw = SafePreferences.string(prefs(context), KEY_SELECTED_IDS, null)
            ?: return emptySet()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptySet()
        val ids = LinkedHashSet<String>()
        for (index in 0 until array.length().coerceAtMost(MAX_SELECTED)) {
            val id = array.optString(index).trim()
            if (id.isNotEmpty()) ids += id
        }
        return ids
    }

    fun setSelectedIds(context: Context, ids: Set<String>) {
        val array = JSONArray()
        ids.asSequence().filter { it.isNotBlank() }.take(MAX_SELECTED).forEach(array::put)
        prefs(context).edit().putString(KEY_SELECTED_IDS, array.toString()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_SUBREDDIT)
            .remove(KEY_REFRESH)
            .remove(KEY_SELECTION)
            .remove(KEY_SELECTED_IDS)
            .apply()
    }

    internal fun prefs(context: Context) =
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
}
