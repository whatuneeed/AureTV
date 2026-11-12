@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SettingsActivity
import com.whatuneed.ovisiontv.kotlin.data.AppArtCache
import com.whatuneed.ovisiontv.kotlin.data.AppRepository
import com.whatuneed.ovisiontv.kotlin.data.drawableToImage
import com.whatuneed.ovisiontv.kotlin.ui.MemoryPressure
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import com.whatuneed.ovisiontv.kotlin.data.ShelfCard
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shelf is a full-screen carousel: the selected recommendation fills the
 * display and the dots say which one is on screen (Java hero parity). The fade
 * is keyed by art URI, so a package broadcast — which rebuilds every TvApp with
 * fresh bitmaps — cannot replay it on the poster that stayed selected (T8).
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

    private fun card(title: String, packageName: String = "test.one") = ShelfCard(
        packageName = packageName,
        title = title,
        subtitle = null,
        thumbnailUri = null,
        posterUri = null,
        intentUri = "",
        progress = null,
        engagement = 0L,
    )

    @Test
    fun the_dots_name_the_recommendation_that_is_on_screen() {
        var selected by mutableIntStateOf(0)
        val cards = listOf(card("Первый кадр"), card("Второй кадр"))
        val label = context.getString(R.string.shelf_continue)

        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Кинопоиск"),
                        cards = cards,
                        selectedCard = selected,
                        focusedCard = -1,
                        onCardActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }

        rule.onAllNodesWithText("Первый кадр").assertCountEquals(1)
        rule.onNodeWithContentDescription("$label: Первый кадр").assertExists()

        selected = 1
        rule.waitForIdle()

        rule.onAllNodesWithText("Второй кадр").assertCountEquals(1)
        rule.onAllNodesWithText("Первый кадр").assertCountEquals(0)
        rule.onNodeWithContentDescription("$label: Второй кадр").assertExists()
    }

    // UX-11: the dots report the page but are not a remote target. The old
    // clickable node could never be reached, so TalkBack promised a control
    // the D-pad could not focus.
    @Test
    fun the_dots_report_the_page_without_being_a_control() {
        val cards = listOf(card("Первый кадр"), card("Второй кадр"))
        val label = context.getString(R.string.shelf_continue)

        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Кинопоиск"),
                        cards = cards,
                        selectedCard = 0,
                        focusedCard = -1,
                        onCardActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }

        rule.onNodeWithContentDescription("$label: Первый кадр")
            .assertExists()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    // 06_TOP_SHELF §7: the page controls must say which poster is on screen.
    // Opacity alone was unreadable over bright art, so the current dot widens
    // into a capsule while the others stay round.
    @Test
    fun the_selected_page_grows_into_a_capsule() {
        var selected by mutableIntStateOf(0)
        val cards = listOf(card("Первый кадр"), card("Второй кадр"), card("Третий кадр"))

        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Кинопоиск"),
                        cards = cards,
                        selectedCard = selected,
                        focusedCard = selected,
                        onCardActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }

        fun widths(): List<Float> =
            rule.onAllNodesWithTag("shelfDot").fetchSemanticsNodes()
                .map { it.boundsInRoot.width }

        val first = widths()
        assertTrue("Three dots expected, got $first", first.size == 3)
        assertTrue("The first dot is not the widest: $first", first[0] > first[1] * 1.5f)
        assertTrue("The first dot is not the widest: $first", first[0] > first[2] * 1.5f)

        selected = 2
        rule.waitForIdle()

        val last = widths()
        assertTrue("The last dot is not the widest: $last", last[2] > last[0] * 1.5f)
        assertTrue("The last dot is not the widest: $last", last[2] > last[1] * 1.5f)
    }

    // 06_TOP_SHELF §7 / 14_MOTION_DESIGN M4: focusing the carousel makes the
    // poster the screen, so the details and page controls drop to the bottom
    // edge instead of staying on the dock line.
    @Test
    fun focusing_the_carousel_moves_details_and_page_controls_to_the_bottom() {
        var focused by mutableIntStateOf(-1)
        val cards = listOf(card("Первый кадр"), card("Второй кадр"))

        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = app("test.one", "Кинопоиск"),
                        cards = cards,
                        selectedCard = 0,
                        focusedCard = focused,
                        onCardActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }

        fun dotBottom(): Float = rule.onAllNodesWithTag("shelfDot")
            .fetchSemanticsNodes().first().boundsInRoot.bottom
        fun titleBottom(): Float = rule.onNodeWithTag("shelfInfoTitle")
            .fetchSemanticsNode().boundsInRoot.bottom

        val restingDots = dotBottom()
        val restingTitle = titleBottom()

        focused = 0
        rule.waitForIdle()

        val droppedDots = dotBottom()
        val droppedTitle = titleBottom()
        assertTrue(
            "The dots did not drop: $restingDots -> $droppedDots",
            droppedDots - restingDots > 300f,
        )
        assertTrue(
            "The details did not drop: $restingTitle -> $droppedTitle",
            droppedTitle - restingTitle > 300f,
        )
    }

    @Test
    fun rebuilding_the_focused_app_keeps_its_poster() {
        var shelf by mutableStateOf(app("test.one", "Кинопоиск"))
        val cards = listOf(card("Первый кадр"), card("Второй кадр"))

        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = shelf,
                        cards = cards,
                        selectedCard = 0,
                        focusedCard = -1,
                        onCardActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }
        rule.onAllNodesWithText("Первый кадр").assertCountEquals(1)

        // Same package, new object and bitmaps: nothing restarts.
        shelf = app("test.one", "Кинопоиск", android.graphics.Color.GREEN)
        rule.waitForIdle()

        rule.onAllNodesWithText("Первый кадр").assertCountEquals(1)
        rule.onAllNodesWithText("Второй кадр").assertCountEquals(0)
    }

    @Test
    fun a_new_app_replaces_the_shelf_underlay() {
        var shelf by mutableStateOf(app("test.one", "Первый"))

        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = shelf,
                        cards = emptyList(),
                        selectedCard = 0,
                        focusedCard = -1,
                        onCardActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }
        rule.onAllNodesWithText("Первый").assertCountEquals(1)

        shelf = app("test.two", "Второй", android.graphics.Color.BLUE)
        rule.waitForIdle()

        rule.onAllNodesWithText("Первый").assertCountEquals(0)
        rule.onAllNodesWithText("Второй").assertCountEquals(1)
    }

    // The caption comes from the launcher's locale-wrapped context, so its
    // capitals must follow that language as well: the Turkish i/İ rule applied
    // to the device locale would spell an English caption two different ways.
    @Test
    fun the_shelf_caption_uppercases_in_the_launcher_language() {
        val turkish = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("tr"))
        }
        val wrapped = context.createConfigurationContext(turkish)
        val caption = wrapped.getString(R.string.shelf_context)
        val expected = caption.uppercase(Locale.forLanguageTag("tr"))
        assertTrue(
            "The caption has no language-dependent capitals",
            expected != caption.uppercase(Locale.US),
        )

        val previous = Locale.getDefault()
        try {
            // Pin the device default: that is what the caption used before, so
            // on an already-Turkish device the bug would hide itself.
            Locale.setDefault(Locale.US)
            rule.setContent {
                OVisionTheme {
                    CompositionLocalProvider(
                        LocalContext provides wrapped,
                        LocalConfiguration provides turkish,
                    ) {
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            TopShelf(
                                app = app("test.one", "Первый"),
                                cards = emptyList(),
                                selectedCard = 0,
                                focusedCard = -1,
                                onCardActivate = {},
                                scale = TvScale(1f),
                            )
                        }
                    }
                }
            }

            rule.onAllNodesWithText(expected).assertCountEquals(1)
        } finally {
            Locale.setDefault(previous)
        }
    }

    // 06_TOP_SHELF §9: an app without a banner and without published rows gets
    // a bare backdrop — the launcher never substitutes an upscaled icon.
    @Test
    fun an_app_without_a_banner_leaves_the_shelf_empty() {
        var shelf by mutableStateOf(app("test.one", "Первый"))
        val bare = TvApp(
            packageName = "test.two",
            activityName = "test.two.MainActivity",
            label = "Второй",
            banner = null,
            icon = com.whatuneed.ovisiontv.kotlin.ui.testArtKey(
                "bare-icon",
                Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888),
            ),
        )

        rule.setContent {
            OVisionTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    TopShelf(
                        app = shelf,
                        cards = emptyList(),
                        selectedCard = 0,
                        focusedCard = -1,
                        onCardActivate = {},
                        scale = TvScale(1f),
                    )
                }
            }
        }
        rule.onAllNodesWithText("Первый").assertCountEquals(1)

        shelf = bare
        rule.waitForIdle()

        rule.onAllNodesWithText("Второй").assertCountEquals(0)
        rule.onAllNodesWithText("Первый").assertCountEquals(0)
    }
}
