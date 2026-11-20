package app.aure.tv.data

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePolicyTest {

    // A device flagged low-RAM stays LOW even when someone reports a large
    // heap: the flag is the vendor's own statement about the box.
    @Test
    fun low_ram_device_is_low_whatever_the_heap() {
        assertEquals(PerformanceProfile.LOW, AdaptivePolicy.profileFor(true, 512))
    }

    @Test
    fun heap_limit_decides_the_profile() {
        assertEquals(PerformanceProfile.LOW, AdaptivePolicy.profileFor(false, 128))
        assertEquals(PerformanceProfile.LOW, AdaptivePolicy.profileFor(false, 160))
        assertEquals(PerformanceProfile.BALANCED, AdaptivePolicy.profileFor(false, 161))
        assertEquals(PerformanceProfile.BALANCED, AdaptivePolicy.profileFor(false, 192))
        assertEquals(PerformanceProfile.BALANCED, AdaptivePolicy.profileFor(false, 320))
        assertEquals(PerformanceProfile.HIGH, AdaptivePolicy.profileFor(false, 321))
        assertEquals(PerformanceProfile.HIGH, AdaptivePolicy.profileFor(false, 512))
    }

    // 0 means the system did not answer; guessing LOW would punish every TV.
    @Test
    fun unknown_heap_keeps_the_full_budgets() {
        assertEquals(PerformanceProfile.HIGH, AdaptivePolicy.profileFor(false, 0))
    }

    @Test
    fun thermal_status_only_ever_caps() {
        assertEquals(
            PerformanceProfile.HIGH,
            AdaptivePolicy.thermalCap(PowerManager.THERMAL_STATUS_NONE),
        )
        assertEquals(
            PerformanceProfile.HIGH,
            AdaptivePolicy.thermalCap(PowerManager.THERMAL_STATUS_LIGHT),
        )
        assertEquals(
            PerformanceProfile.BALANCED,
            AdaptivePolicy.thermalCap(PowerManager.THERMAL_STATUS_MODERATE),
        )
        assertEquals(
            PerformanceProfile.LOW,
            AdaptivePolicy.thermalCap(PowerManager.THERMAL_STATUS_SEVERE),
        )
        assertEquals(
            PerformanceProfile.LOW,
            AdaptivePolicy.thermalCap(PowerManager.THERMAL_STATUS_CRITICAL),
        )
        assertEquals(
            PerformanceProfile.LOW,
            AdaptivePolicy.thermalCap(PowerManager.THERMAL_STATUS_EMERGENCY),
        )
        assertEquals(
            PerformanceProfile.LOW,
            AdaptivePolicy.thermalCap(PowerManager.THERMAL_STATUS_SHUTDOWN),
        )
    }

    // A downgrade must never raise a budget: the next profile down is the old
    // one with less memory, not a different mix.
    @Test
    fun a_lower_profile_never_raises_a_budget() {
        val high = AdaptivePolicy.budgets(PerformanceProfile.HIGH)
        val balanced = AdaptivePolicy.budgets(PerformanceProfile.BALANCED)
        val low = AdaptivePolicy.budgets(PerformanceProfile.LOW)
        for ((small, large) in listOf(balanced to high, low to balanced)) {
            assertTrue(small.aerialPosterBytes <= large.aerialPosterBytes)
            assertTrue(small.previewBytes <= large.previewBytes)
            assertTrue(small.previewThumbBytes <= large.previewThumbBytes)
            assertTrue(small.appArtKb <= large.appArtKb)
            assertTrue(small.discoveryArtEntries <= large.discoveryArtEntries)
            assertTrue(small.discoveryArtBytes <= large.discoveryArtBytes)
            assertTrue(small.discoveryCardPx <= large.discoveryCardPx)
            assertTrue(small.discoveryHeroPx <= large.discoveryHeroPx)
            assertTrue(small.shadowSteps <= large.shadowSteps)
        }
    }

    // A rail is eight cards plus the hero; a profile that keeps fewer
    // re-decodes art that is still being drawn.
    @Test
    fun every_profile_keeps_a_full_rail_of_art() {
        for (profile in PerformanceProfile.entries) {
            assertTrue(AdaptivePolicy.budgets(profile).discoveryArtEntries >= 10)
            assertTrue(AdaptivePolicy.budgets(profile).discoveryHeroPx >= 1280)
            assertTrue(AdaptivePolicy.budgets(profile).discoveryCardPx >= 480)
        }
    }

    // The shelf cache is capped in bytes, so the budget must hold the selected
    // hero and the rail at the same time instead of trusting entry count.
    @Test
    fun every_profile_fits_the_hero_and_the_rail_in_its_byte_budget() {
        for (profile in PerformanceProfile.entries) {
            val budget = AdaptivePolicy.budgets(profile)
            val hero = estimatedArtBytes(budget.discoveryHeroPx, budget.discoveryHeroPx * 9 / 16)
            val card = estimatedArtBytes(budget.discoveryCardPx, budget.discoveryCardPx * 9 / 16)
            assertTrue(
                "$profile shelf budget must hold a hero and eight cards",
                hero + card * 8 <= budget.discoveryArtBytes,
            )
        }
    }
}
