package com.whatuneed.ovisiontv.kotlin.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs one automatic local copy. The copy is local and small; goAsync keeps
 * the process alive while the archive is written, and the alarm is scheduled
 * again either way so one failure does not stop future copies.
 */
class BackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Do not archive a profile catalogue while a rollback is
                // still replacing it.
                BackupRecovery.await()
                BackupManager(appContext).createLocal(trigger = BackupTrigger.AUTO)
            } catch (_: Exception) {
                // The status remains visible in Settings; no fake success.
            } finally {
                BackupScheduler.ensureScheduled(appContext)
                pending.finish()
            }
        }
    }
}
