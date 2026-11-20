package app.aure.tv.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import app.aure.tv.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * tvOS keeps a user-defined order (04_APP_LIBRARY §4) and folders are root items
 * (05_FOLDERS), so the saved list mixes package names and folder objects. Plain
 * strings stay app entries, which keeps lists written by earlier builds readable.
 */
class HomeOrderStore(
    private val context: Context,
    prefsName: String = LauncherPrefs.NAME,
) {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /**
     * A disabled built-in is still installed, so PackageManager can tell us it
     * is a system package. Keep user-installed placeholders for a future
     * reinstall, but do not leave a dead system-service tile in the grid.
     */
    private fun keepMissingPackage(packageName: String): Boolean {
        val info = runCatching {
            context.packageManager.getApplicationInfo(
                packageName,
                PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_UNINSTALLED_PACKAGES,
            )
        }.getOrNull() ?: return true
        return info.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    fun applyOrder(apps: List<TvApp>): List<HomeItem> {
        val saved = SafePreferences.string(prefs, LauncherPrefs.KEY_HOME_ORDER, null)
        val built = buildHomeOrderRepair(
            saved = saved,
            apps = apps,
            defaultFolderName = context.getString(R.string.folder_default),
            hiddenFolderName = context.getString(R.string.hidden_folder_name),
            keepMissing = ::keepMissingPackage,
        )
        // Keep the raw value before a repair can overwrite it. The backup is
        // one bounded key, so a corrupt order stays diagnosable after the
        // launcher has already rebuilt a usable grid.
        if (built.repairedIds || saved?.let(::invalidOrderJson) == true) {
            saved?.takeIf { it.length <= MAX_QUARANTINE_CHARS }?.let {
                prefs.edit().putString(KEY_QUARANTINE, it).apply()
            }
        }
        // Persist a generated folder id even when the saved order already
        // covers every app: otherwise the next read invents another UUID and
        // the Compose key and saved focus become unstable (AQ-28).
        if (built.repairedIds) saveOrder(built.items)
        return built.items
    }

    /**
     * The fallback order follows localized labels, so leaving it unsaved means a
     * language change re-sorts every tile. Freeze the first build (and the one
     * after a reset) into the same saved value the user edits later; the same
     * applies to an app installed later, which would otherwise be appended by
     * localized order until the next explicit save.
     */
    fun applyAndRememberOrder(apps: List<TvApp>): List<HomeItem> {
        val items = applyOrder(apps)
        if (!savedOrderCovers(savedOrder(), apps)) saveOrder(items)
        return items
    }

    /** The raw saved value, so Home can tell whether Settings changed it. */
    fun savedOrder(): String? = SafePreferences.string(
        prefs,
        LauncherPrefs.KEY_HOME_ORDER,
        null,
    )

    fun clearOrder() {
        prefs.edit().remove(LauncherPrefs.KEY_HOME_ORDER).apply()
    }

    /**
     * Deletes only the layout keys, not the whole file: profile p1 shares its
     * file with the language, saver and content settings.
     */
    fun clearLayout() {
        prefs.edit()
            .remove(LauncherPrefs.KEY_HOME_ORDER)
            .remove(LauncherPrefs.KEY_FOCUS_PACKAGE)
            .remove(LauncherPrefs.KEY_SHELF_PACKAGE)
            .apply()
    }

    fun saveOrder(items: List<HomeItem>) {
        val array = JSONArray()
        for (item in items) {
            when (item) {
                is HomeItem.AppItem -> array.put(item.app.packageName)
                is HomeItem.FolderItem -> array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("name", item.name)
                        .put("apps", JSONArray(item.apps.map { it.packageName })),
                )
            }
        }
        prefs.edit().putString(LauncherPrefs.KEY_HOME_ORDER, array.toString()).apply()
    }
}

/**
 * True when the saved order already lists every installed app, so rebuilding it
 * adds nothing. Missing entries, unreadable JSON and a blank value all mean the
 * current build is the fallback and has to be written down.
 */
private const val MAX_QUARANTINE_CHARS = 256 * 1024
private const val MAX_ORDER_CHARS = 512 * 1024
private const val MAX_ORDER_ENTRIES = 1_000
private const val MAX_FOLDER_APPS = 1_000
private const val MAX_TEXT_CHARS = 256
private const val KEY_QUARANTINE = "order_quarantine"

private fun invalidOrderJson(saved: String): Boolean =
    runCatching { JSONArray(saved) }.isFailure

internal fun savedOrderCovers(saved: String?, apps: List<TvApp>): Boolean {
    if (apps.isEmpty()) return saved != null
    if (saved == null || saved.length > MAX_ORDER_CHARS) return false
    val entries = try {
        JSONArray(saved)
    } catch (_: Exception) {
        return false
    }
    if (entries.length() == 0 || entries.length() > MAX_ORDER_ENTRIES) return false
    val covered = HashSet<String>(entries.length())
    for (index in 0 until minOf(entries.length(), MAX_ORDER_ENTRIES)) {
        val entry = entries.opt(index)
        if (entry is String) {
            if (entry.length <= MAX_TEXT_CHARS) covered += entry
        } else if (entry is JSONObject) {
            val packages = entry.optJSONArray("apps") ?: continue
            for (packageIndex in 0 until minOf(packages.length(), MAX_FOLDER_APPS)) {
                val packageName = packages.optString(packageIndex)
                if (packageName.length <= MAX_TEXT_CHARS) covered += packageName
            }
        }
    }
    return apps.all { it.packageName in covered }
}

/**
 * 04_APP_LIBRARY §5: an installed app that is no longer present disappears from the
 * saved order, and a folder left without children is dropped. Newly installed apps
 * are appended so they are never lost.
 */
internal data class BuiltHomeOrder(
    val items: List<HomeItem>,
    val repairedIds: Boolean,
)

internal fun buildHomeOrder(
    saved: String?,
    apps: List<TvApp>,
    defaultFolderName: String,
    hiddenFolderName: String? = null,
): List<HomeItem> = buildHomeOrderRepair(saved, apps, defaultFolderName, hiddenFolderName).items

/** A saved package that is not installed now; its position is still the user's. */
internal fun missingApp(packageName: String): TvApp = TvApp(
    packageName = packageName,
    activityName = "",
    label = packageName,
    banner = null,
    icon = null,
    available = false,
)

internal fun buildHomeOrderRepair(
    saved: String?,
    apps: List<TvApp>,
    defaultFolderName: String,
    hiddenFolderName: String? = null,
    keepMissing: (String) -> Boolean = { true },
): BuiltHomeOrder {
    val fallback = apps.map(HomeItem::AppItem)
    if (saved == null) return BuiltHomeOrder(fallback, repairedIds = false)
    if (saved.length > MAX_ORDER_CHARS) {
        return BuiltHomeOrder(fallback, repairedIds = true)
    }
    val entries = try {
        JSONArray(saved)
    } catch (_: Exception) {
        return BuiltHomeOrder(fallback, repairedIds = false)
    }
    if (entries.length() == 0) return BuiltHomeOrder(fallback, repairedIds = false)

    val byPackage = apps.associateBy { it.packageName }
    val entryLimit = minOf(entries.length(), MAX_ORDER_ENTRIES)
    val used = HashSet<String>(entryLimit)
    val usedIds = HashSet<String>()
    val result = ArrayList<HomeItem>(entryLimit)
    var repairedIds = entries.length() > MAX_ORDER_ENTRIES
    for (index in 0 until entryLimit) {
        val entry = entries.opt(index)
        if (entry is String) {
            if (entry.length > MAX_TEXT_CHARS) continue
            val app = byPackage[entry] ?: entry.takeIf(keepMissing)?.let(::missingApp)
            if (app != null && used.add(entry)) result += HomeItem.AppItem(app)
        } else if (entry is JSONObject) {
            val folderApps = ArrayList<TvApp>()
            val packages = entry.optJSONArray("apps") ?: JSONArray()
            for (packageIndex in 0 until minOf(packages.length(), MAX_FOLDER_APPS)) {
                val packageName = packages.optString(packageIndex)
                if (packageName.length > MAX_TEXT_CHARS) continue
                val app = byPackage[packageName] ?: packageName.takeIf(keepMissing)?.let(::missingApp)
                if (app != null && used.add(packageName)) folderApps += app
            }
            if (folderApps.isEmpty()) continue
            // Duplicate ids would collide with the key Compose uses for each cell.
            var id = entry.optString("id").takeIf { it.length <= MAX_TEXT_CHARS }.orEmpty()
            if (id.isEmpty() || !usedIds.add(id)) {
                id = UUID.randomUUID().toString()
                usedIds.add(id)
                repairedIds = true
            }
            val storedName = entry.optString("name")
                .takeIf { it.isNotBlank() && it.length <= MAX_TEXT_CHARS }
            // The hidden folder is a system bucket with a fixed caption, not a
            // user choice: its stored name would otherwise stay in the language
            // that was active when the first app was hidden.
            val name = if (id == HIDDEN_FOLDER_ID && hiddenFolderName != null) {
                hiddenFolderName
            } else {
                storedName ?: defaultFolderName
            }
            result += HomeItem.FolderItem(id, name, folderApps)
        }
    }
    val additions = apps.filter { it.packageName !in used }.map(HomeItem::AppItem)
    if (additions.isNotEmpty()) {
        // 04_APP_LIBRARY §5: new apps belong in the grid, not behind the system
        // bucket that is pinned to the end of the user's order.
        val hiddenIndex = result.indexOfFirst {
            it is HomeItem.FolderItem && it.id == HIDDEN_FOLDER_ID
        }
        if (hiddenIndex >= 0) result.addAll(hiddenIndex, additions) else result.addAll(additions)
    }
    return BuiltHomeOrder(result, repairedIds)
}
