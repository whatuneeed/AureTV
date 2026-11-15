package com.whatuneed.ovisiontv.kotlin.backup

import android.content.Context
import com.whatuneed.ovisiontv.kotlin.BuildConfig
import com.whatuneed.ovisiontv.kotlin.data.SafePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Live-data schema baseline. There is no older on-disk backup schema yet, so
 * 0 -> 1 is deliberately a no-op for stores; its only job is to leave a
 * pinned pre-migration copy and record that the data has been seen by the
 * current schema. Future migrations extend the same gate instead of guessing
 * from a missing key.
 */
internal data class LiveMigrationResult(
    val schema: Int,
    val preUpdate: BackupIndexEntry? = null,
    val preMigration: BackupIndexEntry? = null,
)

internal class MigrationManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    fun schema(): Int = SafePreferences.int(prefs, KEY_SCHEMA, 0)

    fun lastVersionCode(): Int = SafePreferences.int(prefs, KEY_LAST_VERSION, 0)

    suspend fun ensureCurrent(): LiveMigrationResult {
        val current = schema()
        if (current > BackupContract.DATA_SCHEMA) {
            throw BackupException(
                BackupStage.MIGRATION,
                "stored data schema $current is newer than this app",
            )
        }

        var preUpdate: BackupIndexEntry? = null
        val lastVersion = lastVersionCode()
        if (lastVersion != 0 && lastVersion < BuildConfig.VERSION_CODE) {
            preUpdate = BackupManager(appContext).createLocal(
                selection = BackupSelection(),
                trigger = BackupTrigger.PRE_UPDATE,
            )
            // Record the version only after the copy exists. A failed copy is
            // retried on the next launch, not silently marked as done.
            if (!prefs.edit().putInt(KEY_LAST_VERSION, BuildConfig.VERSION_CODE).commit()) {
                throw BackupException(BackupStage.MIGRATION, "could not record the app version")
            }
        }

        var preMigration: BackupIndexEntry? = null
        if (current < BackupContract.DATA_SCHEMA) {
            preMigration = BackupManager(appContext).createLocal(
                selection = BackupSelection(),
                trigger = BackupTrigger.PRE_MIGRATION,
            )
            val editor = prefs.edit()
                .putInt(KEY_SCHEMA, BackupContract.DATA_SCHEMA)
                .putInt(KEY_LAST_VERSION, BuildConfig.VERSION_CODE)
            if (!editor.commit()) {
                throw BackupException(BackupStage.MIGRATION, "could not record the data schema")
            }
        } else if (lastVersion == 0 && !prefs.edit().putInt(KEY_LAST_VERSION, BuildConfig.VERSION_CODE).commit()) {
            throw BackupException(BackupStage.MIGRATION, "could not record the app version")
        }

        return LiveMigrationResult(
            schema = schema(),
            preUpdate = preUpdate,
            preMigration = preMigration,
        )
    }

    internal companion object {
        const val STATE_PREFS = "backup_state"
        const val KEY_SCHEMA = "data_schema"
        const val KEY_LAST_VERSION = "last_version_code"
    }
}

/** Runs the gate off the main thread; a failure leaves live data untouched. */
internal object BackupMaintenance {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { MigrationManager(appContext).ensureCurrent() }
                .onFailure {
                    runCatching {
                        android.util.Log.w("BackupMaintenance", "backup migration failed", it)
                    }
                }
        }
    }
}
