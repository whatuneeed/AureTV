@file:OptIn(ExperimentalTestApi::class)

package app.aure.tv.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aure.tv.R
import app.aure.tv.data.HomeItem
import app.aure.tv.data.LauncherPrefs
import app.aure.tv.data.SleepControls
import app.aure.tv.data.TvApp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the status pill and the Control Center through the real HomeScreen:
 * Up reaches the pill, OK opens the panel, arrows stay inside it, Back returns
 * the focus to the pill and Down drops back into the grid.
 */
@RunWith(AndroidJUnit4::class)
class ControlCenterTest {

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

    private fun app(packageName: String, label: String) = HomeItem.AppItem(
        TvApp(
            packageName = packageName,
            activityName = "$packageName.MainActivity",
            label = label,
            banner = null,
            icon = null,
        ),
    )

    private fun items(count: Int): List<HomeItem> =
        (0 until count).map { app("test.cc$it", "Приложение $it") }

    private fun setHome(
        count: Int = 8,
        onStartSaver: () -> Unit = {},
        onLaunch: (TvApp) -> Boolean = { true },
        onOpenSystem: ((Intent) -> Unit)? = null,
    ) {
        rule.setContent {
            OVisionTheme {
                HomeScreen(
                    initialItems = items(count),
                    resumeTick = 0,
                    onLaunch = onLaunch,
                    onOrderChanged = {},
                    onStartSaver = onStartSaver,
                    onOpenSystem = onOpenSystem,
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

    private fun capsule() = rule.onNodeWithContentDescription(context.getString(R.string.cc_title))

    private fun openPanel() {
        press(Key.DirectionUp)
        capsule().assertIsFocused()
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
    }

    @Test
    fun upOpensThePanelAndBackReturnsToTheCapsule() {
        setHome()

        // The grid keeps the focus until Up; the pill is chrome, not a stop.
        rule.onNodeWithContentDescription("Приложение 0").assertIsFocused()
        press(Key.DirectionUp)
        capsule().assertIsFocused()

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        // Promoted, the pill is the panel band: the panel owns the focus and
        // the pill leaves the accessibility tree. Power is the lead tile.
        rule.onNodeWithContentDescription(context.getString(R.string.cc_power))
            .assertIsFocused()
        capsule().assertDoesNotExist()

        press(Key.Back)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.cc_power))
            .assertDoesNotExist()
        capsule().assertIsFocused()
    }

    @Test
    fun okReopensThePanelRightAfterBack() {
        setHome()
        press(Key.DirectionUp)
        capsule().assertIsFocused()

        // The release must land on the panel, as the burst filter's delayed
        // KeyUp does on a real remote: down, the panel takes focus, then up.
        dispatchRawKey(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.cc_power))
            .assertIsFocused()
        dispatchRawKey(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        rule.waitForIdle()

        press(Key.Back)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        capsule().assertIsFocused()

        // Home recorded the opening OK but the panel consumed its release;
        // without seeing it Home reads this press inside STALE_MS as a repeat.
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onNodeWithContentDescription(context.getString(R.string.cc_power))
            .assertIsFocused()
    }

    @Test
    fun arrowsStayInsideThePanel() {
        setHome()
        openPanel()

        val power = context.getString(R.string.cc_power)
        val sound = context.getString(R.string.cc_sound)
        val wifi = context.getString(R.string.settings_network_wifi)
        val display = context.getString(R.string.cc_display)
        val timer = context.getString(R.string.cc_sleep_timer)

        rule.onNodeWithContentDescription(power).assertIsFocused()
        // Right enters the right column; Down walks it instead of dropping
        // diagonally into the circle row.
        press(Key.DirectionRight)
        rule.onNodeWithContentDescription(wifi).assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithContentDescription(display).assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithContentDescription(timer).assertIsFocused()
        // Left follows the row: the timer sits next to Sound, not Power.
        press(Key.DirectionLeft)
        rule.onNodeWithContentDescription(sound).assertIsFocused()
        press(Key.DirectionRight)
        rule.onNodeWithContentDescription(timer).assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithContentDescription(display).assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithContentDescription(wifi).assertIsFocused()
        // The top edge holds the focus.
        press(Key.DirectionUp)
        rule.onNodeWithContentDescription(wifi).assertIsFocused()
        press(Key.DirectionLeft)
        rule.onNodeWithContentDescription(power).assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithContentDescription(sound).assertIsFocused()
        // Down from Sound drops into the circle row; Right walks the row and
        // Up returns to the tile above, whichever circles the device offers.
        press(Key.DirectionDown)
        assertCircleFocused()
        press(Key.DirectionRight)
        assertCircleFocused()
        press(Key.DirectionUp)
        rule.onNodeWithContentDescription(sound).assertIsFocused()
        // The left edge holds the focus.
        press(Key.DirectionLeft)
        rule.onNodeWithContentDescription(sound).assertIsFocused()
        press(Key.DirectionUp)
        rule.onNodeWithContentDescription(power).assertIsFocused()
    }

    private fun assertCircleFocused() {
        val labels = listOf(
            R.string.cc_screensaver, R.string.cc_apps, R.string.cc_search,
            R.string.cc_settings, R.string.cc_system, R.string.cc_accessibility,
            R.string.cc_controllers,
        ).map { context.getString(it) }
        val focused = rule.onNode(
            SemanticsMatcher("focused") {
                it.config.getOrNull(SemanticsProperties.Focused) == true
            },
        ).fetchSemanticsNode()
        val description = focused.config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString()
        assertTrue("expected a circle, focused on $description", description in labels)
    }

    @Test
    fun downFromTheCapsuleReturnsToTheGrid() {
        setHome()
        press(Key.DirectionUp)
        capsule().assertIsFocused()

        press(Key.DirectionDown)
        capsule().assertIsNotFocused()
        rule.onNodeWithContentDescription("Приложение 0").assertIsFocused()
    }

    @Test
    fun screensaverTileStartsTheSaverAndClosesThePanel() {
        var started = false
        setHome(onStartSaver = { started = true })
        openPanel()

        // Sound drops into the circle row; Left reaches the saver at its start.
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertTrue(started)
        rule.onNodeWithContentDescription(context.getString(R.string.cc_screensaver))
            .assertDoesNotExist()
    }

    // AQ-03: asking for the sleep permission is system navigation, so Power
    // and Timer must go through the same gate host as the other system tiles
    // instead of opening the permission screen directly.
    @Test
    fun powerTileRoutesThePermissionRequestThroughTheSystemGate() {
        assumeTrue("the TV must offer a permission screen", SleepControls.adminSupported(context))
        assumeFalse("the permission must not be granted already", SleepControls.hasAdmin(context))
        var captured: Intent? = null
        setHome(onOpenSystem = { captured = it })
        openPanel()

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        val intent = requireNotNull(captured) {
            "Power must ask the gate host, not open the permission screen directly"
        }
        assertEquals(permissionAction(), intent.action)
    }

    @Test
    fun timerTileRoutesThePermissionRequestThroughTheSystemGate() {
        assumeTrue("the TV must offer a permission screen", SleepControls.adminSupported(context))
        assumeFalse("the permission must not be granted already", SleepControls.hasAdmin(context))
        var captured: Intent? = null
        setHome(onOpenSystem = { captured = it })
        openPanel()

        // Right enters the right column; Down walks it to the timer (the
        // display tile only exists when the TV exposes a screen for it).
        press(Key.DirectionRight)
        val timer = context.getString(R.string.cc_sleep_timer)
        var steps = 0
        while (focusedDescription() != timer && steps < 3) {
            press(Key.DirectionDown)
            steps++
        }
        rule.onNodeWithContentDescription(timer).assertIsFocused()

        press(Key.DirectionCenter)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()

        val intent = requireNotNull(captured) {
            "Timer must ask the gate host, not open the permission screen directly"
        }
        assertEquals(permissionAction(), intent.action)
        // requestClose(false) is preserved: the panel leaves for the system screen.
        rule.onNodeWithContentDescription(timer).assertDoesNotExist()
    }

    private fun permissionAction(): String =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN)) {
            DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
        } else {
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        }

    private fun focusedDescription(): String? =
        rule.onNode(
            SemanticsMatcher("focused") {
                it.config.getOrNull(SemanticsProperties.Focused) == true
            },
        ).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString()

    @Test
    fun panelCanBeReopenedImmediately() {
        setHome()
        repeat(2) {
            press(Key.DirectionUp)
            capsule().assertIsFocused()
            press(Key.DirectionCenter)
            rule.mainClock.advanceTimeBy(300)
            rule.waitForIdle()
            rule.onNodeWithContentDescription(context.getString(R.string.cc_power))
                .assertIsFocused()
            press(Key.Back)
            rule.mainClock.advanceTimeBy(400)
            rule.waitForIdle()
            capsule().assertIsFocused()
        }
    }

    @Test
    fun libraryTileIsAbsentWithoutASecondRow() {
        setHome(count = 6)
        openPanel()

        // Six apps fill the dock; there is no library to open, so the tile must
        // not be offered at all. The saver circle and search remain.
        rule.onNodeWithContentDescription(context.getString(R.string.cc_apps)).assertDoesNotExist()
        rule.onNodeWithContentDescription(context.getString(R.string.cc_search)).assertExists()
        rule.onNodeWithContentDescription(context.getString(R.string.cc_screensaver)).assertExists()
    }
}
