package com.whatuneed.ovisiontv.kotlin.backup

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Called from Application.onCreate before any screen reads preferences. If a
 * restore died between two file commits, the journal forces the pre-restore
 * copy back. The common case has no journal and stays fully synchronous; only
 * an actual rollback is moved to IO so a slow archive copy cannot block the
 * first frame.
 *
 * A screen must call [await] before reading preferences when the process may
 * be recovering; [isReady] is also exposed for a cheap check in services.
 */
internal object BackupRecovery {
    private const val TAG = "BackupRecovery"

    private val ready = MutableStateFlow(true)
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isReady: StateFlow<Boolean> = ready.asStateFlow()

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val journal = RestoreJournal(appContext)
        val state = journal.read()
        if (state == null) return
        if (state.state == "applied" || state.state == "rolled_back") {
            runCatching { journal.clear() }
            return
        }

        ready.value = false
        scope.launch {
            runCatching {
                BackupManager(appContext).recoverIfNeeded()
            }.onFailure {
                // Match the old synchronous path: keep the journal and let the
                // next launch retry, but never trap the launcher on a black
                // screen forever.
                runCatching { Log.w(TAG, "interrupted restore could not be recovered", it) }
            }
            ready.value = true
        }
    }

    suspend fun await() {
        isReady.first { it }
    }
}
