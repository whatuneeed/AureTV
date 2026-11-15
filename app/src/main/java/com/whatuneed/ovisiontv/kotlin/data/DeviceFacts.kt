package com.whatuneed.ovisiontv.kotlin.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import java.net.Inet4Address
import java.util.Locale
import java.util.TimeZone

enum class NetworkKind {
    WIFI,
    ETHERNET,
    MOBILE,
    OTHER,
    NONE,
}

/**
 * Facts a TV owner can verify against the device itself. Everything here is a
 * public Android API: no serials, MAC addresses, SSIDs or other identifiers
 * that need location or privileged access.
 */
data class DeviceFacts(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val android: String,
    val api: Int,
    val securityPatch: String,
    val buildDisplay: String,
    val abis: String,
    // Null means the system did not expose the fact; the screen must then say
    // "unknown" instead of printing a zero it would present as a measurement.
    val screenWidth: Int?,
    val screenHeight: Int?,
    val refreshHz: Float?,
    val ramTotal: Long?,
    val ramFree: Long?,
    val lowRam: Boolean,
    val storageTotal: Long?,
    val storageFree: Long?,
    val uptimeMs: Long,
    val locale: String,
    val timeZone: String,
    val network: NetworkKind,
    val ipAddress: String?,
    val defaultHome: Boolean,
    val airplayInstalled: Boolean,
)

/** The narrowest grant for reading the Wi-Fi name on a given API level. */
@Suppress("InlinedApi") // A compile-time string constant, guarded by the API check below.
internal fun wifiNamePermissionFor(sdk: Int): String =
    if (sdk >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }

/**
 * Android 12-12L ignore a fine-location request unless coarse is sent with it.
 * API 33+ uses the Wi-Fi-specific grant, so neither location permission is
 * ever requested there.
 */
@Suppress("InlinedApi") // A compile-time string constant, guarded by the API check below.
internal fun wifiNamePermissionsFor(sdk: Int): Array<String> = when {
    sdk >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    sdk >= Build.VERSION_CODES.S -> arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

object DeviceFactsReader {
    /** The GPL-3.0 receiver is a separate app; the launcher never links its code. */
    const val AIRPLAY_PACKAGE = "io.github.besliky.airplaytv"

    fun read(context: Context): DeviceFacts {
        val app = context.applicationContext
        val activityManager = app.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        val display = app.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val data = runCatching { StatFs(app.filesDir.absolutePath) }.getOrNull()
        // A build without the network-state grant must still open Settings.
        val network = runCatching { network(app) }
            .getOrDefault(NetworkKind.NONE to null)
        val packageManager = app.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val home = runCatching {
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        val airplay = runCatching {
            packageManager.getPackageInfo(AIRPLAY_PACKAGE, 0)
        }.isSuccess

        return DeviceFacts(
            manufacturer = Build.MANUFACTURER.orDash(),
            brand = Build.BRAND.orDash(),
            model = Build.MODEL.orDash(),
            android = "Android ${Build.VERSION.RELEASE}",
            api = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.orDash(),
            buildDisplay = Build.DISPLAY.orDash(),
            abis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "—" },
            screenWidth = mode?.physicalWidth,
            screenHeight = mode?.physicalHeight,
            refreshHz = mode?.refreshRate ?: display?.refreshRate,
            ramTotal = memory.totalMem.takeIf { activityManager != null && it > 0L },
            ramFree = memory.availMem.takeIf { activityManager != null && it > 0L },
            lowRam = activityManager?.isLowRamDevice == true,
            storageTotal = data?.let { it.blockCountLong * it.blockSizeLong },
            storageFree = data?.let { it.availableBlocksLong * it.blockSizeLong },
            uptimeMs = SystemClock.elapsedRealtime(),
            locale = Locale.getDefault().toLanguageTag(),
            timeZone = TimeZone.getDefault().id,
            network = network.first,
            ipAddress = network.second,
            defaultHome = home?.activityInfo?.packageName == app.packageName,
            airplayInstalled = airplay,
        )
    }

    /**
     * Transport of the active network for the status capsule and Control
     * Center. The name itself is read by [wifiSsid], which needs the narrow
     * Wi-Fi-name grant described below.
     */
    fun currentNetwork(context: Context): NetworkKind =
        runCatching { network(context).first }.getOrDefault(NetworkKind.NONE)

    /**
     * Android 13+ has a Wi-Fi-specific grant that can promise the system the
     * name is not used to derive location. Older releases still gate the same
     * SSID read behind fine location; this is the narrowest grant each API
     * offers, not a general location request.
     */
    fun wifiNamePermission(): String = wifiNamePermissionFor(Build.VERSION.SDK_INT)

    fun wifiNamePermissions(): Array<String> = wifiNamePermissionsFor(Build.VERSION.SDK_INT)

    fun hasWifiNamePermission(context: Context): Boolean =
        context.checkSelfPermission(wifiNamePermission()) == PackageManager.PERMISSION_GRANTED

    /**
     * The Wi-Fi network name for the Control Center tile. Without the narrow
     * Wi-Fi grant (or legacy location grant) the tile says "Connected" — the
     * same fallback as the Java build.
     */
    @SuppressLint("MissingPermission")
    fun wifiSsid(context: Context): String? {
        if (!hasWifiNamePermission(context)) return null
        val manager = context.getSystemService(WifiManager::class.java) ?: return null
        return visibleSsid(runCatching { manager.connectionInfo?.ssid }.getOrNull())
    }

    /** Android returns the name in quotes and says "unknown ssid" without location. */
    fun visibleSsid(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.contains("unknown ssid", ignoreCase = true)) return null
        return raw.trim('"').ifBlank { null }
    }

    /** Used by the wizard and Settings: is this launcher the system Home? */
    fun isDefaultHome(context: Context): Boolean {
        val packageManager = context.packageManager
        val home = runCatching {
            packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        }.getOrNull()
        return home?.activityInfo?.packageName == context.packageName
    }

    private fun network(context: Context): Pair<NetworkKind, String?> {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkKind.NONE to null
        val active = manager.activeNetwork ?: return NetworkKind.NONE to null
        val capabilities = manager.getNetworkCapabilities(active) ?: return NetworkKind.NONE to null
        val kind = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.MOBILE
            else -> NetworkKind.OTHER
        }
        val ip = runCatching {
            manager.getLinkProperties(active)
                ?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
                ?.address
                ?.hostAddress
        }.getOrNull()
        return kind to ip
    }

    private fun String.orDash(): String = ifBlank { "—" }
}

/** Pure formatting so the device page can be checked without a TV. */
object DeviceFormat {
    /**
     * [units] is B, KB, MB, GB, TB in the screen language: the number follows
     * [locale]'s decimal separator while the magnitude stays binary. The caller
     * passes the launcher language explicitly, so a Russian UI does not switch
     * to a comma just because the system locale changed underneath it.
     */
    fun size(bytes: Long, units: List<String>, locale: Locale): String {
        if (bytes < 1024L || units.size < 2) return "$bytes ${units.firstOrNull().orEmpty()}"
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        val pattern = if (value >= 10.0) "%.0f %s" else "%.1f %s"
        return String.format(locale, pattern, value, units[index])
    }

    fun uptime(ms: Long, days: String, hours: String, minutes: String): String {
        val totalMinutes = (ms.coerceAtLeast(0L) / 60_000L).toInt()
        val d = totalMinutes / (24 * 60)
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return when {
            d > 0 -> "$d $days $h $hours"
            h > 0 -> "$h $hours $m $minutes"
            else -> "$m $minutes"
        }
    }

    fun hertz(value: Float?, locale: Locale): String {
        if (value == null || value <= 0f) return "—"
        val rounded = kotlin.math.round(value * 10f) / 10f
        return if (rounded % 1f == 0f) {
            "${rounded.toInt()} Hz"
        } else {
            String.format(locale, "%.1f Hz", rounded)
        }
    }
}
