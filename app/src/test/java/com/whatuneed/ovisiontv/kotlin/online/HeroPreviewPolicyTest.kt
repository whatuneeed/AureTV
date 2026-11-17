package com.whatuneed.ovisiontv.kotlin.online

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryKind
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroPreviewPolicyTest {

    private fun item(
        source: DiscoverySource = DiscoverySource.ONLINE,
        uri: String? = "https://video-ssl.itunes.apple.com/preview.m4v",
    ) = DiscoveryItem(
        id = "apple|1",
        source = source,
        kind = DiscoveryKind.VIDEO,
        packageName = "com.whatuneed.ovisiontv.kotlin",
        title = "A title",
        intentUri = uri,
        reason = DiscoveryReason.TRENDING,
    )

    @Test
    fun anEnabledOnlineCardWithHttpsPreviewIsPlayable() {
        assertEquals(
            "https://video-ssl.itunes.apple.com/preview.m4v",
            onlinePreviewUri(item(), enabled = true),
        )
    }

    @Test
    fun theUserSettingStopsThePreview() {
        assertNull(onlinePreviewUri(item(), enabled = false))
    }

    @Test
    fun aLocalAppCardNeverGetsAHeroPreview() {
        assertNull(onlinePreviewUri(item(source = DiscoverySource.PACKAGE_MANAGER), enabled = true))
    }

    @Test
    fun cleartextOrMissingPreviewIsNeverPlayed() {
        assertNull(onlinePreviewUri(item(uri = "http://video-ssl.itunes.apple.com/preview.m4v"), enabled = true))
        assertNull(onlinePreviewUri(item(uri = null), enabled = true))
    }
}
