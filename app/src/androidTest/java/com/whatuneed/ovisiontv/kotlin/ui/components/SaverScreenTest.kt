@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaverScreenTest {

    @get:Rule
    val rule = createComposeRule()

    // No clips: the overlay must still own the remote and dismiss on any key.
    private val noClips = emptyList<AerialClip>()

    private fun setSaver(onCloseRequested: () -> Unit = {}) {
        rule.setContent {
            OVisionTheme {
                SaverScreen(items = noClips, onCloseRequested = onCloseRequested)
            }
        }
        // The saver owns the remote from its first frame, so the tests below
        // send keys without advancing the clock past the appear fade.
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    @Test
    fun the_saver_fills_the_screen() {
        setSaver()
        rule.onNodeWithTag(SAVER_SCREEN_TAG).assertIsDisplayed()
    }

    @Test
    fun any_remote_direction_asks_the_saver_to_close() {
        var closeRequests = 0
        setSaver { closeRequests++ }
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        assertEquals(2, closeRequests)
    }

    @Test
    fun back_asks_the_saver_to_close_without_looking_for_another_surface() {
        var closeRequests = 0
        setSaver { closeRequests++ }
        press(Key.Back)
        assertEquals(1, closeRequests)
    }

    @Test
    fun the_saver_takes_the_remote_from_the_focused_grid_immediately() {
        var closeRequests = 0
        var gridKeys = 0
        // Keep the clock frozen: the key must reach the overlay without any
        // frame budget, as it would right after the saver appears.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize()) {
                    val gridFocus = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(gridFocus)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) gridKeys++
                                true
                            }
                            .focusable(),
                    )
                    LaunchedEffect(Unit) { gridFocus.requestFocus() }
                    SaverScreen(items = noClips, onCloseRequested = { closeRequests++ })
                }
            }
        }
        rule.waitForIdle()
        press(Key.DirectionRight)
        assertEquals(1, closeRequests)
        assertEquals(0, gridKeys)
    }

    @Test
    fun the_saver_is_not_removed_before_its_exit_finishes() {
        var closing by mutableStateOf(false)
        var closeRequests = 0
        var dismissed = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OVisionTheme {
                SaverScreen(
                    items = noClips,
                    closing = closing,
                    onCloseRequested = {
                        closeRequests++
                        closing = true
                    },
                    onDismissed = { dismissed++ },
                )
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput {
            keyDown(Key.Back)
            keyUp(Key.Back)
        }
        assertEquals(1, closeRequests)
        assertEquals(0, dismissed)
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun a_saver_that_never_gets_a_picture_leaves_instead_of_owning_the_remote_invisibly() {
        var closeRequests = 0
        val clip = AerialClip(
            id = "missing",
            nameEn = "Missing clip",
            nameRu = "Недоступный ролик",
            theme = SaverTheme.CITIES,
            videoUrl = "https://127.0.0.1:1/missing.mov",
            previewUrl = "",
            order = 0,
        )
        rule.setContent {
            OVisionTheme {
                SaverScreen(
                    items = listOf(clip),
                    onCloseRequested = { closeRequests++ },
                    noPictureTimeoutMs = 0,
                )
            }
        }
        rule.waitForIdle()
        assertEquals(1, closeRequests)
    }

    @Test
    fun the_saver_does_not_paint_its_background_before_it_has_a_picture() {
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Red)) {
                    SaverScreen(items = noClips)
                }
            }
        }
        rule.waitForIdle()
        // No picture is coming for an empty playlist, so the entrance never
        // starts: the overlay has to stay fully transparent instead of
        // covering Home with the dark saver surface. A plain background()
        // outside the alpha layer would paint #0B0E12 from the first frame.
        val pixels = rule.onNodeWithTag(SAVER_SCREEN_TAG).captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertTrue(
            "expected Home through the idle saver, got ${center.red},${center.green},${center.blue}",
            center.red > 0.8f && center.green < 0.2f && center.blue < 0.2f,
        )
    }

    @Test
    fun a_real_still_keeps_the_saver_on_screen_even_when_the_clip_cannot_play() {
        var closeRequests = 0
        val clip = AerialClip(
            id = "with-still",
            nameEn = "Still clip",
            nameRu = "Ролик со стиллом",
            theme = SaverTheme.CITIES,
            videoUrl = "https://127.0.0.1:1/missing.mov",
            previewUrl = "",
            order = 0,
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The store rejects black frames (they came from clip lead-ins), so
        // the still has to look like a real aerial picture.
        val still = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        still.eraseColor(0xFF4A6B8A.toInt())
        runBlocking {
            AerialPosterStore.save(context, clip.id, still)
        }
        rule.setContent {
            OVisionTheme {
                SaverScreen(
                    items = listOf(clip),
                    onCloseRequested = { closeRequests++ },
                    noPictureTimeoutMs = 0,
                )
            }
        }
        rule.waitForIdle()
        assertEquals(0, closeRequests)
    }

    @Test
    fun a_clip_that_cannot_play_keeps_the_saver_usable() {
        var closeRequests = 0
        val clip = AerialClip(
            id = "missing",
            nameEn = "Missing clip",
            nameRu = "Недоступный ролик",
            theme = SaverTheme.CITIES,
            // Nothing listens on port 1, so playback fails at once and the
            // saver has to stay alive on the real still instead of artwork.
            videoUrl = "https://127.0.0.1:1/missing.mov",
            previewUrl = "",
            order = 0,
        )
        rule.setContent {
            OVisionTheme {
                SaverScreen(
                    items = listOf(clip),
                    onCloseRequested = { closeRequests++ },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(SAVER_SCREEN_TAG).assertIsDisplayed()
        press(Key.Back)
        assertEquals(1, closeRequests)
    }
}
