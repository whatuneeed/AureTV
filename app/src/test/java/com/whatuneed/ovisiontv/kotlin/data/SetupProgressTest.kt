package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PHASE 26: a re-run starts at the first page, an interruption resumes the page
 * the viewer reached, and a page map from an older build starts over. The count
 * here is the wizard's real page count (9 since the saver split into two pages).
 */
class SetupProgressTest {

    @Test
    fun firstRunResumesTheSavedStep() {
        assertEquals(3, SetupProgress.resumeStep(done = false, step = 3, version = 5, currentVersion = 5, count = 9))
    }

    @Test
    fun reRunStartsAtTheFirstPage() {
        assertEquals(0, SetupProgress.resumeStep(done = true, step = 4, version = 5, currentVersion = 5, count = 9))
    }

    @Test
    fun olderPageMapStartsOver() {
        assertEquals(0, SetupProgress.resumeStep(done = false, step = 4, version = 4, currentVersion = 5, count = 9))
    }

    @Test
    fun savedStepIsClampedToTheRealPages() {
        assertEquals(8, SetupProgress.resumeStep(done = false, step = 9, version = 5, currentVersion = 5, count = 9))
        assertEquals(0, SetupProgress.resumeStep(done = false, step = -2, version = 5, currentVersion = 5, count = 9))
    }

    @Test
    fun emptyPageMapFallsBackToTheFirstPage() {
        assertEquals(0, SetupProgress.resumeStep(done = false, step = 3, version = 5, currentVersion = 5, count = 0))
    }

    /**
     * The saver step split in two after the profile page, so every stored step
     * number from 0.4.25 and earlier would open the wrong question. The version
     * bump is what makes [olderPageMapStartsOver] fire for a real device; pin it
     * here so a future page insertion cannot ship without raising it.
     */
    @Test
    fun stepVersionIsFiveForTheSaverSplit() {
        assertEquals(5, SetupProgress.STEP_VERSION)
    }
}
