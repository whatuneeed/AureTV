package app.aure.tv.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import app.aure.tv.OVisionAccessibilityService
import app.aure.tv.R
import app.aure.tv.SleepReceiver

/**
 * Standby and the Sleep Timer, as in the Java build. Device admin is the
 * primary route (force-lock only); when the TV has no device-admin screen the
 * accessibility switch's lock action stands in. A stock launcher cannot power
 * the panel off with a documented API, so the Control Center action is named
 * "Sleep" and ends in standby, not a hidden PowerManager call.
 */
object SleepControls {
    const val PREFS = "controls"
    private const val KEY_SLEEP_AT = "sleep_at"
    private const val REQUEST_CODE = 0

    /** Off → 15 → 30 → 60 → 90 → Off. */
    val STEPS = intArrayOf(15, 30, 60, 90)

    fun admin(context: Context): ComponentName =
        ComponentName(context.applicationContext, SleepReceiver.Admin::class.java)

    /** An active admin or a connected accessibility switch can put the TV to sleep. */
    fun hasAdmin(context: Context): Boolean =
        runCatching {
            context.getSystemService(DevicePolicyManager::class.java)
                ?.isAdminActive(admin(context)) == true
        }.getOrDefault(false) || OVisionAccessibilityService.connected

    /** Same rule as Java's Perms.adminSupported: either route needs a real screen. */
    fun adminSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN) ||
            AccessibilityStatus.available(context)

    fun sleepNow(context: Context): Boolean {
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        if (policy != null && runCatching { policy.isAdminActive(admin(context)) }.getOrDefault(false)) {
            return runCatching { policy.lockNow() }.isSuccess
        }
        return OVisionAccessibilityService.sleep()
    }

    /** Documented routes only: device-admin lock or the accessibility lock action. */
    fun powerOff(context: Context): Boolean = sleepNow(context)

    fun timerLeft(context: Context): Int =
        timerLeft(at = sleepAt(context), now = System.currentTimeMillis())

    /** Whole minutes still on the timer; 0 when it is off or already due. */
    fun timerLeft(at: Long, now: Long): Int {
        val left = at - now
        return if (left > 0) Math.ceil(left / 60_000.0).toInt() else 0
    }

    fun nextTimer(left: Int): Int {
        if (left <= 0) return STEPS.first()
        for (step in STEPS) if (step > left + 1) return step
        return 0
    }

    fun setTimer(context: Context, minutes: Int) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java)
        alarm?.cancel(pending(app))
        val at = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SLEEP_AT, at).apply()
        if (minutes > 0 && alarm != null) {
            runCatching { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(app)) }
        }
    }

    fun cancelTimer(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SLEEP_AT, 0L).apply()
        app.getSystemService(AlarmManager::class.java)?.cancel(pending(app))
    }

    private fun sleepAt(context: Context): Long =
        SafePreferences.long(
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
            KEY_SLEEP_AT,
            0L,
        )

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        REQUEST_CODE,
        Intent(context.applicationContext, SleepReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The screen that grants the sleep permission, or null when the TV offers
     * neither route. The Control Center sends it through the kids gate; the
     * system's own device-admin screen explains the request, and when the TV
     * lacks it the accessibility switch is the only route to standby.
     */
    fun permissionIntent(context: Context): Intent? {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(R.string.settings_power_admin_explanation),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (AccessibilityStatus.available(context)) {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return null
    }

    fun requestPermission(context: Context) {
        if (hasAdmin(context)) return
        val intent = permissionIntent(context)
        if (intent != null && intent.action == DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN &&
            runCatching { context.startActivity(intent) }.isSuccess
        ) {
            return
        }
        if (AccessibilityStatus.available(context)) {
            Toast.makeText(context, R.string.settings_power_accessibility_hint, Toast.LENGTH_LONG)
                .show()
            AccessibilityStatus.open(context)
            return
        }
        Toast.makeText(context, R.string.settings_power_unavailable, Toast.LENGTH_SHORT).show()
    }
}
