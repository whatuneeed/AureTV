package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityStatusTest {

    @Test
    fun connectedServiceWinsOverTheStoredSwitch() {
        // The service can be connected while the secure list still has to be
        // re-read; a live connection is the stronger fact.
        assertEquals(
            AccessibilityStatus.State.CONNECTED,
            AccessibilityStatus.state(connected = true, enabledInSettings = false),
        )
        assertEquals(
            AccessibilityStatus.State.CONNECTED,
            AccessibilityStatus.state(connected = true, enabledInSettings = true),
        )
    }

    @Test
    fun enabledButStoppedIsItsOwnState() {
        // Android keeps the switch listed after a force-stop, but the service
        // is not bound: the user must toggle it off and on.
        assertEquals(
            AccessibilityStatus.State.STALE,
            AccessibilityStatus.state(connected = false, enabledInSettings = true),
        )
        assertEquals(
            AccessibilityStatus.State.OFF,
            AccessibilityStatus.state(connected = false, enabledInSettings = false),
        )
    }
}
