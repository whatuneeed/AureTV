package com.whatuneed.ovisiontv.kotlin.discovery

import android.content.Context
import android.content.SharedPreferences
import com.whatuneed.ovisiontv.kotlin.data.SafePreferences
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_JSON_CHARS = 512 * 1024
private const val MAX_USAGE_KEYS = 512
private const val MAX_ITEMS = 1_000
private const val MAX_SHOWN_KEYS = 1_000

// A TV provider is untrusted input. The total JSON cap alone still lets one
// enormous title or URI occupy the whole record, so each persisted field also
// has a hard bound. Deep links are dropped rather than truncated into a
// different intent.
internal const val MAX_ITEM_ID_CHARS = 512
internal const val MAX_ITEM_PACKAGE_CHARS = 255
internal const val MAX_ITEM_TITLE_CHARS = 256
internal const val MAX_ITEM_SUBTITLE_CHARS = 256
internal const val MAX_ITEM_LABEL_CHARS = 256
internal const val MAX_ITEM_ACTIVITY_CHARS = 256
internal const val MAX_ITEM_URI_CHARS = 4_096

internal object DiscoveryKeys {
    const val USAGE = "discovery_usage"
    const val FAVORITES = "discovery_favorites"
    const val HIDDEN = "discovery_hidden"
    const val SHOWN = "discovery_shown"
    const val PERSONALIZATION = "discovery_personalization"

    // p1 shares this file with the rest of the launcher, so these two keys stay
    // byte-identical to ContentPreferences and the wizard keeps working.
    const val RECOMMENDATIONS = "content_recommendations"
    const val CONTINUE = "content_continue_watching"
}

internal data class Usage(val count: Int, val lastUtc: Long)

/** Profile-scoped signals. Only package names, ids, times and counts are kept. */
class DiscoveryStore(context: Context, prefsName: String) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    internal fun usage(): Map<String, Usage> = decodeUsage(SafePreferences.string(prefs, DiscoveryKeys.USAGE, null))

    fun recordLaunch(packageName: String, nowUtc: Long = System.currentTimeMillis()) {
        if (packageName.isBlank()) return
        val usage = usage().toMutableMap()
        val previous = usage[packageName]
        usage[packageName] = Usage(
            count = ((previous?.count ?: 0) + 1).coerceAtMost(MAX_COUNT),
            lastUtc = nowUtc,
        )
        prefs.edit()
            .putString(DiscoveryKeys.USAGE, encodeUsage(pruneUsage(usage, USAGE_LIMIT)))
            .apply()
    }

    fun favorites(): List<DiscoveryItem> =
        decodeItems(SafePreferences.string(prefs, DiscoveryKeys.FAVORITES, null))

    fun isFavorite(id: String): Boolean = favorites().any { it.id == id }

    fun addFavorite(item: DiscoveryItem) {
        val next = listOf(item) + favorites().filterNot { it.id == item.id }
        prefs.edit()
            .putString(DiscoveryKeys.FAVORITES, encodeItems(pruneItems(next, ITEM_LIMIT)))
            .apply()
    }

    fun removeFavorite(id: String) {
        val next = favorites().filterNot { it.id == id }
        prefs.edit().putString(DiscoveryKeys.FAVORITES, encodeItems(next)).apply()
    }

    fun hidden(): List<DiscoveryItem> =
        decodeItems(SafePreferences.string(prefs, DiscoveryKeys.HIDDEN, null))

    fun hiddenIds(): Set<String> = hidden().mapTo(HashSet()) { it.id }

    fun hide(item: DiscoveryItem) {
        val next = listOf(item) + hidden().filterNot { it.id == item.id }
        prefs.edit()
            .putString(DiscoveryKeys.HIDDEN, encodeItems(pruneItems(next, ITEM_LIMIT)))
            .apply()
    }

    fun unhide(id: String) {
        val next = hidden().filterNot { it.id == id }
        prefs.edit().putString(DiscoveryKeys.HIDDEN, encodeItems(next)).apply()
    }

    fun shownAt(): Map<String, Long> = decodeShown(SafePreferences.string(prefs, DiscoveryKeys.SHOWN, null))

    fun markShown(section: DiscoverySection, items: List<DiscoveryItem>, nowUtc: Long) {
        if (items.isEmpty()) return
        val shown = shownAt().toMutableMap()
        items.forEach { shown["${section.name}|${it.id}"] = nowUtc }
        prefs.edit()
            .putString(DiscoveryKeys.SHOWN, encodeShown(pruneShown(shown, SHOWN_LIMIT)))
            .apply()
    }

    /**
     * Removes what the launcher learned by itself. Favorites and hidden cards
     * are explicit user choices, so they survive this action.
     */
    fun clearHistory() {
        prefs.edit()
            .remove(DiscoveryKeys.USAGE)
            .remove(DiscoveryKeys.SHOWN)
            .apply()
    }

    /** Profile deletion: nothing of this profile may survive. */
    fun clearAll() {
        prefs.edit()
            .remove(DiscoveryKeys.USAGE)
            .remove(DiscoveryKeys.FAVORITES)
            .remove(DiscoveryKeys.HIDDEN)
            .remove(DiscoveryKeys.SHOWN)
            .remove(DiscoveryKeys.PERSONALIZATION)
            .remove(DiscoveryKeys.RECOMMENDATIONS)
            .remove(DiscoveryKeys.CONTINUE)
            .apply()
    }

    private companion object {
        const val USAGE_LIMIT = 64
        const val ITEM_LIMIT = 100
        const val SHOWN_LIMIT = 128
        const val MAX_COUNT = 1_000_000
    }
}

// ---------------------------------------------------------------------------
// Codecs are pure so the profile rules can be tested on the JVM (the Android
// SharedPreferences itself is exercised by the instrumented suite).
// ---------------------------------------------------------------------------

internal fun encodeUsage(usage: Map<String, Usage>): String {
    val root = JSONObject()
    usage.forEach { (packageName, value) ->
        root.put(
            packageName,
            JSONObject().put("c", value.count).put("t", value.lastUtc),
        )
    }
    return root.toString()
}

internal fun decodeUsage(raw: String?): Map<String, Usage> {
    if (raw.isNullOrBlank() || raw.length > MAX_JSON_CHARS) return emptyMap()
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
    val result = LinkedHashMap<String, Usage>()
    val keys = root.keys()
    while (keys.hasNext() && result.size < MAX_USAGE_KEYS) {
        val packageName = keys.next()
        if (packageName.isBlank()) continue
        val entry = root.optJSONObject(packageName) ?: continue
        val count = entry.optInt("c", 0)
        val last = entry.optLong("t", 0L)
        if (count <= 0 || last <= 0L) continue
        result[packageName] = Usage(count, last)
    }
    return result
}

internal fun pruneUsage(usage: Map<String, Usage>, limit: Int): Map<String, Usage> {
    if (limit <= 0) return emptyMap()
    return usage.entries
        .sortedByDescending { it.value.lastUtc }
        .take(limit)
        .associateTo(LinkedHashMap()) { it.key to it.value }
}

internal fun encodeItems(items: List<DiscoveryItem>): String {
    val array = JSONArray()
    items.asSequence()
        .filter { it.id.length <= MAX_ITEM_ID_CHARS }
        .filter { it.packageName.length <= MAX_ITEM_PACKAGE_CHARS }
        .forEach { source ->
            val item = source.boundedForStorage()
            val entry = JSONObject()
                .put("id", item.id)
                .put("src", item.source.name)
                .put("kind", item.kind.name)
                .put("pkg", item.packageName)
                .put("title", item.title)
                .put("reason", item.reason.name)
            item.packageLabel?.let { entry.put("label", it) }
            item.subtitle?.let { entry.put("sub", it) }
            item.landscapeUri?.let { entry.put("land", it) }
            item.posterUri?.let { entry.put("poster", it) }
            item.intentUri?.let { entry.put("intent", it) }
            item.activityName?.let { entry.put("act", it) }
            item.progress?.let { entry.put("prog", it.toDouble()) }
            if (item.isContinue) entry.put("cont", true)
            item.lastEngagementUtc?.let { entry.put("eng", it) }
            array.put(entry)
        }
    return array.toString()
}

internal fun decodeItems(raw: String?): List<DiscoveryItem> {
    if (raw.isNullOrBlank() || raw.length > MAX_JSON_CHARS) return emptyList()
    val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    val count = minOf(array.length(), MAX_ITEMS)
    val result = ArrayList<DiscoveryItem>(count)
    for (index in 0 until count) {
        val entry = array.optJSONObject(index) ?: continue
        val id = entry.optString("id").takeIf {
            it.isNotBlank() && it.length <= MAX_ITEM_ID_CHARS
        } ?: continue
        val packageName = entry.optString("pkg").takeIf {
            it.isNotBlank() && it.length <= MAX_ITEM_PACKAGE_CHARS
        } ?: continue
        val title = boundedItemText(entry.optString("title"), MAX_ITEM_TITLE_CHARS) ?: continue
        val progress = if (entry.has("prog")) entry.optDouble("prog").toFloat() else null
        result += DiscoveryItem(
            id = id,
            source = enumOr(entry.optString("src"), DiscoverySource.TV_PROVIDER),
            kind = enumOr(entry.optString("kind"), DiscoveryKind.UNKNOWN),
            packageName = packageName,
            packageLabel = boundedItemText(entry.optString("label"), MAX_ITEM_LABEL_CHARS),
            title = title,
            subtitle = boundedItemText(entry.optString("sub"), MAX_ITEM_SUBTITLE_CHARS),
            landscapeUri = boundedItemUri(entry.optString("land"), MAX_ITEM_URI_CHARS),
            posterUri = boundedItemUri(entry.optString("poster"), MAX_ITEM_URI_CHARS),
            intentUri = boundedItemUri(entry.optString("intent"), MAX_ITEM_URI_CHARS),
            activityName = boundedItemText(entry.optString("act"), MAX_ITEM_ACTIVITY_CHARS),
            progress = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f),
            isContinue = entry.optBoolean("cont", false),
            lastEngagementUtc = entry.optLong("eng", 0L).takeIf { it > 0L },
            reason = enumOr(entry.optString("reason"), DiscoveryReason.NEW_FROM_APP),
        ).boundedForStorage()
    }
    return result
}

private fun DiscoveryItem.boundedForStorage(): DiscoveryItem = copy(
    title = title.take(MAX_ITEM_TITLE_CHARS),
    subtitle = subtitle?.take(MAX_ITEM_SUBTITLE_CHARS),
    packageLabel = packageLabel?.take(MAX_ITEM_LABEL_CHARS),
    landscapeUri = landscapeUri?.takeIf { it.length <= MAX_ITEM_URI_CHARS },
    posterUri = posterUri?.takeIf { it.length <= MAX_ITEM_URI_CHARS },
    intentUri = intentUri?.takeIf { it.length <= MAX_ITEM_URI_CHARS },
    activityName = activityName?.take(MAX_ITEM_ACTIVITY_CHARS),
)

private fun boundedItemText(value: String?, max: Int): String? =
    value?.take(max)?.takeIf { it.isNotBlank() }

private fun boundedItemUri(value: String?, max: Int): String? =
    value?.takeIf { it.isNotBlank() && it.length <= max }

internal fun pruneItems(items: List<DiscoveryItem>, limit: Int): List<DiscoveryItem> {
    if (limit <= 0) return emptyList()
    val seen = HashSet<String>()
    return items.filter { it.id.isNotBlank() && seen.add(it.id) }.take(limit)
}

internal fun encodeShown(shown: Map<String, Long>): String {
    val root = JSONObject()
    shown.forEach { (key, value) -> root.put(key, value) }
    return root.toString()
}

internal fun decodeShown(raw: String?): Map<String, Long> {
    if (raw.isNullOrBlank() || raw.length > MAX_JSON_CHARS) return emptyMap()
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
    val result = LinkedHashMap<String, Long>()
    val keys = root.keys()
    while (keys.hasNext() && result.size < MAX_SHOWN_KEYS) {
        val key = keys.next()
        val value = root.optLong(key, 0L)
        if (value > 0L) result[key] = value
    }
    return result
}

internal fun pruneShown(shown: Map<String, Long>, limit: Int): Map<String, Long> {
    if (limit <= 0) return emptyMap()
    return shown.entries
        .sortedByDescending { it.value }
        .take(limit)
        .associateTo(LinkedHashMap()) { it.key to it.value }
}

private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback
