package app.aure.tv.online

import app.aure.tv.discovery.DiscoveryItem
import app.aure.tv.discovery.DiscoveryKind
import app.aure.tv.discovery.DiscoveryReason
import app.aure.tv.discovery.DiscoverySource
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
        packageName = "app.aure.tv",
        title = "A title",
        trailerUri = uri,
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
    fun aProviderRowWithAMatchedPreviewCanDriveTheHeroToo() {
        assertEquals(
            "https://video-ssl.itunes.apple.com/preview.m4v",
            onlinePreviewUri(
                item(source = DiscoverySource.TV_PROVIDER),
                enabled = true,
            ),
        )
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
