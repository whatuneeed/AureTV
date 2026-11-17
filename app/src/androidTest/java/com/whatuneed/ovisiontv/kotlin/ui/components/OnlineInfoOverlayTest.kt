@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryKind
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnlineInfoOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun ok_with_a_provider_action_opens_it_instead_of_closing_the_information_overlay() {
        var dismissals = 0
        var actions = 0
        rule.setContent {
            OVisionTheme {
                OnlineInfoOverlay(
                    item = DiscoveryItem(
                        id = "justwatch|1",
                        source = DiscoverySource.ONLINE,
                        kind = DiscoveryKind.VIDEO,
                        packageName = "me",
                        packageLabel = "JustWatch",
                        title = "Test title",
                        reason = DiscoveryReason.TRENDING,
                    ),
                    actionLabel = "Смотреть в Flux",
                    onAction = { actions++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(ONLINE_INFO_OVERLAY_TAG)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        assertEquals(1, actions)
        assertEquals(0, dismissals)
    }

    @Test
    fun back_closes_the_information_overlay() {
        var dismissals = 0
        rule.setContent {
            OVisionTheme {
                OnlineInfoOverlay(
                    item = DiscoveryItem(
                        id = "justwatch|1",
                        source = DiscoverySource.ONLINE,
                        kind = DiscoveryKind.VIDEO,
                        packageName = "me",
                        packageLabel = "JustWatch",
                        title = "Test title",
                        year = "2026",
                        synopsis = "A real synopsis from the public chart.",
                        // No artwork: this test covers the dialog contract, not
                        // the network loader. A missing URL leaves the image
                        // layer empty instead of starting a request.
                        reason = DiscoveryReason.TRENDING,
                    ),
                    onDismiss = { dismissals++ },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(ONLINE_INFO_OVERLAY_TAG)
            .performKeyInput { pressKey(Key.Back) }
        rule.waitForIdle()

        assertEquals(1, dismissals)
    }
}
