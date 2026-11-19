package com.whatuneed.ovisiontv.kotlin.data

import com.whatuneed.ovisiontv.kotlin.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every network client identifies the build that actually sent the request. */
class LauncherUserAgentTest {

    @Test
    fun userAgentCarriesTheCurrentVersionName() {
        assertTrue(LauncherUserAgent.value.contains("/${BuildConfig.VERSION_NAME} "))
        assertTrue(LauncherUserAgent.value.startsWith("OVisionTV/"))
        assertTrue(LauncherUserAgent.value.contains("Android TV launcher"))
    }

    @Test
    fun userAgentDoesNotCarryAStaleHardCodedVersion() {
        assertFalse(LauncherUserAgent.value.contains("/0.5.7 "))
    }
}
