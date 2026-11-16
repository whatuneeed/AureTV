@file:OptIn(ExperimentalTestApi::class)

package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.HIDDEN_FOLDER_ID
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

/**
 * Drives the home grid with fake items, so a test never starts a real app:
 * every tile is either a folder or belongs to a package that is not installed.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

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
        resumeTick: MutableState<Int> = mutableStateOf(0),
        onOrderChanged: (List<HomeItem>) -> Unit = {},
        kidsMode: Boolean = false,
    ) {
        rule.setContent {
            OVisionTheme {
                HomeScreen(
                    initialItems = initialItems,
                    resumeTick = resumeTick.value,
                    onLaunch = onLaunch,
                    onOrderChanged = onOrderChanged,
                    kidsMode = kidsMode,
                )
            }
        }
        // Focus is requested after 80 ms; move the test clock to that frame.
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
    }

    private fun press(key: Key) {
        rule.onRoot().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
    }

    // Synthetic key events do not reach OnBackPressedDispatcher, so edit mode
    // (which only listens to BackHandler) needs the framework back path.
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
    fun app_names_are_drawn_under_the_tiles() {
        setHome()
        rule.waitForIdle()

        rule.onAllNodesWithTag("tile_label", useUnmergedTree = true)
            .assertCountEquals(4)
    }

    @Test
    fun a_home_without_recommendations_is_not_a_black_field() {
        setHome()
        rule.waitForIdle()

        rule.onNodeWithText(context.getString(R.string.home_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.home_empty_body)).assertIsDisplayed()
    }

    @Test
    fun every_tile_is_a_single_button_node() {
        setHome()

        for (label in listOf("Первый", "Второй", "Моя папка", "Третий")) {
            rule.onNodeWithContentDescription(label)
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    @Test
    fun right_twice_then_center_opens_the_folder_and_back_closes_it() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })
        rule.onNodeWithContentDescription("В папке 1").assertDoesNotExist()

        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        rule.waitForIdle()

        rule.onNodeWithContentDescription("В папке 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("В папке 2").assertIsDisplayed()

        press(Key.Back)
        rule.waitForIdle()

        rule.onNodeWithContentDescription("В папке 1").assertDoesNotExist()
        rule.onNodeWithContentDescription("Моя папка").assertIsDisplayed()

        // Closing removes the focused panel node; the grid must still take key
        // input afterwards, so Left then OK has to launch tile 1.
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        rule.waitForIdle()
        assertEquals("test.two", launched)
    }

    // UX-09: a folder reopens on the cell it was left on instead of jumping
    // back to its first app.
    @Test
    fun reopening_a_folder_restores_the_cell_it_was_left_on() {
        setHome()

        repeat(2) { press(Key.DirectionRight) }
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 1").assertIsFocused()

        press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 2").assertIsFocused()

        press(Key.Back)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 2").assertDoesNotExist()

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 2").assertIsFocused()
    }

    @Test
    fun left_returns_to_the_previous_tile() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })

        repeat(2) { press(Key.DirectionRight) }
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        rule.waitForIdle()

        // Focus started on tile 0, two rights put it on the folder (2) and one
        // left on tile 1; OK must launch that app, not open the folder.
        assertEquals("test.two", launched)
        rule.onNodeWithContentDescription("В папке 1").assertDoesNotExist()
    }

    // Starting real content is off limits on the emulator, so the zoom is checked
    // as pixels: a solid banner stands in for the app window, and after 0.5 s it
    // has to cover every corner of the screen-sized layer.
    @Test
    fun launch_zoom_fills_the_screen_from_the_focused_tile() {
        val bitmap = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        setHome(
            initialItems = listOf(
                HomeItem.AppItem(
                    TvApp(
                        packageName = "test.one",
                        activityName = "test.one.MainActivity",
                        label = "Первый",
                        banner = testArtKey("launch", bitmap),
                        icon = null,
                    ),
                ),
            ),
        )

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        val image = rule.onRoot().captureToImage().toPixelMap()
        val corners = listOf(
            2 to 2,
            image.width - 3 to 2,
            2 to image.height - 3,
            image.width - 3 to image.height - 3,
        )
        for ((x, y) in corners) {
            val pixel = image[x, y]
            assertTrue(
                "corner $x,$y is ${pixel.red},${pixel.green},${pixel.blue}, not the banner",
                pixel.red > 0.8f && pixel.green < 0.25f && pixel.blue < 0.25f,
            )
        }
    }

    // The focused tile catches a light that grows with it (Stage 9). The light is
    // recorded once at full strength and only its layer alpha follows the growth,
    // so a settled unfocused tile must stay dark: a fixed alpha would burn the
    // highlight into every tile and this check would see no difference.
    @Test
    fun focus_light_grows_with_the_tile_and_is_off_when_unfocused() {
        setHome(initialItems = listOf(app("test.one", "Первый"), app("test.two", "Второй")))
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        val focused = rule.onRoot().captureToImage().toPixelMap()

        press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        val unfocused = rule.onRoot().captureToImage().toPixelMap()

        // The tile is 250×150 points; the edge light's top band is 16 % high.
        // The point sits 3 pt below the top edge and 60 pt from the corner, so the
        // focused tile's 1.224 growth still covers it and the rim/AA stay outside.
        val bounds = rule.onNodeWithContentDescription("Первый").fetchSemanticsNode().boundsInRoot
        val pxPerPoint = bounds.width / TvOs.ICON_WIDTH
        val x = (bounds.left + 60f * pxPerPoint).toInt()
        val y = (bounds.top + 3f * pxPerPoint).toInt()
        val lit = averageLuminance(focused, x, y)
        val dark = averageLuminance(unfocused, x, y)

        assertTrue(
            "tile light: focused $lit vs unfocused $dark at $x,$y",
            lit - dark > 0.025f,
        )
    }

    // White artwork hides the white rim completely. A dark contour sits under it,
    // so the focused tile still has a visible boundary on light banners while the
    // white rim keeps doing its job on dark ones.
    @Test
    fun focus_rim_stays_readable_on_white_artwork() {
        val white = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        white.eraseColor(android.graphics.Color.WHITE)
        setHome(
            initialItems = listOf(
                HomeItem.AppItem(
                    TvApp(
                        packageName = "test.white",
                        activityName = "test.white.MainActivity",
                        label = "Белый",
                        banner = testArtKey("white", white),
                        icon = null,
                    ),
                ),
            ),
        )
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        val image = rule.onRoot().captureToImage().toPixelMap()
        val bounds = rule.onNodeWithContentDescription("Белый").fetchSemanticsNode().boundsInRoot
        val pxPerPoint = bounds.width / TvOs.ICON_WIDTH
        val x = (bounds.left + bounds.width / 2f).toInt()
        // Semantics keeps the 250×150 layout box while the focused tile grows to
        // 306×184 around its centre, so the visible top edge sits above bounds.top.
        val visibleTop = bounds.top - (TvOs.FOCUS_SCALE - 1f) * bounds.height / 2f
        // Sample inside the stroke only. The centre of every 3×3 patch is ceiled so
        // the patch cannot straddle the tile edge and read the dark shelf behind
        // it; past ~4 pt the white banner begins.
        val edge = listOf(1.5f, 2.5f, 3.5f).minOf { offset ->
            averageLuminance(image, x, ceil(visibleTop + offset * pxPerPoint).toInt())
        }
        val center = averageLuminance(image, x, (bounds.top + bounds.height / 2f).toInt())

        assertTrue(
            "rim on white: edge $edge vs centre $center, visibleTop $visibleTop",
            edge < center - 0.08f,
        )
    }

    // A folder is not just a caption: TalkBack announces how many apps it holds.
    @Test
    fun a_folder_announces_how_many_apps_it_holds() {
        setHome()

        val count = context.resources.getQuantityString(R.plurals.folder_app_count, 2, 2)
        rule.onNodeWithContentDescription("Моя папка")
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, count),
            )
    }

    private fun averageLuminance(pixels: PixelMap, x: Int, y: Int): Float {
        var sum = 0f
        var count = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                val px = x + dx
                val py = y + dy
                if (px in 0 until pixels.width && py in 0 until pixels.height) {
                    sum += luminance(pixels[px, py])
                    count++
                }
            }
        }
        return sum / count
    }

    private fun luminance(pixel: Color): Float =
        0.2126f * pixel.red + 0.7152f * pixel.green + 0.0722f * pixel.blue

    // The return path has no visible app window in a test, so the resume tick is
    // what tells HomeScreen the app was left. The overlay and the TalkBack gate
    // share the `launching` state, so a restored tile node means the card is gone
    // and the remote is live again.
    @Test
    fun return_from_an_app_restores_the_grid_and_the_remote() {
        val tick = mutableStateOf(0)
        var launched: String? = null
        setHome(
            initialItems = listOf(app("test.one", "Первый"), app("test.two", "Второй")),
            onLaunch = { launched = it.packageName; true },
            resumeTick = tick,
        )

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertEquals("test.one", launched)
        rule.onNodeWithContentDescription("Первый").assertDoesNotExist()

        tick.value = 1
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Первый").assertIsDisplayed()

        // The grid takes input again: RIGHT moves focus and OK opens the next app.
        launched = null
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        // The app is started 380 ms into the zoom, so the callback needs the clock.
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertEquals("test.two", launched)
    }

    @Test
    fun moving_while_ok_is_held_does_not_launch_the_next_tile() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })

        // The press started on tile 0; the direction key moves focus while OK is
        // still down, so the release must not open the tile that received focus.
        rule.onRoot().performKeyInput {
            keyDown(Key.DirectionCenter)
            keyDown(Key.DirectionRight)
            keyUp(Key.DirectionRight)
            keyUp(Key.DirectionCenter)
        }
        rule.waitForIdle()

        assertNull(launched)
    }

    @Test
    fun talkback_cannot_rename_a_folder_while_the_app_is_launching() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })

        repeat(2) { press(Key.DirectionRight) }
        press(Key.DirectionCenter)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 1")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("test.folder.one", launched)

        // The panel is still on screen behind the launch zoom, but its title must
        // not open the rename keyboard over a modal transition.
        rule.onNodeWithContentDescription("Моя папка")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.keyboard_space))
            .assertDoesNotExist()
    }

    // tvOS keeps the icon on the cursor after the lift press, so one tap picks
    // it up and the arrows carry it; a second tap drops it where it stands.
    @Test
    fun a_lifted_tile_stays_up_after_release_and_moves_with_the_arrows() {
        var order: List<HomeItem>? = null
        setHome(onOrderChanged = { order = it })

        enterEditMode()

        press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        // Dropping commits the order the drag already produced.
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        val saved = order ?: error("the new order was not saved")
        assertEquals(listOf("test.two", "test.one", "folder:test-folder", "test.three"),
            saved.map { it.key })
        rule.onNodeWithContentDescription("Первый").assertIsDisplayed()
    }

    @Test
    fun back_during_a_drag_restores_the_order_and_the_cursor() {
        var order: List<HomeItem>? = null
        setHome(onOrderChanged = { order = it })

        enterEditMode()
        press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        press(Key.Back)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        // Nothing was committed, and focus is back on the tile that was lifted.
        assertNull(order)
        rule.onNodeWithContentDescription("Первый").assertIsFocused()
        rule.onNodeWithContentDescription("Второй").assertIsDisplayed()
    }

    @Test
    fun holding_ok_over_a_neighbour_folds_both_apps_into_a_folder() {
        setHome()

        enterEditMode()
        // Carry the lifted app onto the second one.
        press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        // A second OK press starts the fold timer; 750 ms hover plus the 300 ms
        // confirmation fold the dragged app with the neighbour it pushed aside.
        rule.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        rule.mainClock.advanceTimeBy(1_200)
        rule.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Первый").assertDoesNotExist()
        rule.onNodeWithContentDescription("Второй").assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.folder_default))
            .assertIsDisplayed()

        // Leave edit mode, then open the new folder: both apps are inside.
        systemBack()
        press(Key.DirectionCenter)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Первый").assertIsDisplayed()
        rule.onNodeWithContentDescription("Второй").assertIsDisplayed()
    }

    @Test
    fun an_auto_repeating_ok_opens_the_menu_without_taking_a_row() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })

        // A remote starts repeating after ~0.5 s, and some boxes send every
        // repeat as its own press and release. The hold opens the menu on the
        // initial press; the pairs that follow must neither pick the focused
        // row nor launch the tile. OK works again only after the release.
        val start = SystemClock.uptimeMillis()
        dispatchRawKey(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER, eventTime = start)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_move)).assertIsDisplayed()

        var offset = 520L
        repeat(20) {
            dispatchRawKey(
                AndroidKeyEvent.ACTION_DOWN,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                eventTime = start + offset,
            )
            rule.mainClock.advanceTimeBy(40)
            dispatchRawKey(
                AndroidKeyEvent.ACTION_UP,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                eventTime = start + offset + 20,
            )
            rule.mainClock.advanceTimeBy(40)
            offset += 80
        }
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()

        assertNull(launched)
        rule.onNodeWithContentDescription(context.getString(R.string.menu_move))
            .assertIsDisplayed()

        // The release armed the panel, so the next OK opens the focused row.
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertEquals("test.one", launched)
    }

    // 04_APP_LIBRARY: DOWN from the dock lifts the whole grid to the top and
    // BACK puts the dock back. Third-party content is never started here.
    @Test
    fun down_opens_the_library_and_back_returns_to_the_dock() {
        setHome(initialItems = (1..12).map { app("test.$it", "Приложение $it") })
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        val dockTop = rule.onNodeWithContentDescription("Приложение 1")
            .fetchSemanticsNode().boundsInRoot.top

        press(Key.DirectionDown)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        val libraryTop = rule.onNodeWithContentDescription("Приложение 1")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("dock $dockTop -> library $libraryTop", libraryTop < dockTop - 100f)

        press(Key.Back)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        val backTop = rule.onNodeWithContentDescription("Приложение 1")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("back to dock, top is $backTop", kotlin.math.abs(backTop - dockTop) < 1f)
    }

    private fun enterEditMode() {
        // A long OK opens the context menu; "Move" (the second row) lifts the
        // tile, and the arrows carry it while it stays up.
        rule.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        rule.mainClock.advanceTimeBy(400)
        rule.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        rule.mainClock.advanceTimeBy(200)
        rule.waitForIdle()
        press(Key.DirectionDown)
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        press(Key.DirectionCenter)
        // The panel fades out before the action runs.
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
    }

    @Test
    fun talkback_focus_follows_the_picked_tile_while_it_moves() {
        setHome()

        // The context menu lifts the first tile; while it is up the drag proxy
        // replaces the cell and owns the semantics focus.
        enterEditMode()

        // TalkBack reads this focus, not the engine cursor: the proxy must own it.
        rule.onNodeWithContentDescription("Первый").assertIsFocused()

        // Moving over another tile must not hand the spoken focus to it.
        press(Key.DirectionRight)
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Первый").assertIsFocused()
        rule.onNodeWithContentDescription("Второй").assertIsNotFocused()
    }

    @Test
    fun talkback_click_opens_folder_and_launches_inside() {
        var launched: String? = null
        setHome(onLaunch = { launched = it.packageName; true })

        // TalkBack invokes the semantics OnClick action on a double-tap;
        // performClick would inject a touch event, which this key-driven grid
        // never handles.
        rule.onNodeWithContentDescription("Моя папка")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 1").assertIsDisplayed()

        rule.onNodeWithContentDescription("В папке 2")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals("test.folder.two", launched)
    }

    @Test
    fun back_closes_the_folder_menu_and_the_grid_keeps_input() {
        var order: List<HomeItem>? = null
        setHome(onOrderChanged = { order = it })

        repeat(2) { press(Key.DirectionRight) }
        press(Key.Menu)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_open))
            .assertIsDisplayed()

        press(Key.Back)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_open))
            .assertDoesNotExist()
        assertNull(order)

        // Focus must be back on the grid, not lost with the removed menu node:
        // OK has to open the folder the menu was opened for.
        press(Key.DirectionCenter)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 1").assertIsDisplayed()
    }

    @Test
    fun menu_on_a_folder_deletes_it_and_its_apps_return_home() {
        var order: List<HomeItem>? = null
        setHome(onOrderChanged = { order = it })

        repeat(2) { press(Key.DirectionRight) }
        press(Key.Menu)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_delete_folder))
            .assertIsDisplayed()

        // Open, Move, Delete Folder — the destructive action now asks once more.
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_delete_folder))
            .assertIsDisplayed()
        press(Key.DirectionCenter)
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Моя папка").assertDoesNotExist()
        rule.onNodeWithContentDescription("В папке 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("В папке 2").assertIsDisplayed()
        val saved = order ?: error("the new order was not saved")
        assertEquals(
            listOf("test.one", "test.two", "test.folder.one", "test.folder.two", "test.three"),
            saved.map { it.key },
        )
    }

    @Test
    fun menu_inside_a_folder_shows_an_app_back_on_home() {
        var order: List<HomeItem>? = null
        setHome(onOrderChanged = { order = it })

        repeat(2) { press(Key.DirectionRight) }
        press(Key.DirectionCenter)
        rule.waitForIdle()

        press(Key.Menu)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_show_on_home))
            .assertIsDisplayed()

        // Open, Move, Show on Home.
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        // The panel leaves on the same 0.5 s zoom as BACK.
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        // The app is a Home tile now and the folder keeps its other child.
        rule.onNodeWithContentDescription("В папке 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("В папке 2").assertDoesNotExist()
        rule.onNodeWithContentDescription("Моя папка").assertIsDisplayed()
        val saved = order ?: error("the new order was not saved")
        assertEquals(
            listOf("test.one", "test.two", "folder:test-folder", "test.folder.one", "test.three"),
            saved.map { it.key },
        )
    }

    // UX-02: a kids profile opens folders read-only. The grid offers no menu,
    // the panel opens no menu and no rename, and a hold runs out without
    // launching anything; a short press still opens and launches.
    @Test
    fun kids_profile_folder_is_read_only() {
        var launched: String? = null
        setHome(kidsMode = true, onLaunch = { launched = it.packageName; true })

        repeat(2) { press(Key.DirectionRight) }
        press(Key.Menu)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_open))
            .assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_delete_folder))
            .assertDoesNotExist()

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 1").assertIsDisplayed()

        press(Key.Menu)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_show_on_home))
            .assertDoesNotExist()

        // The hold is consumed after the usual delay and opens nothing.
        rule.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        rule.mainClock.advanceTimeBy(400)
        rule.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_move))
            .assertDoesNotExist()
        assertNull(launched)

        // A short press is not a hold: the app inside the folder still opens.
        press(Key.DirectionCenter)
        rule.waitForIdle()
        assertEquals("test.folder.one", launched)
    }

    @Test
    fun moving_the_last_app_out_leaves_no_folder_tile() {
        setHome(
            initialItems = listOf(
                app("test.one", "Первый"),
                HomeItem.FolderItem(
                    id = "solo",
                    name = "Одна",
                    apps = listOf(tvApp("test.solo", "Один")),
                ),
            ),
        )

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        rule.waitForIdle()
        press(Key.Menu)
        rule.waitForIdle()
        // Open, Move, Show on Home.
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        // 05_FOLDERS §9: an emptied folder is not shown; the app takes its place.
        rule.onNodeWithContentDescription("Одна").assertDoesNotExist()
        rule.onNodeWithContentDescription("Один").assertIsDisplayed()
        // Same index, same size: memory must still learn the new tile, or the
        // next start points at a folder that no longer exists.
        assertEquals(
            "test.solo",
            context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
                .getString(LauncherPrefs.KEY_FOCUS_PACKAGE, null),
        )
    }

    @Test
    fun menu_on_an_app_hides_it_in_more_apps() {
        var order: List<HomeItem>? = null
        setHome(onOrderChanged = { order = it })

        press(Key.Menu)
        rule.waitForIdle()
        // TalkBack announces the pane title before its rows, so the user knows
        // which app the actions belong to without leaving the menu.
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Первый"))
            .assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_hide))
            .assertIsDisplayed()
        // A user-installed app can be removed; this entry only opens the
        // system's own confirmation, it is not pressed here.
        rule.onNodeWithContentDescription(context.getString(R.string.menu_uninstall))
            .assertIsDisplayed()

        // Open, Move, Move to Folder…, Hide.
        repeat(3) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Первый").assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.hidden_folder_name))
            .assertIsDisplayed()
        val saved = order ?: error("the new order was not saved")
        assertEquals(
            listOf("test.two", "folder:test-folder", "test.three", "folder:$HIDDEN_FOLDER_ID"),
            saved.map { it.key },
        )
        val hidden = saved.last() as HomeItem.FolderItem
        assertEquals(listOf("test.one"), hidden.apps.map { it.packageName })
        // Focus stays on the tile that took the gap, not on the new folder.
        rule.onNodeWithContentDescription("Второй").assertIsDisplayed()
        // Phase 08: only a resting cursor is persisted; the save is debounced.
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        assertEquals(
            "test.two",
            context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
                .getString(LauncherPrefs.KEY_FOCUS_PACKAGE, null),
        )
    }

    @Test
    fun the_app_menu_moves_an_app_into_an_existing_folder() {
        var order: List<HomeItem>? = null
        setHome(onOrderChanged = { order = it })

        press(Key.Menu)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_move_to_folder))
            .assertIsDisplayed()

        // Open, Move, Move to Folder…: the app menu closes and the picker opens.
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Моя папка").assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.new_folder_title))
            .assertIsDisplayed()

        // The first picker row is the existing folder.
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        // The tile left the grid and joined the folder at its old slot.
        rule.onNodeWithContentDescription("Первый").assertDoesNotExist()
        val saved = order ?: error("the new order was not saved")
        assertEquals(
            listOf("test.two", "folder:test-folder", "test.three"),
            saved.map { it.key },
        )
        val folder = saved[1] as HomeItem.FolderItem
        assertEquals(
            listOf("test.folder.one", "test.folder.two", "test.one"),
            folder.apps.map { it.packageName },
        )
    }

    @Test
    fun new_folder_from_the_app_menu_wraps_the_app_in_its_slot() {
        var order: List<HomeItem>? = null
        setHome(onOrderChanged = { order = it })

        press(Key.Menu)
        rule.waitForIdle()
        // Open, Move, Move to Folder….
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        // One existing folder is listed above, so the new-folder row is second.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        // The keyboard opens empty: type the first letter, then reach Done
        // (Down to the action row, Left to its start, six Rights to Done).
        press(Key.DirectionCenter)
        repeat(3) { press(Key.DirectionDown) }
        repeat(10) { press(Key.DirectionLeft) }
        repeat(6) { press(Key.DirectionRight) }
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        val saved = order ?: error("the new order was not saved")
        assertEquals(4, saved.size)
        val created = saved[0] as HomeItem.FolderItem
        assertEquals(listOf("test.one"), created.apps.map { it.packageName })
        assertTrue(created.name.isNotEmpty())
        assertEquals(
            listOf("test.two", "folder:test-folder", "test.three"),
            saved.drop(1).map { it.key },
        )
        // The folder takes the app's slot, and the old tile is gone.
        rule.onNodeWithContentDescription(created.name).assertIsDisplayed()
        rule.onNodeWithContentDescription("Первый").assertDoesNotExist()
    }

    @Test
    fun menu_on_the_hidden_folder_opens_no_menu() {
        setHome(
            initialItems = listOf(
                app("test.one", "Первый"),
                HomeItem.FolderItem(
                    id = HIDDEN_FOLDER_ID,
                    name = context.getString(R.string.hidden_folder_name),
                    apps = listOf(tvApp("test.hidden", "Скрытый")),
                ),
            ),
        )

        press(Key.DirectionRight)
        press(Key.Menu)
        rule.waitForIdle()

        rule.onNodeWithContentDescription(context.getString(R.string.menu_delete_folder))
            .assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_open))
            .assertDoesNotExist()
    }

    @Test
    fun menu_on_the_own_settings_tile_has_no_hide_or_uninstall() {
        setHome(
            initialItems = listOf(app(context.packageName, "Настройки OVisionTV")),
        )

        press(Key.Menu)
        rule.waitForIdle()

        rule.onNodeWithContentDescription(context.getString(R.string.menu_app_info))
            .assertIsDisplayed()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_hide))
            .assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_uninstall))
            .assertDoesNotExist()
    }

    @Test
    fun an_app_returns_from_more_apps_to_the_main_grid() {
        var order: List<HomeItem>? = null
        setHome(
            initialItems = listOf(
                app("test.one", "Первый"),
                HomeItem.FolderItem(
                    id = HIDDEN_FOLDER_ID,
                    name = context.getString(R.string.hidden_folder_name),
                    apps = listOf(tvApp("test.hidden", "Скрытый")),
                ),
            ),
            onOrderChanged = { order = it },
        )

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        rule.waitForIdle()
        press(Key.Menu)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_show_on_home))
            .assertIsDisplayed()
        // Open, Move, Show on Home.
        repeat(2) { press(Key.DirectionDown) }
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        rule.onNodeWithContentDescription(context.getString(R.string.hidden_folder_name))
            .assertDoesNotExist()
        rule.onNodeWithContentDescription("Скрытый").assertIsDisplayed()
        val saved = order ?: error("the new order was not saved")
        assertEquals(listOf("test.one", "test.hidden"), saved.map { it.key })
    }

    // The keyboard covers the panel. With the cells still in the semantics tree,
    // TalkBack could launch an app hidden under the modal keyboard.
    @Test
    fun the_rename_keyboard_hides_the_folder_cells_from_talkback() {
        setHome()

        repeat(2) { press(Key.DirectionRight) }
        press(Key.DirectionCenter)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("В папке 1").assertIsDisplayed()

        rule.onNodeWithContentDescription("Моя папка")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        rule.onNodeWithContentDescription(context.getString(R.string.keyboard_space))
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("В папке 1").assertDoesNotExist()
    }

    // The hidden folder keeps its fixed name, so its title is a label and must
    // not advertise a rename that the key path refuses.
    @Test
    fun the_hidden_folder_title_is_not_a_rename_button() {
        setHome(
            initialItems = listOf(
                app("test.one", "Первый"),
                HomeItem.FolderItem(
                    id = HIDDEN_FOLDER_ID,
                    name = context.getString(R.string.hidden_folder_name),
                    apps = listOf(tvApp("test.hidden", "Скрытый")),
                ),
            ),
        )

        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        rule.waitForIdle()

        rule.onNodeWithContentDescription(context.getString(R.string.hidden_folder_name))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    // An external package change rebuilds the grid under an open menu. A menu
    // whose tile is gone has no node left to dismiss it, so it must close with
    // the rebuild instead of gating the grid and swallowing SEARCH forever.
    @Test
    fun a_package_change_closes_a_menu_for_a_removed_app() {
        val list = mutableStateOf(items())
        rule.setContent {
            OVisionTheme {
                HomeScreen(
                    initialItems = list.value,
                    resumeTick = 0,
                    onLaunch = { true },
                    onOrderChanged = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        press(Key.Menu)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.menu_open))
            .assertIsDisplayed()

        // "Первый" is uninstalled while its menu is on screen.
        rule.runOnIdle { list.value = items().drop(1) }
        rule.waitForIdle()

        press(Key.Search)
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.search_title)).assertIsDisplayed()
    }

    // Android TV remotes send ASSIST where tvOS sends the magnifier, so both
    // keys must reach the search surface.
    @Test
    fun the_assist_key_opens_search() {
        setHome()

        press(Key.Assist)
        rule.waitForIdle()

        rule.onNodeWithText(context.getString(R.string.search_title)).assertIsDisplayed()
    }

    // The last row may be shorter than six cells. DOWN from a column that has
    // no cell below must still move somewhere useful instead of leaving focus
    // standing on the first row.
    @Test
    fun down_from_an_incomplete_last_row_moves_to_the_nearest_cell() {
        var launched: String? = null
        val eight = (1..8).map { app("test.$it", "Приложение $it") }
        setHome(initialItems = eight, onLaunch = { launched = it.packageName; true })

        repeat(5) { press(Key.DirectionRight) }
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        // Cells 6 and 7 exist on the second row; the cursor lands on the last one.
        assertEquals("test.8", launched)
    }

    // When the focused package disappears, its saved key no longer resolves.
    // The cursor must stay at the last remembered place instead of jumping to
    // the first tile, which would also mislead TalkBack users.
    @Test
    fun removing_the_focused_app_keeps_the_cursor_in_place() {
        val list = mutableStateOf((1..4).map { app("test.$it", "Приложение $it") })
        var launched: String? = null
        rule.setContent {
            OVisionTheme {
                HomeScreen(
                    initialItems = list.value,
                    resumeTick = 0,
                    onLaunch = { launched = it.packageName; true },
                    onOrderChanged = {},
                )
            }
        }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        repeat(2) { press(Key.DirectionRight) }
        rule.waitForIdle()

        // The focused third app is uninstalled; the fourth app takes its index.
        rule.runOnIdle { list.value = list.value.filterNot { it.key == "test.3" } }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertEquals("test.4", launched)
    }

    // A folder page holds nine cells in three rows. Both RIGHT and DOWN at the
    // page edge flip it; DOWN keeps the column so the grid does not jump to a
    // random cell, and the off-page cards stay out of the semantics tree.
    @Test
    fun folder_down_at_the_page_end_opens_the_next_page() {
        var launched: String? = null
        val big = (1..10).map { tvApp("test.big.$it", "Большая $it") }
        setHome(
            initialItems = listOf(HomeItem.FolderItem(id = "big", name = "Большая", apps = big)),
            onLaunch = { launched = it.packageName; true },
        )

        press(Key.DirectionCenter)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Большая 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Большая 10").assertDoesNotExist()

        // RIGHT stays inside the row: from cell 3 the third RIGHT changes
        // nothing, so two DOWNs below land on cell 9, the page end.
        repeat(3) {
            press(Key.DirectionRight)
            rule.waitForIdle()
        }
        repeat(2) {
            press(Key.DirectionDown)
            rule.waitForIdle()
        }

        // DOWN on the last row opens the next page at the same column.
        press(Key.DirectionDown)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Большая 10").assertIsDisplayed()
        rule.onNodeWithContentDescription("Большая 1").assertDoesNotExist()

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertEquals("test.big.10", launched)
    }
}
