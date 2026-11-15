package com.whatuneed.ovisiontv.kotlin.data

import android.content.SharedPreferences
import android.util.Log

/**
 * Reads SharedPreferences without letting a value written by another schema,
 * a restore or manual editing crash the process. A wrong type is a data
 * problem, not a reason to lose the Home screen: the caller gets its default,
 * the failure is logged once per key, and the stored value is left untouched
 * for a later repair. Values are never logged, only the key name.
 */
internal object SafePreferences {
    private const val TAG = "SafePreferences"
    private val reported = java.util.Collections.synchronizedSet(HashSet<String>())

    fun string(prefs: SharedPreferences, key: String, fallback: String?): String? =
        safePrefRead(key, fallback, ::report) { prefs.getString(key, fallback) }

    fun stringSet(
        prefs: SharedPreferences,
        key: String,
        fallback: Set<String>?,
    ): Set<String>? = safePrefRead(key, fallback?.toHashSet(), ::report) {
        prefs.getStringSet(key, fallback)?.toHashSet()
    }

    fun boolean(prefs: SharedPreferences, key: String, fallback: Boolean): Boolean =
        safePrefRead(key, fallback, ::report) { prefs.getBoolean(key, fallback) }

    fun int(prefs: SharedPreferences, key: String, fallback: Int): Int =
        safePrefRead(key, fallback, ::report) { prefs.getInt(key, fallback) }

    fun long(prefs: SharedPreferences, key: String, fallback: Long): Long =
        safePrefRead(key, fallback, ::report) { prefs.getLong(key, fallback) }

    fun float(prefs: SharedPreferences, key: String, fallback: Float): Float =
        safePrefRead(key, fallback, ::report) { prefs.getFloat(key, fallback) }

    private fun report(key: String) {
        if (reported.add(key)) {
            Log.w(TAG, "preference '$key' has an unexpected type; using the default")
        }
    }
}

/**
 * The pure part of [SafePreferences], kept testable on the JVM.
 */
internal inline fun <T> safePrefRead(
    key: String,
    fallback: T,
    onCorrupt: (String) -> Unit = {},
    read: () -> T,
): T = try {
    read()
} catch (_: ClassCastException) {
    onCorrupt(key)
    fallback
}
