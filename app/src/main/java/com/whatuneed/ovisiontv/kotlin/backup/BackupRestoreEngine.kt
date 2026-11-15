package com.whatuneed.ovisiontv.kotlin.backup

import android.content.Context
import android.content.SharedPreferences
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.Profile
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SafePreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverSettings
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.data.SetupProgress
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.data.serializeProfiles
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryKeys
import com.whatuneed.ovisiontv.kotlin.discovery.encodeItems
import com.whatuneed.ovisiontv.kotlin.discovery.encodeShown
import com.whatuneed.ovisiontv.kotlin.discovery.encodeUsage

internal data class RestoreOutcome(
    val warnings: List<BackupWarning>,
    val restartRequired: Boolean = true,
)

/**
 * Applies validated data to the existing stores. One SharedPreferences.Editor
 * per file, one commit per editor. SharedPreferences is atomic only per file;
 * the pre-restore copy and journal in BackupManager cover the gap between files.
 */
internal class BackupRestoreEngine(private val context: Context) {

    fun apply(
        validated: ValidatedBackup,
        selection: BackupSelection,
    ): RestoreOutcome {
        val data = validated.data
        val warnings = LinkedHashSet<BackupWarning>()
        val profileStore = ProfileStore(context)
        val currentProfiles = profileStore.list()
        val currentById = currentProfiles.associateBy { it.id }
        val kidsStore = KidsStore(context)

        val targetProfiles: List<Profile>
        val targetCurrent: String
        val targetNextId: Int
        val askOnBoot: Boolean
        val askOnWake: Boolean

        if (selection.profiles && data.profiles != null) {
            val restored = data.profiles.profiles.map {
                Profile(
                    id = it.id,
                    name = it.name,
                    emoji = it.emoji,
                    color = it.color,
                    // A kids flag without a PIN is not a lock. Never create
                    // that state from an archive that deliberately excludes
                    // credential hashes.
                    kids = it.kids && kidsStore.hasPin(it.id).also { hasPin ->
                        if (it.kids && !hasPin) warnings += BackupWarning.KIDS_PIN_RESET
                    },
                )
            }
            val restoredById = restored.associateBy { it.id }
            val merged = ArrayList<Profile>(currentProfiles.size + restored.size)
            currentProfiles.forEach { existing ->
                val replacement = restoredById[existing.id]
                if (replacement == null) {
                    merged += existing
                } else {
                    merged += replacement
                }
            }
            restored.forEach { profile ->
                if (currentById[profile.id] == null) merged += profile
            }
            if (merged.size > ProfileCatalog.MAX) {
                throw BackupException(
                    BackupStage.RESTORE,
                    "restore would exceed ${ProfileCatalog.MAX} profiles",
                )
            }
            targetProfiles = merged
            val restoredCurrent = data.profiles.currentId
            targetCurrent = restoredCurrent.takeIf { id -> merged.any { it.id == id } }
                ?: profileStore.current().id
            targetNextId = maxOf(
                profileStore.nextIdFloor(),
                data.profiles.nextId,
                merged.maxOfOrNull { it.id.removePrefix("p").toIntOrNull() ?: 0 }?.plus(1) ?: 0,
            )
            askOnBoot = data.profiles.askOnBoot
            askOnWake = data.profiles.askOnWake
        } else {
            targetProfiles = currentProfiles
            targetCurrent = profileStore.current().id
            targetNextId = profileStore.nextIdFloor()
            askOnBoot = profileStore.askOnBoot()
            askOnWake = profileStore.askOnWake()
        }

        val known = targetProfiles.mapTo(HashSet()) { it.id }
        val editors = LinkedHashMap<String, SharedPreferences.Editor>()
        fun editor(name: String): SharedPreferences.Editor = editors.getOrPut(name) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        }

        if (selection.home) {
            data.homes.forEach { (profileId, home) ->
                if (profileId !in known) {
                    warnings += BackupWarning.REPAIRED
                    return@forEach
                }
                val editor = editor(ProfileStore.homePrefsName(profileId))
                editor.putString(
                    LauncherPrefs.KEY_HOME_ORDER,
                    BackupJson.encodeHomeOrder(home),
                )
                editor.remove(LauncherPrefs.KEY_FOCUS_PACKAGE)
                editor.remove(LauncherPrefs.KEY_SHELF_PACKAGE)
            }
        }

        if (selection.discovery) {
            data.discovery.forEach { (profileId, discovery) ->
                if (profileId !in known) {
                    warnings += BackupWarning.REPAIRED
                    return@forEach
                }
                val editor = editor(ProfileStore.homePrefsName(profileId))
                editor.putBoolean(DiscoveryKeys.RECOMMENDATIONS, discovery.recommendations)
                editor.putBoolean(DiscoveryKeys.CONTINUE, discovery.continueWatching)
                editor.putBoolean(DiscoveryKeys.PERSONALIZATION, discovery.personalization)
                editor.putString(DiscoveryKeys.FAVORITES, encodeItems(discovery.favorites))
                editor.putString(DiscoveryKeys.HIDDEN, encodeItems(discovery.hidden))
                editor.putString(DiscoveryKeys.USAGE, encodeUsage(discovery.usage))
                editor.putString(DiscoveryKeys.SHOWN, encodeShown(discovery.shown))
            }
        }

        if (selection.kids) {
            data.kids.forEach { (profileId, kids) ->
                if (profileId !in known) {
                    warnings += BackupWarning.REPAIRED
                    return@forEach
                }
                editor(KidsStore.FILE).putStringSet(
                    KidsStore.KEY_ALLOW + profileId,
                    HashSet(kids.allowlist),
                )
            }
        }

        if (selection.weather) {
            data.weather?.let { weather ->
                val prefs = WeatherPreferences.prefs(context)
                val editor = editor(WeatherPreferences.NAME)
                editor.putBoolean(WeatherPreferences.K_ENABLED, weather.enabled)
                editor.putBoolean(WeatherPreferences.K_MANUAL, weather.manual)
                editor.putString(WeatherPreferences.K_PLACE, weather.place)
                if (weather.manual) {
                    editor.putLong(WeatherPreferences.K_LAT, weather.latitude.toRawBits())
                    editor.putLong(WeatherPreferences.K_LON, weather.longitude.toRawBits())
                } else {
                    editor.remove(WeatherPreferences.K_LAT)
                    editor.remove(WeatherPreferences.K_LON)
                }
                editor.putString(WeatherPreferences.K_UNITS, weather.units)
                editor.putString(WeatherPreferences.K_SOURCE, weather.source)
                editor.putInt(WeatherPreferences.K_REFRESH, weather.refreshMinutes)
                // The cache belongs to the old place and must never be shown
                // under the restored one. Bump the generation so a fetch that
                // started before the restore cannot write back.
                editor.remove(WeatherPreferences.K_CACHE)
                editor.remove(WeatherPreferences.K_CACHE_AT)
                editor.remove(WeatherPreferences.K_CACHE_KEY)
                editor.putInt(
                    WeatherPreferences.K_GENERATION,
                    SafePreferences.int(prefs, WeatherPreferences.K_GENERATION, 0) + 1,
                )
            }
        }

        if (selection.saver) {
            data.saver?.let { saver ->
                val themes = saver.themes.mapNotNull { key ->
                    SaverTheme.entries.firstOrNull { it.key == key }
                }.toSet()
                val editor = editor(LauncherPrefs.NAME)
                editor.putInt(SaverPreferences.KEY_DELAY_MINUTES, saver.delayMinutes)
                editor.putBoolean(SaverPreferences.KEY_MUSIC, saver.music)
                editor.putString(SaverPreferences.KEY_REFRESH, saver.refresh)
                editor.putString(SaverPreferences.KEY_THEMES, SaverSettings.encodeThemes(themes))
                editor.putString(SaverPreferences.KEY_HIDDEN_CLIPS, SaverSettings.encodeHidden(saver.hidden))
            }
        }

        if (selection.language) {
            data.language?.let { language ->
                val editor = editor(LauncherPrefs.NAME)
                editor.putString(LocalePreference.KEY, language.language)
                editor.putBoolean(LauncherPrefs.KEY_SHOW_PHONE_APPS, language.showPhoneApps)
            }
        }

        if (selection.content) {
            data.content?.let { content ->
                val editor = editor(LauncherPrefs.NAME)
                editor.putBoolean(ContentPreferences.KEY_RECOMMENDATIONS, content.recommendations)
                editor.putBoolean(ContentPreferences.KEY_CONTINUE_WATCHING, content.continueWatching)
            }
        }

        if (selection.startup) {
            data.startup?.let { startup ->
                val editor = editor(StartupPreferences.NAME)
                editor.putString(StartupPreferences.KEY_TARGET, startup.target)
                editor.putString(StartupPreferences.KEY_LABEL, startup.label)
                editor.putBoolean(StartupPreferences.KEY_ON_WAKE, startup.onWake)
                // home_override is a system permission attempt, never data.
            }
        }

        if (selection.profiles && data.profiles != null) {
            val editor = editor(ProfileStore.FILE)
            editor.putString(ProfileStore.KEY_LIST, serializeProfiles(targetProfiles))
            editor.putString(ProfileStore.KEY_CURRENT, targetCurrent)
            editor.putInt(ProfileStore.KEY_NEXT_ID, targetNextId)
            editor.putBoolean(ProfileStore.KEY_ASK_ON_BOOT, askOnBoot)
            editor.putBoolean(ProfileStore.KEY_ASK_ON_WAKE, askOnWake)
        }

        // Commit the per-profile files before the profile catalog: the active
        // profile must not switch to a layout that has not been written yet.
        val profileFile = ProfileStore.FILE
        val ordered = editors.entries.sortedBy { if (it.key == profileFile) 1 else 0 }
        ordered.forEach { (name, editor) ->
            if (!editor.commit()) {
                throw BackupException(BackupStage.RESTORE, "could not write ${name}")
            }
        }

        if (selection.profiles && data.profiles != null) {
            val stored = SafePreferences.string(
                context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE),
                ProfileStore.KEY_LIST,
                null,
            )
            if (stored.isNullOrBlank()) {
                throw BackupException(BackupStage.RESTORE, "profile catalog was not written")
            }
        }

        SetupProgress.markDone(context)
        return RestoreOutcome(warnings.toList())
    }
}
