@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whatuneed.ovisiontv.kotlin.ambient.RedditWallpaper
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The catalog is the only screen where an individual image can be chosen. It
 * must work with a D-pad: one focus cursor, no hidden touch requirement.
 */
@RunWith(AndroidJUnit4::class)
class RedditCatalogOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private fun item(id: String) = RedditWallpaper(
        id = id,
        title = "Title $id",
        author = "author",
        community = "TVWallpapersSFW",
        // Rejected before any socket is opened: the host is not Reddit.
        previewUrl = "https://example.invalid/$id.png",
        imageUrl = "https://example.invalid/$id.png",
        postUrl = "https://www.reddit.com/r/TVWallpapersSFW/comments/$id/",
    )

    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    @Test
    fun the_first_tile_is_at_the_cursor_and_center_toggles_it() {
        var selected by mutableStateOf<Set<String>>(emptySet())
        rule.setContent {
            OVisionTheme {
                RedditCatalogOverlay(
                    items = listOf(item("a"), item("b"), item("c")),
                    selected = selected,
                    loading = false,
                    onToggle = {
                        selected = if (it.id in selected) selected - it.id else selected + it.id
                    },
                    onClose = {},
                )
            }
        }
        rule.waitForIdle()

        rule.onAllNodesWithTag(REDDIT_TILE_TAG)[0].assertExists()
        press(Key.DirectionCenter)
        rule.runOnIdle { assertEquals(setOf("a"), selected) }
        press(Key.DirectionCenter)
        rule.runOnIdle { assertTrue(selected.isEmpty()) }
    }

    @Test
    fun arrows_move_between_rows_and_columns() {
        var selected by mutableStateOf<Set<String>>(emptySet())
        rule.setContent {
            OVisionTheme {
                RedditCatalogOverlay(
                    items = List(7) { item("i$it") },
                    selected = selected,
                    loading = false,
                    onToggle = { selected = selected + it.id },
                    onClose = {},
                )
            }
        }
        rule.waitForIdle()

        // The selected id proves which tile the cursor was on after every move.
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        rule.runOnIdle { assertEquals(setOf("i1"), selected) }
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        rule.runOnIdle { assertEquals(setOf("i1", "i4"), selected) }
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        rule.runOnIdle { assertEquals(setOf("i1", "i4", "i3"), selected) }
    }

    @Test
    fun back_closes_the_catalog_even_when_the_feed_is_empty() {
        var closed = 0
        rule.setContent {
            OVisionTheme {
                RedditCatalogOverlay(
                    items = emptyList(),
                    selected = emptySet(),
                    loading = false,
                    onToggle = {},
                    onClose = { closed++ },
                )
            }
        }
        rule.waitForIdle()

        press(Key.Back)
        rule.runOnIdle { assertEquals(1, closed) }
    }

    @Test
    fun back_closes_the_catalog() {
        var closed = 0
        rule.setContent {
            OVisionTheme {
                RedditCatalogOverlay(
                    items = listOf(item("a")),
                    selected = emptySet(),
                    loading = false,
                    onToggle = {},
                    onClose = { closed++ },
                )
            }
        }
        rule.waitForIdle()

        press(Key.Back)
        rule.runOnIdle { assertEquals(1, closed) }
    }
}
