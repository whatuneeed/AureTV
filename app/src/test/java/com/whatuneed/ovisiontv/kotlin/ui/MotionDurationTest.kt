package com.whatuneed.ovisiontv.kotlin.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionDurationTest {

    @Test
    fun reduce_motion_removes_the_travel_but_keeps_the_hand_off() {
        assertEquals(500, motionDuration(500, reduceMotion = false))
        assertEquals(0, motionDuration(500, reduceMotion = true))
        assertEquals(0, motionDuration(0, reduceMotion = false))
    }
}
