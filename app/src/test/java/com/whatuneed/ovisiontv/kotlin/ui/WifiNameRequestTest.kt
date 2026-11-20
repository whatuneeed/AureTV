package com.whatuneed.ovisiontv.kotlin.ui

import com.whatuneed.ovisiontv.kotlin.data.NetworkKind
import com.whatuneed.ovisiontv.kotlin.ui.components.shouldAskForWifiName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A denied Wi-Fi name grant must not turn the Quick Settings tile into a dead
 * button: only the first press asks, later presses open Wi-Fi settings.
 */
class WifiNameRequestTest {

    @Test
    fun asksOnceOnWifiWithoutTheGrant() {
        assertTrue(shouldAskForWifiName(NetworkKind.WIFI, granted = false, asked = false))
    }

    @Test
    fun doesNotAskAgainAfterADenial() {
        assertFalse(shouldAskForWifiName(NetworkKind.WIFI, granted = false, asked = true))
    }

    @Test
    fun doesNotAskWhenTheNameIsAlreadyVisible() {
        assertFalse(shouldAskForWifiName(NetworkKind.WIFI, granted = true, asked = false))
    }

    @Test
    fun otherNetworksNeverAsk() {
        listOf(NetworkKind.ETHERNET, NetworkKind.MOBILE, NetworkKind.OTHER, NetworkKind.NONE)
            .forEach { assertFalse(shouldAskForWifiName(it, granted = false, asked = false)) }
    }
}
