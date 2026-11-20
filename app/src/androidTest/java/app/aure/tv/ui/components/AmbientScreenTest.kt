@file:OptIn(ExperimentalTestApi::class)

package app.aure.tv.ui.components

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aure.tv.ambient.AmbientMode
import app.aure.tv.ambient.RedditWallpaper
import app.aure.tv.data.AerialClip
import app.aure.tv.ui.OVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AmbientScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    @Test
    fun the_clock_mode_shows_a_real_clock_and_takes_any_key() {
        var closeRequests = 0
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.CLOCK,
                    items = emptyList<AerialClip>(),
                    onCloseRequested = { closeRequests++ },
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(AMBIENT_SCREEN_TAG).assertIsDisplayed()
        rule.onNodeWithTag(AMBIENT_CLOCK_TAG, useUnmergedTree = true).assertExists()

        press(Key.DirectionDown)
        assertEquals(1, closeRequests)
    }

    @Test
    fun the_minimal_mode_has_no_clock_text() {
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.MINIMAL,
                    items = emptyList<AerialClip>(),
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(AMBIENT_SCREEN_TAG).assertIsDisplayed()
        rule.onAllNodesWithTag(AMBIENT_CLOCK_TAG).assertCountEquals(0)
    }

    @Test
    fun photos_without_a_folder_fall_back_to_the_clock() {
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.PHOTOS,
                    items = emptyList(),
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(AMBIENT_CLOCK_TAG, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(AMBIENT_PHOTO_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun kids_get_the_clock_instead_of_personal_photos() {
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.PHOTOS,
                    items = emptyList(),
                    photos = listOf(Uri.parse("content://photos/a")),
                    photosAllowed = false,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(AMBIENT_CLOCK_TAG, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(AMBIENT_PHOTO_TAG).assertCountEquals(0)
    }

    @Test
    fun photos_with_a_real_selection_use_the_photo_surface() {
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.PHOTOS,
                    items = emptyList(),
                    photos = listOf(Uri.parse("content://photos/a")),
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(AMBIENT_PHOTO_TAG, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(AMBIENT_CLOCK_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun reddit_without_a_feed_falls_back_to_the_clock() {
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.REDDIT,
                    items = emptyList(),
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(AMBIENT_CLOCK_TAG, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(AMBIENT_REDDIT_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun kids_get_the_clock_instead_of_reddit() {
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.REDDIT,
                    items = emptyList(),
                    reddit = listOf(redditItem()),
                    redditAllowed = false,
                )
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(AMBIENT_CLOCK_TAG, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag(AMBIENT_REDDIT_TAG).assertCountEquals(0)
    }

    @Test
    fun reddit_with_a_selection_shows_a_clock_until_the_first_image_arrives() {
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.REDDIT,
                    items = emptyList(),
                    reddit = listOf(redditItem()),
                )
            }
        }
        rule.waitForIdle()

        // The surface is there, but its first remote image has not loaded, so
        // the calm clock is what the viewer actually sees. It disappears once
        // RedditFace has a bitmap.
        rule.onNodeWithTag(AMBIENT_REDDIT_TAG, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(AMBIENT_CLOCK_TAG, useUnmergedTree = true).assertExists()
    }

    private fun redditItem() = RedditWallpaper(
        id = "t3_test",
        title = "Test",
        author = "tester",
        community = "TVWallpapersSFW",
        // Invalid host: the face must stay a surface without touching network.
        previewUrl = "https://example.invalid/preview.png",
        imageUrl = "https://example.invalid/image.png",
        postUrl = "https://www.reddit.com/r/TVWallpapersSFW/comments/test/",
    )

    @Test
    fun the_back_key_and_the_exit_fade_keep_their_order() {
        var closing by mutableStateOf(false)
        var closeRequests = 0
        var dismissed = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OVisionTheme {
                AmbientScreen(
                    mode = AmbientMode.CLOCK,
                    items = emptyList(),
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

        press(Key.Back)
        assertEquals(1, closeRequests)
        assertEquals(0, dismissed)

        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }
}
