package app.aure.tv.data

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.mutableStateOf

enum class PerformanceProfile { HIGH, BALANCED, LOW }

/**
 * Budgets a profile may change. Values are chosen so a profile never removes a
 * surface: only how many decoded frames and shadow layers stay in memory.
 */
data class AdaptiveBudgets(
    val aerialPosterBytes: Int,
    val previewBytes: Int,
    val previewThumbBytes: Int,
    val appArtKb: Int,
    val discoveryArtEntries: Int,
    val discoveryArtBytes: Int,
    val discoveryCardPx: Int,
    val discoveryHeroPx: Int,
    val shadowSteps: Int,
)

/**
 * Conservative decoded size of an ARGB_8888 bitmap. Compose does not expose
 * allocationByteCount, and a hardware bitmap still has to be estimated against
 * the process heap; four bytes per pixel is the worst case, never an undercount.
 */
internal fun estimatedArtBytes(width: Int, height: Int): Int =
    (width.coerceAtLeast(1).toLong() * height.coerceAtLeast(1) * 4L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

/**
 * One place that turns device facts into performance budgets. The launcher has
 * no performance switch: a TV either has the memory for the full-quality
 * budgets or it does not, and the owner should not have to guess which.
 */
object AdaptivePolicy {

    // The per-app heap limit is the number that actually decides when the
    // launcher is killed; total RAM alone lets a 2 GB box with a 512 MB heap
    // look stronger than it is.
    private const val LOW_HEAP_MB = 160
    private const val BALANCED_HEAP_MB = 320

    fun profileFor(lowRamDevice: Boolean, memoryClassMb: Int): PerformanceProfile = when {
        lowRamDevice -> PerformanceProfile.LOW
        memoryClassMb <= 0 -> PerformanceProfile.HIGH
        memoryClassMb <= LOW_HEAP_MB -> PerformanceProfile.LOW
        memoryClassMb <= BALANCED_HEAP_MB -> PerformanceProfile.BALANCED
        else -> PerformanceProfile.HIGH
    }

    /**
     * Thermal caps only ever tighten: a hot BALANCED device drops to LOW, but
     * cooling down does not promote past what the hardware started as.
     */
    fun thermalCap(status: Int): PerformanceProfile = when (status) {
        PowerManager.THERMAL_STATUS_MODERATE -> PerformanceProfile.BALANCED
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN,
        -> PerformanceProfile.LOW
        else -> PerformanceProfile.HIGH
    }

    fun budgets(profile: PerformanceProfile): AdaptiveBudgets = when (profile) {
        PerformanceProfile.HIGH -> AdaptiveBudgets(
            aerialPosterBytes = 32 * 1024 * 1024,
            previewBytes = 6 * 1024 * 1024,
            previewThumbBytes = 16 * 1024 * 1024,
            appArtKb = 12 * 1024,
            // The rail keeps eight cards plus the hero, and a trim should not
            // evict art that is still being drawn.
            discoveryArtEntries = 16,
            // Two heroes and a full rail of cards at the HIGH decode sizes.
            discoveryArtBytes = 48 * 1024 * 1024,
            discoveryCardPx = 720,
            discoveryHeroPx = 2560,
            shadowSteps = 12,
        )
        PerformanceProfile.BALANCED -> AdaptiveBudgets(
            aerialPosterBytes = 24 * 1024 * 1024,
            previewBytes = 6 * 1024 * 1024,
            previewThumbBytes = 12 * 1024 * 1024,
            appArtKb = 12 * 1024,
            // The grid and the shelf are what the owner looks at every day, so
            // their budgets stay whole until the device is genuinely small.
            discoveryArtEntries = 14,
            discoveryArtBytes = 32 * 1024 * 1024,
            discoveryCardPx = 640,
            discoveryHeroPx = 1920,
            shadowSteps = 10,
        )
        PerformanceProfile.LOW -> AdaptiveBudgets(
            aerialPosterBytes = 16 * 1024 * 1024,
            previewBytes = 3 * 1024 * 1024,
            previewThumbBytes = 8 * 1024 * 1024,
            appArtKb = 8 * 1024,
            discoveryArtEntries = 12,
            discoveryArtBytes = 16 * 1024 * 1024,
            discoveryCardPx = 480,
            discoveryHeroPx = 1280,
            shadowSteps = 8,
        )
    }
}

/**
 * Process-wide profile. Installed from every entry activity before any cache
 * is touched, so singletons can size themselves from [budgets] at creation.
 * A later install re-derives from the hardware and the current thermal status;
 * [onThermalStatus] only ever tightens what is already set.
 */
object Adaptive {
    // A Compose state: the Settings "Performance" row shows the live profile,
    // and a thermal downgrade must be visible without re-opening the page.
    private val state = mutableStateOf(PerformanceProfile.HIGH)

    val current: PerformanceProfile get() = state.value

    val budgets: AdaptiveBudgets
        get() = AdaptivePolicy.budgets(current)

    fun install(context: Context) {
        val activityManager = context.applicationContext
            .getSystemService(ActivityManager::class.java)
        val resolved = AdaptivePolicy.profileFor(
            lowRamDevice = activityManager?.isLowRamDevice == true,
            memoryClassMb = activityManager?.memoryClass ?: 0,
        )
        apply(tighten(resolved, thermalStatus(context)))
    }

    fun onThermalStatus(status: Int) {
        apply(tighten(current, status))
    }

    private fun apply(profile: PerformanceProfile) {
        if (profile != state.value) state.value = profile
    }

    private fun tighten(profile: PerformanceProfile, status: Int): PerformanceProfile =
        maxOf(profile, AdaptivePolicy.thermalCap(status))

    /** Thermal status needs API 29; older TV builds keep their resolved profile. */
    @SuppressLint("InlinedApi") // THERMAL_STATUS_NONE is inlined at compile time.
    private fun thermalStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return PowerManager.THERMAL_STATUS_NONE
        val manager = context.applicationContext.getSystemService(PowerManager::class.java)
            ?: return PowerManager.THERMAL_STATUS_NONE
        return runCatching { manager.currentThermalStatus }
            .getOrDefault(PowerManager.THERMAL_STATUS_NONE)
    }
}
