package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PHASE 26: a re-run starts at the first page, an interruption resumes the page
 * the viewer reached, and a page map from an older build starts over. The count
 * here is the wizard's real page count (6 after the 0.5.10 condensing).
 */
class SetupProgressTest {

    @Test
    fun firstRunResumesTheSavedStep() {
        assertEquals(3, SetupProgress.resumeStep(done = false, step = 3, version = 6, currentVersion = 6, count = 6))
    }

    @Test
    fun reRunStartsAtTheFirstPage() {
        assertEquals(0, SetupProgress.resumeStep(done = true, step = 4, version = 6, currentVersion = 6, count = 6))
    }

    @Test
    fun olderPageMapStartsOver() {
        assertEquals(0, SetupProgress.resumeStep(done = false, step = 4, version = 5, currentVersion = 6, count = 6))
    }

    @Test
    fun savedStepIsClampedToTheRealPages() {
        assertEquals(5, SetupProgress.resumeStep(done = false, step = 9, version = 6, currentVersion = 6, count = 6))
        assertEquals(0, SetupProgress.resumeStep(done = false, step = -2, version = 6, currentVersion = 6, count = 6))
    }

    @Test
    fun emptyPageMapFallsBackToTheFirstPage() {
        assertEquals(0, SetupProgress.resumeStep(done = false, step = 3, version = 6, currentVersion = 6, count = 0))
    }

    /**
     * The 0.5.10 wizard merged language/profile, Home/weather and removed the
     * separate themes page. Every stored step number from an earlier build
     * would open the wrong question. The version bump is what makes
     * [olderPageMapStartsOver] fire for a real device; pin it here so a future
     * page insertion cannot ship without raising it.
     */
    @Test
    fun stepVersionIsSixForTheCondensedWizard() {
        assertEquals(6, SetupProgress.STEP_VERSION)
    }
}
