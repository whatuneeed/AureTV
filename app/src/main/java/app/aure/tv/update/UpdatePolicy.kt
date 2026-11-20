package app.aure.tv.update

import android.os.Build

/** Install eligibility is decided by versionCode, never by versionName sorting. */
internal object UpdateVersion {
    fun isNewer(candidate: Int, installed: Int): Boolean = candidate > installed

    fun isSameOrOlder(candidate: Int, installed: Int): Boolean = candidate <= installed
}

internal enum class Incompatibility {
    ANDROID_TOO_OLD,
    ABI_UNSUPPORTED,
    VERSION_NOT_NEWER,
}

internal data class CompatibilityResult(
    val compatible: Boolean,
    val eol: Boolean = false,
    val reason: Incompatibility? = null,
)

internal object UpdateCompatibility {
    /**
     * The app is a universal APK today, but the asset field exists so a future
     * native build cannot be installed on the wrong CPU without a check.
     */
    fun check(
        release: UpdateRelease,
        installedVersionCode: Int,
        api: Int = Build.VERSION.SDK_INT,
        abis: List<String> = Build.SUPPORTED_ABIS.toList(),
    ): CompatibilityResult {
        if (!UpdateVersion.isNewer(release.versionCode, installedVersionCode)) {
            return CompatibilityResult(false, reason = Incompatibility.VERSION_NOT_NEWER)
        }
        if (release.minAndroidApi > api) {
            return CompatibilityResult(false, reason = Incompatibility.ANDROID_TOO_OLD)
        }
        val assetAbi = release.asset.abi
        if (assetAbi != "universal" && assetAbi !in abis) {
            return CompatibilityResult(false, reason = Incompatibility.ABI_UNSUPPORTED)
        }
        return CompatibilityResult(
            compatible = true,
            eol = installedVersionCode < release.minSupportedVersionCode,
        )
    }

    /** Conservative cache estimate; the system installer may still need more. */
    fun requiredCacheBytes(assetSize: Long): Long =
        assetSize.coerceAtLeast(0L) * 2L + 16L * 1024L * 1024L
}
