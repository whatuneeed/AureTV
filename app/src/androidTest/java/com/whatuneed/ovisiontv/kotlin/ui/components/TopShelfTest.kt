@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SettingsActivity
import com.whatuneed.ovisiontv.kotlin.data.AppArtCache
import com.whatuneed.ovisiontv.kotlin.data.AppRepository
import com.whatuneed.ovisiontv.kotlin.data.drawableToImage
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryKind
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryRail
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySection
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.ui.MemoryPressure
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * The shelf is one hero behind a rail: the section bar says which row is on
 * screen, the focused card is the hero, and every card is reachable by TalkBack
 * even though the remote is handled by HomeScreen.
 */
@RunWith(AndroidJUnit4::class)
class TopShelfTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun background_trim_restores_the_settings_activity_icon() {
        val own = runBlocking { AppRepository(context).load() }
            .single { it.packageName == context.packageName }
        val key = requireNotNull(own.icon)
        val expected = requireNotNull(drawableToImage(
            context.packageManager.getActivityInfo(
                ComponentName(context, SettingsActivity::class.java), 0,
            ).loadIcon(context.packageManager), 256, 256,
        )).asAndroidBitmap()
        val rendered = AtomicReference<ImageBitmap?>()
        rule.setContent {
            val art = rememberArt(key)
            SideEffect { rendered.set(art) }
        }
        rule.waitUntil(5_000) { rendered.get() != null }
        val before = requireNotNull(rendered.get())
        assertTrue(before.asAndroidBitmap().sameAs(expected))
        rule.runOnIdle {
            MemoryPressure.trim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
            assertNull(AppArtCache.get(key))
        }
        rule.waitUntil(5_000) { rendered.get()?.let { it !== before } == true }
        rule.runOnIdle {
            val restored = requireNotNull(rendered.get())
            assertSame(AppArtCache.get(key), restored)
            assertTrue(restored.asAndroidBitmap().sameAs(expected))
        }
    }

    private fun app(
        packageName: String,
        label: String,
        color: Int = android.graphics.Color.RED,
    ) = TvApp(
        packageName = packageName,
        activityName = "$packageName.MainActivity",
        label = label,
        banner = com.whatuneed.ovisiontv.kotlin.ui.testArtKey(
            "$packageName|$label",
            Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888).apply { eraseColor(color) },
        ),
        icon = null,
    )

    private fun card(title: String, packageName: String = "test.one") = DiscoveryItem(
        id = "tv|$packageName|$title",
        source = DiscoverySource.TV_PROVIDER,
        kind = DiscoveryKind.VIDEO,
        packageName = packageName,
        title = title,
        intentUri = "intent://$title#Intent;package=$packageName;end",
    )

    private fun rails(vararg entries: Pair<DiscoverySection, List<DiscoveryItem>>) =
        entries.map { (section, items) -> DiscoveryRail(section, items) }

    @Test
    fun the_section_bar_names_the_selected_rail() {
        val section = rails(
            DiscoverySection.CONTINUE to listOf(card("Первый кадр")),
            DiscoverySection.FOR_YOU to listOf(card("Второй кадр")),
        )
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Кинопоиск"),
                        rails = section,
                        selectedSection = DiscoverySection.CONTINUE,
                        selectedCard = 0,
                        focusedCard = 0,
                        sectionFocused = false,
                        onCardActivate = {},
                        onSectionActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }

        // The selected section is named by its bar and by the rail above the
        // cards; the unselected section is named once, by its tab.
        rule.onAllNodesWithText(context.getString(R.string.discovery_section_continue))
            .assertCountEquals(2)
        rule.onAllNodesWithText(context.getString(R.string.discovery_section_for_you))
            .assertCountEquals(1)
        rule.onAllNodesWithContentDescription("Первый кадр").assertCountEquals(1)
    }

    @Test
    fun the_browse_home_does_not_draw_the_rail_under_the_dock() {
        val section = rails(
            DiscoverySection.CONTINUE to listOf(card("Первый кадр")),
            DiscoverySection.FOR_YOU to listOf(card("Второй кадр")),
        )
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Кинопоиск"),
                        rails = section,
                        selectedSection = DiscoverySection.CONTINUE,
                        selectedCard = 0,
                        focusedCard = -1,
                        sectionFocused = false,
                        onCardActivate = {},
                        onSectionActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }

        // Browse is just the poster, its title and the dock. The rail appears
        // only after Up enters the shelf, when HomeScreen has moved the grid.
        rule.onAllNodesWithContentDescription("Первый кадр").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("Второй кадр").assertCountEquals(0)
        rule.onAllNodesWithText(context.getString(R.string.discovery_section_continue))
            .assertCountEquals(0)
    }

    @Test
    fun the_focused_card_is_the_real_android_focus_target() {
        val section = rails(
            DiscoverySection.CONTINUE to listOf(card("Первый кадр"), card("Второй кадр")),
        )
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Кинопоиск"),
                        rails = section,
                        selectedSection = DiscoverySection.CONTINUE,
                        selectedCard = 0,
                        focusedCard = 0,
                        sectionFocused = false,
                        onCardActivate = {},
                        onSectionActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Первый кадр").assertIsFocused()
    }

    @Test
    fun the_selected_section_pill_is_the_real_android_focus_target() {
        val section = rails(
            DiscoverySection.CONTINUE to listOf(card("Первый кадр")),
            DiscoverySection.FOR_YOU to listOf(card("Второй кадр")),
        )
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Кинопоиск"),
                        rails = section,
                        selectedSection = DiscoverySection.CONTINUE,
                        selectedCard = 0,
                        focusedCard = -1,
                        sectionFocused = true,
                        onCardActivate = {},
                        onSectionActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithContentDescription(
            context.getString(R.string.discovery_section_continue),
        ).assertIsFocused()
    }

    @Test
    fun cards_are_named_for_talkback() {
        val section = rails(
            DiscoverySection.FOR_YOU to listOf(card("Первый кадр"), card("Второй кадр")),
        )
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = null,
                        rails = section,
                        selectedSection = DiscoverySection.FOR_YOU,
                        selectedCard = 0,
                        focusedCard = 0,
                        sectionFocused = false,
                        onCardActivate = {},
                        onSectionActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }

        rule.onNodeWithContentDescription("Первый кадр").assertExists()
        rule.onNodeWithContentDescription("Второй кадр").assertExists()
    }

    @Test
    fun a_shelf_without_rails_keeps_the_app_banner() {
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Первый"),
                        rails = emptyList(),
                        selectedSection = null,
                        selectedCard = 0,
                        focusedCard = -1,
                        sectionFocused = false,
                        onCardActivate = {},
                        onSectionActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }
        rule.onAllNodesWithText("Первый").assertCountEquals(1)
    }

    @Test
    fun rebuilding_the_focused_app_keeps_its_card() {
        var shelf by mutableStateOf(app("test.one", "Кинопоиск"))
        val section = rails(
            DiscoverySection.FROM_APP to listOf(card("Первый кадр"), card("Второй кадр")),
        )

        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = shelf,
                        rails = section,
                        selectedSection = DiscoverySection.FROM_APP,
                        selectedCard = 0,
                        focusedCard = 0,
                        sectionFocused = false,
                        onCardActivate = {},
                        onSectionActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }
        rule.onAllNodesWithContentDescription("Первый кадр").assertCountEquals(1)

        // Same package, new object and bitmaps: nothing restarts.
        shelf = app("test.one", "Кинопоиск", android.graphics.Color.GREEN)
        rule.waitForIdle()

        rule.onAllNodesWithContentDescription("Первый кадр").assertCountEquals(1)
        rule.onAllNodesWithContentDescription("Второй кадр").assertCountEquals(1)
    }

    @Test
    fun the_heading_names_the_selected_card_without_an_app_category() {
        val section = rails(
            DiscoverySection.FROM_APP to listOf(card("Первый кадр")),
        )
        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Первый"),
                        rails = section,
                        selectedSection = DiscoverySection.FROM_APP,
                        selectedCard = 0,
                        focusedCard = 0,
                        sectionFocused = false,
                        onCardActivate = {},
                        onSectionActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }

        // The heading and the fallback card both carry the same title; the
        // point is that the heading is the content title, not the app category.
        rule.onAllNodesWithText("Первый кадр").assertCountEquals(2)
        rule.onAllNodesWithText(context.getString(R.string.shelf_context)).assertCountEquals(0)
    }
}
