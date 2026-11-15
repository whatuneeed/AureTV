package com.whatuneed.ovisiontv.kotlin.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientPowerPolicyTest {

    @Test
    fun dimming_is_off_until_the_chosen_minute() {
        assertEquals(1f, AmbientPowerPolicy.dimAlpha(0L, AmbientDim.NEVER), 0f)
        assertEquals(1f, AmbientPowerPolicy.dimAlpha(59_999L, AmbientDim.TEN), 0f)
        assertEquals(0.8f, AmbientPowerPolicy.dimAlpha(600_000L, AmbientDim.TEN), 0.001f)
    }

    @Test
    fun dimming_takes_a_second_step_five_minutes_later() {
        assertEquals(
            0.8f,
            AmbientPowerPolicy.dimAlpha(600_000L + AmbientPowerPolicy.DIM_STEP_MS - 1, AmbientDim.TEN),
            0.001f,
        )
        assertEquals(
            AmbientPowerPolicy.SECOND_DIM_ALPHA,
            AmbientPowerPolicy.dimAlpha(600_000L + AmbientPowerPolicy.DIM_STEP_MS, AmbientDim.TEN),
            0.001f,
        )
    }

    @Test
    fun a_session_limit_is_off_by_default_and_expires_exactly_at_the_limit() {
        assertFalse(AmbientPowerPolicy.sessionExpired(24 * 60 * 60_000L, AmbientSession.NEVER))
        assertFalse(AmbientPowerPolicy.sessionExpired(1_799_999L, AmbientSession.THIRTY))
        assertTrue(AmbientPowerPolicy.sessionExpired(1_800_000L, AmbientSession.THIRTY))
    }
}
