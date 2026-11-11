package com.whatuneed.ovisiontv.kotlin.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.whatuneed.ovisiontv.kotlin.OVisionAccessibilityService
import com.whatuneed.ovisiontv.kotlin.R

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
        return enabled.split(':').any {
            it.equals(component.flattenToString(), ignoreCase = true) ||
                it.startsWith(context.packageName + "/")
        }
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
