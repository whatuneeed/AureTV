package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.roundToInt

/**
 * Banners and icons are immutable for a given package version, yet every
 * broadcast and every return from Settings re-decoded all of them. The cache is
 * keyed by lastUpdateTime so an updated app picks up its new artwork, and the
 * budget is small enough to be evicted instead of holding the grid hostage.
 */
internal object AppArtCache {
    private val cache = object : LruCache<String, ImageBitmap>(Adaptive.budgets.appArtKb) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.asAndroidBitmap().allocationByteCount / 1024
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }

    /** Called from MemoryPressure; any artwork can be re-read from the APK. */
    fun trim(percent: Int) {
        if (percent >= 100) cache.evictAll()
        else cache.trimToSize(cache.maxSize() * (100 - percent) / 100)
    }

    /** A lower profile (thermal, pressure) may shrink the budget after creation. */
    fun shrinkTo(kb: Int) {
        if (kb < cache.maxSize()) cache.trimToSize(kb)
    }
}

class AppRepository(context: Context) {
    // The launcher language lives in its own preference and is applied by
    // wrapping an Activity; the application context keeps the system locale.
    // Labels and grid order are read from here, so the wrap has to be re-applied
    // or the "Settings" tile stays in the system language after a switch.
    private val context: Context = LocalePreference.wrap(context)

    suspend fun load(): List<TvApp> = load(withArtwork = true)

    /**
     * Settings lists names only; decoding a banner and an icon for every
     * installed app would allocate bitmaps that screen never draws.
     */
    suspend fun load(withArtwork: Boolean): List<TvApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        // The launcher can show a language different from the system one; the
        // grid order must follow the captions the user actually reads.
        val locale = context.resources.configuration.locales[0]
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }

        val activities = collectActivities(packageManager)
        activities
            .mapNotNull { activity -> readApp(packageManager, activity, withArtwork) }
            .sortedWith(
                compareBy(collator) { app: TvApp -> app.label }
                    .thenBy { app: TvApp -> app.packageName },
            )
    }

    /**
     * Leanback activities first: an app that ships both a TV and a phone entry
     * point must be opened through the TV one. Deduplicated by package because
     * the grid shows one tile per app.
     */
    private fun collectActivities(packageManager: PackageManager): List<ActivityInfo> {
        val byPackage = LinkedHashMap<String, ActivityInfo>()
        val categories = listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER,
            Intent.CATEGORY_LAUNCHER,
        )

        for (category in categories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val resolved = try {
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            } catch (_: Exception) {
                emptyList()
            }
            for (resolveInfo in resolved) {
                val activity = resolveInfo.activityInfo ?: continue
                // The launcher itself is not an app tile, but its Settings screen
                // is: tvOS exposes Settings as a normal grid icon.
                if (activity.packageName == context.packageName &&
                    activity.name != SettingsActivity::class.java.name
                ) {
                    continue
                }
                if (activity.name == null) continue
                byPackage.putIfAbsent(activity.packageName, activity)
            }
        }
        return byPackage.values.toList()
    }

    private fun readApp(
        packageManager: PackageManager,
        activity: ActivityInfo,
        withArtwork: Boolean,
    ): TvApp? {
        // PackageInfo carries both the application entry and lastUpdateTime, so
        // one lookup keys the artwork cache to the exact package version.
        val packageInfo = try {
            packageManager.getPackageInfo(activity.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val appInfo = packageInfo.applicationInfo ?: return null

        // The system TV Settings is also labelled "Настройки"/"Settings", so the
        // launcher tile needs its own name to stay distinguishable in the grid.
        val label = if (activity.packageName == context.packageName) {
            context.getString(R.string.settings_tile_name)
        } else {
            packageManager.getApplicationLabel(appInfo)
                .toString()
                .ifBlank { activity.packageName }
        }

        // The application banner belongs to the launcher itself and would replace
        // the gear that SettingsActivity declares, so this one tile skips it.
        val ownSettings = activity.packageName == context.packageName
        val stamp = packageInfo.lastUpdateTime
        val banner = if (!withArtwork || ownSettings) {
            null
        } else {
            val key = "${activity.packageName}|$stamp|banner|400x240"
            AppArtCache.get(key) ?: drawableToImage(
                appInfo.loadBannerSafe(packageManager), 400, 240,
            )?.also { AppArtCache.put(key, it) }
        }
        val icon = if (!withArtwork) {
            null
        } else if (banner == null) {
            val key = "${activity.packageName}|$stamp|icon|256x256"
            val cached = AppArtCache.get(key)
            cached ?: run {
                val drawable = if (ownSettings) {
                    activity.loadIconSafe(packageManager)
                } else {
                    appInfo.loadIconSafe(packageManager)
                }
                drawableToImage(drawable, 256, 256)?.also { AppArtCache.put(key, it) }
            }
        } else {
            null
        }

        return TvApp(
            packageName = activity.packageName,
            activityName = activity.name,
            label = label,
            banner = banner,
            icon = icon,
            system = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        )
    }

    private fun ApplicationInfo.loadBannerSafe(packageManager: PackageManager): Drawable? =
        try {
            loadBanner(packageManager)
        } catch (_: Exception) {
            null
        }

    private fun ApplicationInfo.loadIconSafe(packageManager: PackageManager): Drawable? =
        try {
            loadIcon(packageManager)
        } catch (_: Exception) {
            null
        }

    private fun ActivityInfo.loadIconSafe(packageManager: PackageManager): Drawable? =
        try {
            loadIcon(packageManager)
        } catch (_: Exception) {
            null
        }

}

/**
 * Android TV banners are 16:9 while the home cell is 5:3. Copying into a 5:3
 * bitmap would stretch the artwork before Compose crops it, so the copy keeps
 * the source aspect and only fits inside the given box. It never upscales: a
 * small source stays at its own pixel size.
 */
internal fun drawableToImage(drawable: Drawable?, maxWidth: Int, maxHeight: Int): ImageBitmap? {
    if (drawable == null) return null
    return try {
        val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxWidth
        val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxHeight
        val (width, height) = fitInside(sourceWidth, sourceHeight, maxWidth, maxHeight)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/** Fits a source into a box without upscaling; shared with unit tests. */
internal fun fitInside(
    sourceWidth: Int,
    sourceHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
): Pair<Int, Int> {
    val factor = minOf(
        maxWidth.toFloat() / sourceWidth,
        maxHeight.toFloat() / sourceHeight,
    ).coerceAtMost(1f)
    return (sourceWidth * factor).roundToInt().coerceAtLeast(1) to
        (sourceHeight * factor).roundToInt().coerceAtLeast(1)
}

/**
 * The home shelf may paint one banner across the screen, while [TvApp.banner]
 * is a 400×240 grid thumbnail. Only the focused app is decoded at screen size,
 * on IO, so an app that ships a larger banner is not shown from the thumbnail;
 * a smaller source keeps the thumbnail instead of being upscaled into a bigger
 * bitmap.
 */
internal suspend fun loadShelfBanner(
    context: Context,
    packageName: String,
    maxWidth: Int,
    maxHeight: Int,
): ImageBitmap? = withContext(Dispatchers.IO) {
    val packageManager = context.packageManager
    val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
        ?: return@withContext null
    drawableToImage(runCatching { info.loadBanner(packageManager) }.getOrNull(), maxWidth, maxHeight)
}
