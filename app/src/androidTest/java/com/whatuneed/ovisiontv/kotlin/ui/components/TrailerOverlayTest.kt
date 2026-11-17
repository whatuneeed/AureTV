@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrailerOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun ok_with_an_action_opens_the_provider_instead_of_closing_the_preview() {
        var dismissals = 0
        var actions = 0
        rule.setContent {
            OVisionTheme {
                TrailerOverlay(
                    title = "Test title",
                    url = "file:///android_asset/does-not-exist.mp4",
                    actionLabel = "OK — open in Flux",
                    onAction = { actions++ },
                    onDismiss = { dismissals++ },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(TRAILER_OVERLAY_TAG)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        assertEquals(1, actions)
        assertEquals(0, dismissals)
    }

    @Test
    fun back_closes_the_preview_instead_of_the_home_grid() {
        var dismissals = 0
        rule.setContent {
            OVisionTheme {
                TrailerOverlay(
                    title = "Test title",
                    // A deliberately unreachable file: this test is about the
                    // focus/back contract, not playback. ExoPlayer is released
                    // with the overlay either way.
                    url = "file:///android_asset/does-not-exist.mp4",
                    onDismiss = { dismissals++ },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(TRAILER_OVERLAY_TAG)
            .performKeyInput { pressKey(Key.Back) }
        rule.waitForIdle()

        assertEquals(1, dismissals)
    }
}
