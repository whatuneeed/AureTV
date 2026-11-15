package com.whatuneed.ovisiontv.kotlin.backup

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Called from Application.onCreate before any screen reads preferences. If a
 * restore died between two file commits, the journal forces the pre-restore
 * copy back. Starting the process with old data is safer than showing a
 * half-applied profile catalogue.
 */
internal object BackupRecovery {
    private const val TAG = "BackupRecovery"

    fun recoverIfNeeded(context: Context) {
        val state = RestoreJournal(context.applicationContext).read() ?: return
        if (state.state == "applied" || state.state == "rolled_back") {
            RestoreJournal(context.applicationContext).clear()
            return
        }
        runCatching {
            runBlocking(Dispatchers.IO) {
                BackupManager(context.applicationContext).recoverIfNeeded()
            }
        }.onFailure {
            runCatching { Log.w(TAG, "interrupted restore could not be recovered", it) }
        }
    }
}
