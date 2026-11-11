package com.whatuneed.ovisiontv.kotlin.data

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
            assertTrue(small.shelfArtEntries <= large.shelfArtEntries)
            assertTrue(small.shelfArtMaxPx <= large.shelfArtMaxPx)
            assertTrue(small.shadowSteps <= large.shadowSteps)
        }
    }

    // Twelve posters are on screen at once; a profile that keeps fewer
    // re-decodes art that is still being drawn.
    @Test
    fun every_profile_keeps_a_full_carousel_of_art() {
        for (profile in PerformanceProfile.entries) {
            assertTrue(AdaptivePolicy.budgets(profile).shelfArtEntries >= 10)
        }
    }
}
