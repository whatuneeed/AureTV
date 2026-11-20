package app.aure.tv

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.aure.tv.backup.BackupRecovery
import app.aure.tv.data.SleepControls

/**
 * Fires when the Control Center sleep timer runs out. The alarm survives the
 * launcher being closed; the admin is only a force-lock (see [Admin]).
 */
class SleepReceiver : BroadcastReceiver() {

    /** Device-admin component: force-lock only, nothing else is requested. */
    class Admin : DeviceAdminReceiver()

    override fun onReceive(context: Context, intent: Intent?) {
        // Do not write a timer preference while a restore rollback owns the
        // preference files.
        if (!BackupRecovery.isReady.value) return
        SleepControls.cancelTimer(context)
        SleepControls.sleepNow(context)
    }
}
