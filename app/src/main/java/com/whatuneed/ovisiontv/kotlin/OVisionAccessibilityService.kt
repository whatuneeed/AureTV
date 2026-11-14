package com.whatuneed.ovisiontv.kotlin

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.whatuneed.ovisiontv.kotlin.data.StartupPreferences

/**
 * OVisionTV's accessibility switch. Stage B uses it for two real jobs:
 * - Home button: when the TV's own launcher appears while OVisionTV is not the
 *   Home app, open OVisionTV instead. Only the package and class of the window
 *   are looked at; nothing on screen is read.
 * - Start with the TV: an accessibility service may open a screen from the
 *   background, so the chosen app can open after boot and on wake.
 */
class OVisionAccessibilityService : AccessibilityService() {

    private var screenOn: BroadcastReceiver? = null
    private var lastRedirect = 0L
    @Volatile
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Only a Home/stock-launcher wake may start the chosen app;
                // waking another app must not have the screen stolen from it.
                if (isHomeSurface()) {
                    StartupPreferences.onScreenOn(this@OVisionAccessibilityService)
                }
            }
        }
        screenOn = receiver
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        // Bound early after boot, and allowed to open screens: a reliable boot trigger too.
        if (StartupPreferences.justBooted()) StartupPreferences.onBoot(this)
    }

    /** The last reported window belongs to Home or to a stock launcher. */
    private fun isHomeSurface(): Boolean {
        val packageName = lastPackage ?: return false
        return packageName == this.packageName ||
            stockHomes(this).any { it.startsWith("$packageName/") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        lastPackage = packageName
        val className = event.className?.toString() ?: return
        if (!StartupPreferences.homeOverrideWanted(this)) return
        if ("$packageName/$className" !in stockHomes(this)) return
        // The stock launcher may report its window twice.
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 800) return
        redirect()
    }

    private fun redirect() {
        lastRedirect = SystemClock.elapsedRealtime()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        screenOn?.let { runCatching { unregisterReceiver(it) } }
        screenOn = null
        instance = null
        lastPackage = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: OVisionAccessibilityService? = null
            private set

        val connected: Boolean get() = instance != null

        /** Standby fallback for TVs whose settings app has no device-admin screen. */
        fun sleep(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        /**
         * Other launchers' Home activities as "package/class". Android's
         * boot-time FallbackHome is left alone, and activity aliases are added
         * by their target class because window events report that one.
         */
        @Synchronized
        fun stockHomes(context: Context): Set<String> {
            val now = SystemClock.elapsedRealtime()
            val cached = homes
            if (cached != null && now - homesAt < 60_000) return cached

            val found = HashSet<String>()
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            runCatching {
                context.packageManager.queryIntentActivities(intent, 0)
            }.getOrDefault(emptyList()).forEach { info ->
                val activity = info.activityInfo ?: return@forEach
                val packageName = activity.packageName ?: return@forEach
                if (packageName == context.packageName) return@forEach
                val name = activity.name ?: return@forEach
                if (name.endsWith("FallbackHome")) return@forEach
                found.add("$packageName/$name")
                activity.targetActivity?.let { found.add("$packageName/$it") }
            }
            homes = found
            homesAt = now
            return found
        }

        private var homes: Set<String>? = null
        private var homesAt = 0L
    }
}
