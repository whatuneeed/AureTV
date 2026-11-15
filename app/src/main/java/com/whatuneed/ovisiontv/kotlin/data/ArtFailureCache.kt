package com.whatuneed.ovisiontv.kotlin.data

/**
 * Remembers artwork URLs that just failed, so a recomposition does not open the
 * same dead host again and again. A failure expires: a temporarily offline TV
 * must be able to load the poster later without restarting the launcher.
 */
internal class ArtFailureCache(
    private val ttlMs: Long = 60_000L,
    private val maxEntries: Int = 32,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val entries = LinkedHashMap<String, Long>(16, 0.75f, true)

    fun isFailed(key: String): Boolean {
        val at = entries[key] ?: return false
        if (now() - at < ttlMs) return true
        entries.remove(key)
        return false
    }

    fun markFailed(key: String) {
        if (maxEntries <= 0) return
        entries.remove(key)
        entries[key] = now()
        while (entries.size > maxEntries) {
            val eldest = entries.entries.iterator()
            if (eldest.hasNext()) {
                eldest.next()
                eldest.remove()
            } else {
                break
            }
        }
    }

    fun clear(key: String) {
        entries.remove(key)
    }
}
