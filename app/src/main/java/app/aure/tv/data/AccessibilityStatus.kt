package app.aure.tv.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import app.aure.tv.OVisionAccessibilityService
import app.aure.tv.R

/**
 * State of OVisionTV's accessibility switch. [STALE] is the case Android
 * creates after a force-stop: the switch stays listed as enabled but the
 * service is not connected, so the user has to turn it off and on again.
 */
object AccessibilityStatus {
    enum class State { CONNECTED, STALE, OFF }

    fun state(context: Context): State = state(
        connected = OVisionAccessibilityService.connected,
        enabledInSettings = isEnabledInSettings(context),
    )

    fun state(connected: Boolean, enabledInSettings: Boolean): State = when {
        connected -> State.CONNECTED
        enabledInSettings -> State.STALE
        else -> State.OFF
    }

    /** The system list is colon-separated; the stored form is package/class. */
    fun isEnabledInSettings(context: Context): Boolean {
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull() ?: return false
        val component = ComponentName(context, OVisionAccessibilityService::class.java)
        val full = component.flattenToString()
        val short = component.flattenToShortString()
        val listed = enabled.split(':').any {
            it.equals(full, ignoreCase = true) || it.equals(short, ignoreCase = true)
        }
        if (!listed) return false
        // A listed service is not connected while accessibility is globally off.
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
    }

    /** False when the TV maps the screen to an empty stub or has none at all. */
    fun available(context: Context): Boolean = runCatching {
        val component = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .resolveActivity(context.packageManager) ?: return false
        !component.className.contains("Stub")
    }.getOrDefault(false)

    /** A vendor TV may hide the exact page; Android Settings is the way out. */
    fun open(context: Context) {
        val opened = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess || runCatching {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!opened) {
            Toast.makeText(context, R.string.settings_system_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
