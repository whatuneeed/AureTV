package app.aure.tv.ui

import android.content.ComponentCallbacks2
import androidx.compose.runtime.mutableIntStateOf
import app.aure.tv.data.Adaptive
import app.aure.tv.data.AppArtCache
import app.aure.tv.ui.components.AerialPosterStore
import app.aure.tv.ui.components.AerialPreviewStore
import app.aure.tv.ui.components.dropShelfScrim
import app.aure.tv.ui.components.trimShelfArtCache
import app.aure.tv.ui.components.trimShelfArtCacheTo

/**
 * Bitmap caches are disposable: disk copies keep every poster recoverable, so
 * under pressure they go before the system has to kill the launcher.
 */
internal object MemoryPressure {

    /** How much of each cache to drop and whether the shelf posters may go. */
    data class TrimPlan(val percent: Int, val shelf: Boolean)

    /**
     * Incremented on every trim. Surfaces that hold a decoded frame outside the
     * caches (the saver's warm still) watch it and let go of that frame, or the
     * trim leaves the biggest single allocation untouched (audit A-09).
     */
    val trimTick = mutableIntStateOf(0)

    /**
     * Levels are matched by name: the constants are not ordered by severity
     * (RUNNING_* sit below UI_HIDDEN), so a numeric `when` would trim backwards.
     */
    fun plan(level: Int): TrimPlan = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> TrimPlan(percent = 25, shelf = true)
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> TrimPlan(percent = 50, shelf = true)
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> TrimPlan(percent = 100, shelf = true)
        // Home is merely paused (an app or Settings is in front). The saver
        // frames cannot be used while it is hidden, but the shelf posters are
        // what makes the return instant, so those stay.
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> TrimPlan(percent = 100, shelf = true)
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> TrimPlan(percent = 100, shelf = false)
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> TrimPlan(percent = 100, shelf = false)
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> TrimPlan(percent = 100, shelf = false)
        else -> TrimPlan(percent = 25, shelf = true)
    }

    /**
     * The profile dropped after the caches were sized (thermal throttling).
     * Budgets shrink in place; the shelf art stays, it is what keeps Home
     * instant and it is re-readable from disk at any time.
     */
    fun trimToProfile() {
        val budgets = Adaptive.budgets
        AerialPosterStore.shrinkTo(budgets.aerialPosterBytes)
        AerialPreviewStore.shrinkTo(budgets.previewBytes, budgets.previewThumbBytes)
        trimShelfArtCacheTo(budgets.discoveryArtBytes)
        AppArtCache.shrinkTo(budgets.appArtKb)
    }

    fun trim(level: Int) {
        val plan = plan(level)
        trimTick.intValue++
        AerialPosterStore.trim(plan.percent)
        AerialPreviewStore.trim(plan.percent)
        if (!plan.shelf) trimShelfArtCache(plan.percent)
        // Icons come straight out of the APKs, so they are cheap to bring back
        // but still worth dropping once the launcher is truly in the background.
        if (!plan.shelf) AppArtCache.trim(plan.percent)
        // ~2 MB at half resolution; it is rebuilt on the next shelf draw.
        if (plan.percent >= 100) dropShelfScrim()
    }
}
