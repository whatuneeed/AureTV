package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.whatuneed.ovisiontv.kotlin.R

/**
 * "Start with the TV": opens the chosen app after boot or wake. Android only
 * lets an app open a screen from the background in a few cases; the
 * accessibility switch is one, so the wake launch works only with it on. The
 * boot launch fires once per process, either from Home or from the service.
 */
object StartupPreferences {
    const val NAME = "startup"
    const val KEY_TARGET = "target"
    const val KEY_LABEL = "label"
    const val KEY_ON_WAKE = "on_wake"
    const val KEY_HOME_OVERRIDE = "home_override"

    const val TARGET_HOME = ""
    private const val APP_PREFIX = "app:"
    private const val BOOT_DELAY_MS = 2_500L
    private const val WAKE_DELAY_MS = 1_200L
    private const val DEDUP_MS = 10_000L
    private const val BOOT_WINDOW_MS = 5 * 60_000L

    // The service and Home both receive SCREEN_ON, so the dedup below is the
    // one thing that keeps a wake from opening the app twice.
    private val handler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var bootHandled = false
    private var lastFired = 0L

    fun target(context: Context): String =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_TARGET, TARGET_HOME)
            .orEmpty()

    /** Empty target is the Home screen, named in the current launcher language. */
    fun label(context: Context): String {
        val target = target(context)
        if (target.isEmpty()) return context.getString(R.string.startup_home)
        return context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_LABEL, target)
            .orEmpty()
            .ifBlank { target }
    }

    fun onWake(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ON_WAKE, false)

    fun set(context: Context, target: String, label: String) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGET, target)
            .putString(KEY_LABEL, label)
            .apply()
    }

    fun setOnWake(context: Context, on: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ON_WAKE, on)
            .apply()
    }

    /** Home button override; on by default, but only needed when not the Home app. */
    fun homeOverride(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HOME_OVERRIDE, true)

    fun setHomeOverride(context: Context, on: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HOME_OVERRIDE, on)
            .apply()
    }

    fun homeOverrideWanted(context: Context): Boolean =
        homeOverride(context) && !DeviceFactsReader.isDefaultHome(context)

    /** The first few minutes after boot; a launcher restart later does not count. */
    fun justBooted(): Boolean = SystemClock.elapsedRealtime() < BOOT_WINDOW_MS

    @Synchronized
    fun onBoot(context: Context) {
        if (bootHandled) return
        bootHandled = true
        if (target(context).isEmpty()) return
        later(context, BOOT_DELAY_MS)
    }

    fun onScreenOn(context: Context) {
        if (!opensOnWake(target(context), onWake(context))) return
        later(context, WAKE_DELAY_MS)
    }

    private fun later(context: Context, delay: Long) {
        val app = context.applicationContext
        handler.postDelayed({ fire(app) }, delay)
    }

    /** Home and the service can both fire on wake; repeats within 10 s are dropped. */
    @Synchronized
    fun fire(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastFired != 0L && now - lastFired < DEDUP_MS) return
        lastFired = now
        val packageName = appPackage(target(context)) ?: return
        launch(context, packageName)
    }

    fun appPackage(target: String): String? {
        if (!target.startsWith(APP_PREFIX)) return null
        return target.substring(APP_PREFIX.length).ifEmpty { null }
    }

    fun targetFor(packageName: String): String = APP_PREFIX + packageName

    fun opensOnWake(target: String, onWake: Boolean): Boolean =
        target.isNotEmpty() && onWake

    private fun launch(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
