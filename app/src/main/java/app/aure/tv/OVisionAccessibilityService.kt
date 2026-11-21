package app.aure.tv

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean
import app.aure.tv.backup.BackupRecovery
import app.aure.tv.data.StartupPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    // PackageManager queries are IO-bound; the accessibility event thread must
    // never wait for a slow package scan while the user is pressing Home.
    private val homesWorker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val homesWarming = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // The service can start before a backup rollback finishes; the Home
        // and boot preferences it reads must wait for that recovery.
        if (BackupRecovery.isReady.value) {
            configureService()
        } else {
            homesWorker.launch {
                BackupRecovery.await()
                configureService()
            }
        }
    }

    private fun configureService() {
        if (instance !== this) return
        requestStockHomesRefresh()

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
        requestStockHomesRefresh()
        return packageName == this.packageName ||
            stockHomes(this).any { it.startsWith("$packageName/") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!BackupRecovery.isReady.value) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        lastPackage = packageName
        val className = event.className?.toString() ?: return
        if (!StartupPreferences.homeOverrideWanted(this)) return
        requestStockHomesRefresh()
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

    private fun requestStockHomesRefresh() {
        if (!stockHomesAreStale() || !homesWarming.compareAndSet(false, true)) return
        homesWorker.launch {
            try {
                refreshStockHomes(this@OVisionAccessibilityService)
            } finally {
                homesWarming.set(false)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        homesWorker.cancel()
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
        private const val HOMES_TTL_MS = 60_000L

        /** Cached candidates only; the actual query runs on [Dispatchers.IO]. */
        fun stockHomes(@Suppress("UNUSED_PARAMETER") context: Context): Set<String> =
            // Keep the stale set until the worker replaces it: an empty set
            // here would make every Home press fail while the refresh runs.
            snapshot?.entries ?: emptySet()

        fun stockHomesAreStale(): Boolean {
            val current = snapshot ?: return true
            return SystemClock.elapsedRealtime() - current.at >= HOMES_TTL_MS
        }

        /**
         * Must be called from an IO worker, never from the event thread. The
         * PackageManager query deliberately runs without the publish lock: the
         * accessibility thread must never wait behind a slow package scan.
         */
        fun refreshStockHomes(context: Context) {
            if (!stockHomesAreStale()) return
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
            val now = SystemClock.elapsedRealtime()
            synchronized(lock) {
                // A parallel refresh may have published a newer snapshot while
                // the query was running; never replace it with this result.
                val current = snapshot
                if (current != null && now - current.at < HOMES_TTL_MS) return
                snapshot = Snapshot(found, now)
            }
        }

        private data class Snapshot(val entries: Set<String>, val at: Long)
        private val lock = Any()
        @Volatile
        private var snapshot: Snapshot? = null
    }
}
