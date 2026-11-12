package com.whatuneed.ovisiontv.kotlin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** AQ-01: the entry grant is one-shot and bound to the profile it was issued for. */
class KidsEntryGrantTest {

    @Before
    fun reset() {
        KidsEntryGrant.clear()
    }

    @Test
    fun issuedGrantPassesOnceForTheSameProfile() {
        KidsEntryGrant.issue("p1")
        assertTrue(KidsEntryGrant.consume("p1"))
        assertFalse("the grant must be one-shot", KidsEntryGrant.consume("p1"))
    }

    @Test
    fun anotherProfileCannotUseTheGrantAndConsumesIt() {
        KidsEntryGrant.issue("p1")
        assertFalse(KidsEntryGrant.consume("p2"))
        assertFalse("a foreign consume must still clear the grant", KidsEntryGrant.consume("p1"))
    }

    @Test
    fun withoutGrantNothingPasses() {
        assertFalse(KidsEntryGrant.consume("p1"))
    }

    @Test
    fun clearDropsTheGrant() {
        KidsEntryGrant.issue("p1")
        KidsEntryGrant.clear()
        assertFalse(KidsEntryGrant.consume("p1"))
    }
}