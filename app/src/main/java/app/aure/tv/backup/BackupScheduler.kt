package app.aure.tv.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aure.tv.data.SafePreferences

/**
 * A lightweight periodic local copy. AlarmManager is deliberate here: the
 * launcher already runs at boot, so it does not need a boot receiver or the
 * WAKE_LOCK/RECEIVE_BOOT_COMPLETED permissions WorkManager would merge in.
 * The alarm is inexact; the system may defer it, and the UI never promises a
 * precise time.
 */
object BackupScheduler {
    private const val NAME = "backup_state"
    private const val KEY_AUTO = "auto"
    private const val REQUEST_CODE = 7_301
    private const val ACTION = "app.aure.tv.action.BACKUP"
    const val INTERVAL_MS = AlarmManager.INTERVAL_DAY

    fun isAutoEnabled(context: Context): Boolean =
        SafePreferences.boolean(
            context.getSharedPreferences(NAME, Context.MODE_PRIVATE),
            KEY_AUTO,
            true,
        )

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO, enabled)
            .apply()
        if (enabled) schedule(context) else cancel(context)
    }

    /** Called at app start; an alarm does not survive a reboot, but Home does. */
    fun ensureScheduled(context: Context) {
        if (isAutoEnabled(context)) schedule(context)
    }

    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val trigger = System.currentTimeMillis() + INTERVAL_MS
        runCatching {
            alarm.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger,
                INTERVAL_MS,
                pending(app),
            )
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(pending(app))
    }

    internal fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE,
        Intent(context.applicationContext, BackupAlarmReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
