package app.aure.tv.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.mutableIntStateOf
import app.aure.tv.R
import app.aure.tv.SettingsActivity
import kotlinx.coroutines.CancellationException
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

    /**
     * Bumped on every eviction. Cards key their remembered artwork on it, so a
     * tile that still held a dropped bitmap lets go of it and re-reads from the
     * APK instead of pinning the whole grid in memory (audit A-01).
     */
    val epoch = mutableIntStateOf(0)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }

    /** Called from MemoryPressure; any artwork can be re-read from the APK. */
    fun trim(percent: Int) {
        if (percent >= 100) {
            // Drop everything now, but keep a bounded budget for the refill.
            val max = cache.maxSize()
            cache.evictAll()
            cache.resize(trimmedCapacity(max, 100))
        } else {
            // Resize, not just trim: a refill must not return to the old budget.
            val target = trimmedCapacity(cache.maxSize(), percent)
            cache.resize(target)
            cache.trimToSize(target)
        }
        epoch.intValue++
    }

    /** A lower profile (thermal, pressure) may shrink the budget after creation. */
    fun shrinkTo(kb: Int) {
        val target = kb.coerceAtLeast(1)
        if (target < cache.maxSize()) {
            cache.resize(target)
            cache.trimToSize(target)
            epoch.intValue++
        }
    }

    /**
     * Re-reads one entry from the package manager after it was evicted. The key
     * is the exact string [AppRepository.readApp] built, so an app update (a new
     * lastUpdateTime) can never resolve to the previous version's artwork.
     * Runs on the caller's IO dispatcher; the decode itself is ~1 ms.
     */
    fun load(context: Context, key: String): ImageBitmap? {
        get(key)?.let { return it }
        val parsed = parseArtKey(key) ?: return null
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val info = runCatching { packageManager.getPackageInfo(parsed.packageName, 0) }.getOrNull()
            ?: return null
        val app = info.applicationInfo ?: return null
        val kind = parsed.kind
        val drawable = when {
            // The launcher's own Settings tile declares its own icon, which is
            // not the application icon; the key keeps the activity name for it.
            kind.startsWith(KIND_ACTIVITY) -> runCatching {
                packageManager.getActivityInfo(
                    ComponentName(parsed.packageName, kind.removePrefix(KIND_ACTIVITY)),
                    0,
                )
            }.getOrNull()?.loadIconSafe(packageManager)

            kind == KIND_BANNER -> app.loadBannerSafe(packageManager)
            else -> app.loadIconSafe(packageManager)
        }
        return drawableToImage(drawable, parsed.width, parsed.height)?.also { put(key, it) }
    }
}

internal const val KIND_BANNER = "banner"
internal const val KIND_ICON = "icon"
internal const val KIND_ACTIVITY = "act:"

/** One decoded [TvApp] artwork key: package, kind and the box it fits into. */
internal data class ArtKey(
    val packageName: String,
    val kind: String,
    val width: Int,
    val height: Int,
)

/**
 * The key is "package|stamp|kind|WxH". Anything else is not ours, so a corrupt
 * saved layout can never make the reload touch an unrelated package.
 */
internal fun parseArtKey(key: String): ArtKey? {
    val parts = key.split('|')
    if (parts.size != 4 || parts[0].isEmpty()) return null
    val size = parts[3].split('x', limit = 2)
    val width = size.getOrNull(0)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val height = size.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
    return ArtKey(parts[0], parts[2], width, height)
}

/**
 * Registers art that was painted directly (tests, previews) under a stable key
 * and hands the key to the tile. Only the cache holds the bitmap, the same rule
 * the package-manager path follows.
 */
internal fun artKey(key: String, art: ImageBitmap?): String? =
    art?.let {
        AppArtCache.put(key, it)
        key
    }

/** Phone-only apps stay visible by default; Settings can hide them. */
internal fun showPhoneApps(context: Context): Boolean =
    SafePreferences.boolean(
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE),
        LauncherPrefs.KEY_SHOW_PHONE_APPS,
        true,
    )

internal fun setShowPhoneApps(context: Context, show: Boolean) {
    context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(LauncherPrefs.KEY_SHOW_PHONE_APPS, show)
        .apply()
}

internal fun filterPhoneOnly(apps: List<TvApp>, showPhoneApps: Boolean): List<TvApp> =
    if (showPhoneApps) apps else apps.filter { it.leanback }

/**
 * Grid banners are painted into a 250x150 design tile. At 1080p a 400x240
 * source is already oversampled; a 4K panel doubles the tile to 500x300, so
 * only then is the larger decode worth its memory. The threshold is the panel
 * width, not the Launcher profile.
 */
internal fun gridBannerBox(panelWidthPx: Int): Pair<Int, Int> =
    if (panelWidthPx >= 2560) 500 to 300 else 400 to 240

private const val APP_TAG = "AppRepository"

private fun AppLoadResult.appsOrEmpty(): List<TvApp> = when (this) {
    is AppLoadResult.Loaded -> apps
    is AppLoadResult.Failed -> emptyList()
}

/** A failed scan must stay distinguishable from a genuinely empty app list. */
sealed interface AppLoadResult {
    data class Loaded(val apps: List<TvApp>) : AppLoadResult
    data class Failed(val error: Exception) : AppLoadResult
}

class AppRepository(context: Context) {
    // The launcher language lives in its own preference and is applied by
    // wrapping an Activity; the application context keeps the system locale.
    // Labels and grid order are read from here, so the wrap has to be re-applied
    // or the "Settings" tile stays in the system language after a switch.
    private val context: Context = LocalePreference.wrap(context)

    suspend fun load(
        withArtwork: Boolean = true,
        prefsName: String = LauncherPrefs.NAME,
    ): List<TvApp> = loadResult(withArtwork, prefsName).appsOrEmpty()

    /**
     * A Primary-surface load: a failure is returned to the caller instead of
     * escaping a UI coroutine. Cancellation is never converted into a failure.
     */
    suspend fun loadResult(
        withArtwork: Boolean = true,
        prefsName: String = LauncherPrefs.NAME,
    ): AppLoadResult = try {
        AppLoadResult.Loaded(loadNow(withArtwork, prefsName))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(APP_TAG, "app catalogue load failed", error)
        AppLoadResult.Failed(error)
    }

    /**
     * Settings lists names only; decoding a banner and an icon for every
     * installed app would allocate bitmaps that screen never draws.
     */
    private suspend fun loadNow(
        withArtwork: Boolean,
        prefsName: String,
    ): List<TvApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        // The launcher can show a language different from the system one; the
        // grid order must follow the captions the user actually reads.
        val locale = context.resources.configuration.locales[0]
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }

        val activities = collectActivities(packageManager)
        // One preference read for the whole scan: per-tile parsing would repeat
        // the same JSON for every app in the grid.
        val customLabels = AppLabelStore.labels(context, prefsName)
        filterPhoneOnly(
            activities.mapNotNull { activity ->
                readApp(
                    packageManager,
                    activity.info,
                    activity.leanback,
                    withArtwork,
                    customLabels[activity.info.packageName],
                )
            },
            showPhoneApps(context),
        )
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
    private data class DiscoveredActivity(val info: ActivityInfo, val leanback: Boolean)

    private fun collectActivities(packageManager: PackageManager): List<DiscoveredActivity> {
        val byPackage = LinkedHashMap<String, DiscoveredActivity>()
        val categories = listOf(
            Intent.CATEGORY_LEANBACK_LAUNCHER to true,
            Intent.CATEGORY_LAUNCHER to false,
        )

        for ((category, leanback) in categories) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            // A failed query is a failed scan, not a genuinely empty launcher:
            // let it reach loadResult so Home can offer a retry.
            val resolved = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
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
                byPackage.putIfAbsent(activity.packageName, DiscoveredActivity(activity, leanback))
            }
        }
        return byPackage.values.toList()
    }

    private fun readApp(
        packageManager: PackageManager,
        activity: ActivityInfo,
        leanback: Boolean,
        withArtwork: Boolean,
        customLabel: String?,
    ): TvApp? = try {
        readAppUnchecked(packageManager, activity, leanback, withArtwork, customLabel)
    } catch (_: PackageManager.NameNotFoundException) {
        // The package disappeared after the query; the rest of the grid is still valid.
        null
    } catch (_: Exception) {
        // One broken package (resources, label, security state) must not abort
        // the whole catalogue and leave Home without its other tiles.
        Log.w(APP_TAG, "app entry skipped after a load error")
        null
    }

    private fun readAppUnchecked(
        packageManager: PackageManager,
        activity: ActivityInfo,
        leanback: Boolean,
        withArtwork: Boolean,
        customLabel: String?,
    ): TvApp? {
        // PackageInfo carries both the application entry and lastUpdateTime, so
        // one lookup keys the artwork cache to the exact package version.
        val packageInfo = packageManager.getPackageInfo(activity.packageName, 0)
        val appInfo = packageInfo.applicationInfo ?: return null
        // A component disabled after the scan must not keep a dead tile.
        if (!activity.enabled || !appInfo.enabled) return null

        // The system TV Settings is also labelled "Настройки"/"Settings", so the
        // launcher tile needs its own name to stay distinguishable in the grid.
        val defaultLabel = if (activity.packageName == context.packageName) {
            context.getString(R.string.settings_tile_name)
        } else {
            packageManager.getApplicationLabel(appInfo)
                .toString()
                .ifBlank { activity.packageName }
        }
        val label = customLabel ?: defaultLabel

        // The application banner belongs to the launcher itself and would replace
        // the gear that SettingsActivity declares, so this one tile skips it.
        val ownSettings = activity.packageName == context.packageName
        val stamp = packageInfo.lastUpdateTime
        // The cache is decoded eagerly so the first frame has art, but the tile
        // keeps only the key: nothing in the grid owns a bitmap (A-01).
        val banner = if (!withArtwork || ownSettings) {
            null
        } else {
            val (bannerWidth, bannerHeight) =
                gridBannerBox(context.resources.displayMetrics.widthPixels)
            val key = "${activity.packageName}|$stamp|$KIND_BANNER|${bannerWidth}x$bannerHeight"
            artKey(
                key,
                AppArtCache.get(key) ?: drawableToImage(
                    appInfo.loadBannerSafe(packageManager),
                    bannerWidth,
                    bannerHeight,
                ),
            )
        }
        val icon = if (!withArtwork || banner != null) {
            null
        } else {
            val kind = if (ownSettings) {
                KIND_ACTIVITY + activity.name
            } else {
                KIND_ICON
            }
            val key = "${activity.packageName}|$stamp|$kind|256x256"
            val art = AppArtCache.get(key) ?: run {
                val drawable = if (ownSettings) {
                    activity.loadIconSafe(packageManager)
                } else {
                    appInfo.loadIconSafe(packageManager)
                }
                drawableToImage(drawable, 256, 256)
            }
            artKey(key, art)
        }

        return TvApp(
            packageName = activity.packageName,
            activityName = activity.name,
            label = label,
            banner = banner,
            icon = icon,
            system = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            leanback = leanback,
        )
    }

}

internal fun ApplicationInfo.loadBannerSafe(packageManager: PackageManager): Drawable? =
    try {
        loadBanner(packageManager)
    } catch (_: Exception) {
        null
    }

internal fun ApplicationInfo.loadIconSafe(packageManager: PackageManager): Drawable? =
    try {
        loadIcon(packageManager)
    } catch (_: Exception) {
        null
    }

internal fun ActivityInfo.loadIconSafe(packageManager: PackageManager): Drawable? =
    try {
        loadIcon(packageManager)
    } catch (_: Exception) {
        null
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
