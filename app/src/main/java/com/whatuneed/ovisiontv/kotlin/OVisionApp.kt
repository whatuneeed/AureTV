package com.whatuneed.ovisiontv.kotlin

import android.app.Application
import android.content.ComponentCallbacks2
import com.whatuneed.ovisiontv.kotlin.backup.BackupMaintenance
import com.whatuneed.ovisiontv.kotlin.backup.BackupRecovery
import com.whatuneed.ovisiontv.kotlin.backup.BackupScheduler
import com.whatuneed.ovisiontv.kotlin.data.CrashReport
import com.whatuneed.ovisiontv.kotlin.ui.MemoryPressure

/**
 * Application-wide callbacks keep diagnostics and cache trimming working on
 * every entry point, not only while the Home activity is alive. Nothing here
 * restarts the launcher or swallows a failure.
 */
class OVisionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReport.install(this)
        // A journal means the previous process died between two restore
        // commits; undo it before Home reads a half-restored profile file.
        BackupRecovery.recoverIfNeeded(this)
        BackupScheduler.ensureScheduled(this)
        BackupMaintenance.start(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        MemoryPressure.trim(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        MemoryPressure.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}
