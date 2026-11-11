@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.ui.components.KEYBOARD_PANEL_TAG
import com.whatuneed.ovisiontv.kotlin.ui.components.SEARCH_FIELD_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the search surface through the SEARCH key and the on-screen keyboard,
 * with fake items whose packages are not installed, so a test never starts a
 * real app. Letters are tapped through their TalkBack action: that path works
 * in both Cyrillic and Latin layouts, while arrow navigation depends on the
 * device locale.
 */
@RunWith(AndroidJUnit4::class)
class SearchTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedPrefs: Map<String, Any?>

    @Before
    fun resetFocusMemory() {
        savedPrefs = PrefsSnapshot.capture(context)
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun restoreFocusMemory() {
        PrefsSnapshot.restore(context, savedPrefs)
    }

    private fun tvApp(packageName: String, label: String) = TvApp(
        packageName = packageName,
        activityName = "$packageName.MainActivity",
        label = label,
        banner = null,
        icon = null,
    )

    private fun app(packageName: String, label: String) =
        HomeItem.AppItem(tvApp(packageName, label))

    private fun items(): List<HomeItem> = listOf(
        app("test.one", "Первый"),
        app("test.two", "Второй"),
        HomeItem.FolderItem(
            id = "test-folder",
            name = "Моя папка",
            apps = listOf(
                tvApp("test.folder.one", "В папке 1"),
                tvApp("test.folder.two", "В папке 2"),
            ),
        ),
        app("test.three", "Третий"),
    )

    private fun setHome(
        initialItems: List<HomeItem> = items(),
        onLaunch: (TvApp) -> Boolean = { true },
    ) {
        rule.setContent {
            OVisionTheme {
                HomeScreen(
                    initialItems = initialItems,
                    resumeTick = 0,
                    onLaunch = onLaunch,
                    onOrderChanged = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        rule.waitForIdle()
    }

    private fun openSearch() {
        press(Key.Search)
        rule.onNodeWithText(context.getString(R.string.search_title)).assertIsDisplayed()
    }

    // The layout starts in the device language; "абв" exists only while the
    // Latin or digit layout is showing, so tapping it switches to Cyrillic.
    private fun typeCyrillic(text: String) {
        if (rule.onAllNodesWithContentDescription("абв").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithContentDescription("абв")
                .performSemanticsAction(SemanticsActions.OnClick)
        }
        for (char in text) {
            rule.onNodeWithContentDescription(char.toString())
                .performSemanticsAction(SemanticsActions.OnClick)
        }
        rule.waitForIdle()
    }

    private fun systemBack() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstOrNull() as? ComponentActivity ?: return@runOnMainSync
            activity.onBackPressedDispatcher.onBackPressed()
        }
        rule.waitForIdle()
    }

    @Test
    fun search_key_opens_the_surface_with_the_hint() {
        setHome()
        openSearch()

        rule.onNodeWithText(context.getString(R.string.search_placeholder)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.search_hint)).assertIsDisplayed()
        // TalkBack's heading navigation needs the surface title to be a heading,
        // like every Settings page.
        rule.onNodeWithText(context.getString(R.string.search_title)).assert(isHeading())
    }

    // Cyrillic row 1 has twelve keys and is the widest row; on the narrower
    // search panel it used to be centered past both edges and clipped.
    @Test
    fun keyboard_keys_stay_inside_the_panel() {
        setHome()
        openSearch()
        if (rule.onAllNodesWithContentDescription("абв").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithContentDescription("абв")
                .performSemanticsAction(SemanticsActions.OnClick)
            rule.waitForIdle()
        }

        val panel = rule.onNodeWithTag(KEYBOARD_PANEL_TAG).getUnclippedBoundsInRoot()
        val first = rule.onNodeWithContentDescription("й").getUnclippedBoundsInRoot()
        val last = rule.onNodeWithContentDescription("ъ").getUnclippedBoundsInRoot()

        assertTrue("first key at ${first.left}, panel starts at ${panel.left}", first.left >= panel.left)
        assertTrue("last key ends at ${last.right}, panel ends at ${panel.right}", last.right <= panel.right)
    }

    @Test
    fun results_header_stays_clear_of_the_input_field() {
        setHome()
        openSearch()
        typeCyrillic("пер")

        val field = rule.onNodeWithTag(SEARCH_FIELD_TAG).getUnclippedBoundsInRoot()
        val header = rule.onNodeWithText(context.getString(R.string.search_results))
            .getUnclippedBoundsInRoot()

        assertTrue("header at ${header.left}, field ends at ${field.right}", header.left >= field.right)
    }

    @Test
    fun typing_filters_the_results_live() {
        setHome()
        openSearch()
        typeCyrillic("пер")

        rule.onNodeWithContentDescription("Первый").assertIsDisplayed()
        rule.onNodeWithContentDescription("Второй").assertDoesNotExist()
        rule.onNodeWithContentDescription("Третий").assertDoesNotExist()
    }

    @Test
    fun apps_inside_folders_are_searchable() {
        setHome()
        openSearch()
        typeCyrillic("папке")

        rule.onNodeWithContentDescription("В папке 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("В папке 2").assertIsDisplayed()
    }

    @Test
    fun no_match_shows_the_empty_message() {
        setHome()
        openSearch()
        typeCyrillic("ццц")

        rule.onNodeWithText(context.getString(R.string.search_no_results)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.search_no_results_hint))
            .assertIsDisplayed()
    }

    @Test
    fun done_moves_to_results_and_ok_launches_the_app() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })
        openSearch()
        typeCyrillic("пер")

        rule.onNodeWithContentDescription(context.getString(R.string.keyboard_done))
            .performSemanticsAction(SemanticsActions.OnClick)
        press(Key.DirectionCenter)

        assertEquals("test.one", launched)
        rule.onNodeWithText(context.getString(R.string.search_title)).assertDoesNotExist()
    }

    // The row index used to survive a query change, so the cursor could point at
    // the second match of the previous query while the list showed the new one.
    @Test
    fun a_new_query_moves_the_result_cursor_back_to_the_first_match() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })
        openSearch()
        typeCyrillic("па")

        rule.onNodeWithContentDescription(context.getString(R.string.keyboard_done))
            .performSemanticsAction(SemanticsActions.OnClick)
        press(Key.DirectionDown)

        // "пап" keeps both matches, so only the query change can tell the cursor
        // that its old row number now names another app.
        rule.onNodeWithContentDescription("п")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.keyboard_done))
            .performSemanticsAction(SemanticsActions.OnClick)
        press(Key.DirectionCenter)

        assertEquals("test.folder.one", launched)
    }

    @Test
    fun back_key_closes_the_surface_and_returns_the_grid() {
        setHome()
        openSearch()

        press(Key.Back)

        rule.onNodeWithText(context.getString(R.string.search_title)).assertDoesNotExist()
        rule.onNodeWithContentDescription("Первый").assertIsDisplayed()
    }

    // The grid re-takes focus 60 ms after the overlay leaves; without that the
    // tiles are drawn but the remote no longer reaches them.
    @Test
    fun closing_search_restores_the_remote() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })
        openSearch()
        typeCyrillic("пер")

        press(Key.Back)
        rule.mainClock.advanceTimeBy(120)
        rule.waitForIdle()

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        rule.waitForIdle()

        assertEquals("test.two", launched)
    }

    @Test
    fun system_back_closes_the_surface() {
        setHome()
        openSearch()

        systemBack()

        rule.onNodeWithText(context.getString(R.string.search_title)).assertDoesNotExist()
        rule.onNodeWithContentDescription("Первый").assertIsDisplayed()
    }

    // The grid's key handler still runs behind the overlay; MENU must not open
    // a context menu that the search surface would then cover.
    @Test
    fun menu_key_does_not_open_a_menu_behind_search() {
        setHome()
        openSearch()

        press(Key.Menu)

        rule.onNodeWithContentDescription(context.getString(R.string.menu_open))
            .assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.search_title)).assertIsDisplayed()
    }
}
