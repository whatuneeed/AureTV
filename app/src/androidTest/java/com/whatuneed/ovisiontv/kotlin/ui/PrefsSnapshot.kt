package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import com.whatuneed.ovisiontv.kotlin.data.KidsStore
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SetupProgress
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences

/**
 * Connected tests clear the launcher's focus and order memory for deterministic
 * runs. That is the real prefs file of the installed app, so each test class
 * snapshots it and puts the user's values back afterwards.
 *
 * The wizard keeps its own file: it must read as done, or MainActivity opens
 * SetupActivity over the test. Its snapshot follows the same rule.
 */
internal object PrefsSnapshot {

    fun capture(context: Context): Map<String, Any?> = read(context, LauncherPrefs.NAME)

    fun restore(context: Context, values: Map<String, Any?>) =
        write(context, LauncherPrefs.NAME, values)

    fun captureSetup(context: Context): Map<String, Any?> = read(context, SetupProgress.NAME)

    fun restoreSetup(context: Context, values: Map<String, Any?>) =
        write(context, SetupProgress.NAME, values)

    fun clearSetup(context: Context) =
        context.getSharedPreferences(SetupProgress.NAME, Context.MODE_PRIVATE).edit().clear().commit()

    fun captureStartup(context: Context): Map<String, Any?> = read(context, StartupPreferences.NAME)

    fun restoreStartup(context: Context, values: Map<String, Any?>) =
        write(context, StartupPreferences.NAME, values)

    fun captureProfiles(context: Context): Map<String, Any?> = read(context, ProfileStore.FILE)

    fun restoreProfiles(context: Context, values: Map<String, Any?>) =
        write(context, ProfileStore.FILE, values)

    /** PIN hashes, allowlists and lockouts live in their own file. */
    fun captureKids(context: Context): Map<String, Any?> = read(context, KidsStore.FILE)

    fun restoreKids(context: Context, values: Map<String, Any?>) =
        write(context, KidsStore.FILE, values)

    fun markSetupDone(context: Context) = SetupProgress.markDone(context)

    private fun read(context: Context, name: String): Map<String, Any?> =
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all

    private fun write(context: Context, name: String, values: Map<String, Any?>) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            .edit()
            .clear()
        for ((key, value) in values) {
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }
}
