package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import com.whatuneed.ovisiontv.kotlin.R
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

    fun applyOrder(apps: List<TvApp>): List<HomeItem> =
        buildHomeOrder(
            saved = prefs.getString(LauncherPrefs.KEY_HOME_ORDER, null),
            apps = apps,
            defaultFolderName = context.getString(R.string.folder_default),
            hiddenFolderName = context.getString(R.string.hidden_folder_name),
        )

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
    fun savedOrder(): String? = prefs.getString(LauncherPrefs.KEY_HOME_ORDER, null)

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
internal fun savedOrderCovers(saved: String?, apps: List<TvApp>): Boolean {
    if (apps.isEmpty()) return saved != null
    if (saved == null) return false
    val entries = try {
        JSONArray(saved)
    } catch (_: Exception) {
        return false
    }
    if (entries.length() == 0) return false
    val covered = HashSet<String>(entries.length())
    for (index in 0 until entries.length()) {
        val entry = entries.opt(index)
        if (entry is String) {
            covered += entry
        } else if (entry is JSONObject) {
            val packages = entry.optJSONArray("apps") ?: continue
            for (packageIndex in 0 until packages.length()) {
                covered += packages.optString(packageIndex)
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
internal fun buildHomeOrder(
    saved: String?,
    apps: List<TvApp>,
    defaultFolderName: String,
    hiddenFolderName: String? = null,
): List<HomeItem> {
    val fallback = apps.map(HomeItem::AppItem)
    if (saved == null) return fallback
    val entries = try {
        JSONArray(saved)
    } catch (_: Exception) {
        return fallback
    }
    if (entries.length() == 0) return fallback

    val byPackage = apps.associateBy { it.packageName }
    val used = HashSet<String>(entries.length())
    val usedIds = HashSet<String>()
    val result = ArrayList<HomeItem>(entries.length())
    for (index in 0 until entries.length()) {
        val entry = entries.opt(index)
        if (entry is String) {
            val app = byPackage[entry] ?: continue
            if (used.add(entry)) result += HomeItem.AppItem(app)
        } else if (entry is JSONObject) {
            val folderApps = ArrayList<TvApp>()
            val packages = entry.optJSONArray("apps") ?: JSONArray()
            for (packageIndex in 0 until packages.length()) {
                val packageName = packages.optString(packageIndex)
                val app = byPackage[packageName] ?: continue
                if (used.add(packageName)) folderApps += app
            }
            if (folderApps.isEmpty()) continue
            // Duplicate ids would collide with the key Compose uses for each cell.
            var id = entry.optString("id")
            if (id.isEmpty() || !usedIds.add(id)) {
                id = UUID.randomUUID().toString()
                usedIds.add(id)
            }
            val storedName = entry.optString("name").takeIf { it.isNotBlank() }
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
    return result
}
