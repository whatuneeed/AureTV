package app.aure.tv

import android.app.Application
import android.content.ComponentCallbacks2
import app.aure.tv.backup.BackupMaintenance
import app.aure.tv.backup.BackupRecovery
import app.aure.tv.data.CrashReport
import app.aure.tv.ui.MemoryPressure

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
        // commits. Start the rollback here; activities/services await its
        // completion before reading prefs.
        BackupRecovery.start(this)
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
