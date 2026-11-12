package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRecoveryTest {

    @Test
    fun a_grant_is_never_a_permanent_denial() {
        assertFalse(permanentDenial(granted = true, asked = true, rationale = false))
        assertFalse(permanentDenial(granted = true, asked = false, rationale = false))
    }

    @Test
    fun a_denial_before_asking_is_not_permanent() {
        assertFalse(permanentDenial(granted = false, asked = false, rationale = false))
    }

    @Test
    fun a_temporary_denial_still_shows_the_rationale() {
        assertFalse(permanentDenial(granted = false, asked = true, rationale = true))
    }

    @Test
    fun a_denial_without_a_rationale_is_permanent() {
        assertTrue(permanentDenial(granted = false, asked = true, rationale = false))
    }
}
