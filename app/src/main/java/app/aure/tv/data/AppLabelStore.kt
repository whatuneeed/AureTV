package app.aure.tv.data

import android.content.Context
import app.aure.tv.R
import org.json.JSONObject

/**
 * Names the user chose for app tiles. Android has no per-launcher rename API,
 * so the label lives in the launcher's own preferences and is applied after
 * PackageManager returns the default label. Packages that the user has not
 * renamed are absent, keeping the preference small.
 */
object AppLabelStore {
    internal const val KEY = "app_labels"
    private const val MAX_LABEL_CHARS = 64
    private const val MAX_ENTRIES = 500
    private const val MAX_RAW_CHARS = 512 * 1024

    private fun prefs(context: Context, prefsName: String) =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /** A malformed or oversized value is ignored instead of breaking Home. */
    fun labels(
        context: Context,
        prefsName: String = LauncherPrefs.NAME,
    ): Map<String, String> {
        val raw = SafePreferences.string(prefs(context, prefsName), KEY, null) ?: return emptyMap()
        if (raw.length > MAX_RAW_CHARS) return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        val keys = json.keys()
        while (keys.hasNext() && result.size < MAX_ENTRIES) {
            val packageName = keys.next()
            val value = json.optString(packageName).trim()
            if (packageName.isNotBlank() && value.isNotEmpty() && value.length <= MAX_LABEL_CHARS) {
                result[packageName] = value
            }
        }
        return result
    }

    fun label(
        context: Context,
        packageName: String,
        prefsName: String = LauncherPrefs.NAME,
    ): String? = labels(context, prefsName)[packageName]

    /**
     * Stores a custom label, or removes it when [label] is blank. A blank value
     * restores the package manager's own name on the next scan.
     */
    fun setLabel(
        context: Context,
        packageName: String,
        label: String?,
        prefsName: String = LauncherPrefs.NAME,
    ) {
        if (packageName.isBlank()) return
        val values = labels(context, prefsName).toMutableMap()
        val normalized = label?.trim()?.take(MAX_LABEL_CHARS)?.takeIf { it.isNotEmpty() }
        if (normalized == null) {
            values.remove(packageName)
        } else {
            if (values.size >= MAX_ENTRIES && !values.containsKey(packageName)) return
            values[packageName] = normalized
        }
        val encoded = JSONObject(values as Map<*, *>).toString()
        prefs(context, prefsName).edit().putString(KEY, encoded).apply()
    }

    fun clearLabel(
        context: Context,
        packageName: String,
        prefsName: String = LauncherPrefs.NAME,
    ) {
        setLabel(context, packageName, null, prefsName)
    }

    /** Drops every custom name; used when a profile is reset. */
    fun clear(context: Context, prefsName: String = LauncherPrefs.NAME) {
        prefs(context, prefsName).edit().remove(KEY).apply()
    }

    /** Validated map → the exact preference value used by a restore. */
    internal fun encode(values: Map<String, String>): String {
        val sanitized = LinkedHashMap<String, String>()
        values.forEach { (packageName, label) ->
            if (sanitized.size >= MAX_ENTRIES) return@forEach
            val value = label.trim().take(MAX_LABEL_CHARS).takeIf { it.isNotEmpty() } ?: return@forEach
            if (packageName.isNotBlank()) sanitized[packageName] = value
        }
        return JSONObject(sanitized as Map<*, *>).toString()
    }

    /**
     * The package manager label, used by the "Reset Name" action so reset does
     * not depend on the possibly renamed value currently shown on the tile.
     */
    fun defaultLabel(context: Context, app: TvApp): String {
        if (app.packageName == context.packageName) {
            return context.getString(R.string.settings_tile_name)
        }
        val info = runCatching {
            context.packageManager.getApplicationInfo(app.packageName, 0)
        }.getOrNull()
        val managerLabel = info?.let {
            runCatching { context.packageManager.getApplicationLabel(it).toString() }.getOrNull()
        }.orEmpty()
        return managerLabel.ifBlank { app.label.ifBlank { app.packageName } }
    }
}
