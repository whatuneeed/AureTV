package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.whatuneed.ovisiontv.kotlin.MainActivity
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.AppRepository
import com.whatuneed.ovisiontv.kotlin.data.ContentPreferences
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.HomeOrderStore
import com.whatuneed.ovisiontv.kotlin.data.LocalePreference
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.ui.components.SEARCH_FIELD_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Every overlay around the grid — the folder panel, its menu and the search
 * surface — is closed by a key, and closing it removes the node that held focus.
 * `HomeScreenTest` and `SearchTest` drive these keys through the Compose test
 * root, which can still deliver them when the window has lost input focus. This
 * test sends real keys to the real activity instead: a closed overlay that
 * leaves the grid without focus, or a search keyboard that never takes the
 * focus, shows up as a dead remote, not as a passing synthetic test.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInputTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    // A folder holding one installed app: the app is reachable only through the
    // panel, so its label is the panel's marker.
    private val firstApp: TvApp by lazy {
        runBlocking { AppRepository(context).load(withArtwork = false) }
            .first { it.packageName != context.packageName }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val seedLayout = object : ExternalResource() {
        private lateinit var savedPrefs: Map<String, Any?>
        private lateinit var savedSetup: Map<String, Any?>

        override fun before() {
            savedPrefs = PrefsSnapshot.capture(context)
            savedSetup = PrefsSnapshot.captureSetup(context)
            PrefsSnapshot.markSetupDone(context)
            HomeOrderStore(context).saveOrder(
                listOf(HomeItem.FolderItem(FOLDER_ID, FOLDER_NAME, listOf(firstApp))),
            )
        }

        override fun after() {
            PrefsSnapshot.restore(context, savedPrefs)
            PrefsSnapshot.restoreSetup(context, savedSetup)
        }
    }

    // Home's shelf reads the TV provider only with this grant; keeping it on
    // makes the shelf path part of the run instead of a denied query.
    private val listingsPermission =
        GrantPermissionRule.grant(ContentPreferences.READ_TV_LISTINGS)

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(listingsPermission)
        .around(seedLayout)
        .around(composeRule)

    private fun string(id: Int): String = LocalePreference.wrap(context).getString(id)

    // A fresh install leaves the first-run wizard in front of Home, and Home
    // recreates itself when a restored language differs from the one its
    // activity applied. Both windows have no composition to read, so a poll
    // that lands in one keeps waiting: the 20 s deadline still reports a grid
    // that never comes back.
    private fun poll(read: () -> Boolean): Boolean = try {
        read()
    } catch (_: IllegalStateException) {
        false
    }

    private fun waitForVisible(description: String) {
        composeRule.waitUntil(20_000) {
            poll {
                composeRule.onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun waitForGone(description: String) {
        composeRule.waitUntil(20_000) {
            poll {
                composeRule.onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes().isEmpty()
            }
        }
    }

    private fun resumedMain(): MainActivity? {
        var found: MainActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>()
                .firstOrNull()
        }
        return found
    }

    // A remote press reaches the app through the window, so the launcher has to
    // own the focus before the first key is sent.
    private fun waitForWindowFocus() {
        composeRule.waitUntil(20_000) {
            var focused = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                focused = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull()
                    ?.hasWindowFocus() == true
            }
            focused
        }
    }

    private fun sendKey(keyCode: Int) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)
        composeRule.waitForIdle()
    }

    private fun waitForSearchOverlay(open: Boolean) {
        composeRule.waitUntil(20_000) {
            poll {
                composeRule.onAllNodesWithTag(SEARCH_FIELD_TAG)
                    .fetchSemanticsNodes().isNotEmpty() == open
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(20_000) {
            poll {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    // The search keyboard starts in the language of the screen, and its first
    // row is a hardware row: the second key is the one an arrow reaches.
    private fun secondKeyboardKey(): String =
        if (LocalePreference.wrap(context).resources.configuration.locales[0].language == "ru") {
            "ц"
        } else {
            "w"
        }

    @Test
    fun every_overlay_hands_the_remote_back_to_the_grid() {
        waitForVisible(FOLDER_NAME)
        waitForWindowFocus()

        // OK opens the folder panel; the app inside is reachable only there.
        sendKey(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForVisible(firstApp.label)

        sendKey(KeyEvent.KEYCODE_BACK)
        waitForGone(firstApp.label)

        // Search is sent straight after the panel gave focus back.
        sendKey(KeyEvent.KEYCODE_SEARCH)
        waitForSearchOverlay(open = true)
        sendKey(KeyEvent.KEYCODE_BACK)
        waitForSearchOverlay(open = false)

        // MENU opens the menu of the focused tile; only the folder menu has the
        // delete entry, so it also proves the focus stayed on the folder.
        sendKey(KeyEvent.KEYCODE_MENU)
        waitForVisible(string(R.string.menu_delete_folder))
        sendKey(KeyEvent.KEYCODE_BACK)
        waitForGone(string(R.string.menu_delete_folder))

        // The key after the menu close must reach the grid again.
        sendKey(KeyEvent.KEYCODE_SEARCH)
        waitForSearchOverlay(open = true)
        sendKey(KeyEvent.KEYCODE_BACK)
        waitForSearchOverlay(open = false)
        assertNotNull(resumedMain())
    }

    // The search surface is a focus node of its own. Its arrows and OK, and the
    // unicode char of a hardware key, are only exercised by real events: the
    // synthetic tests tap the keys through their TalkBack action.
    @Test
    fun search_takes_real_arrows_and_hardware_typing() {
        waitForVisible(FOLDER_NAME)
        waitForWindowFocus()

        sendKey(KeyEvent.KEYCODE_SEARCH)
        waitForSearchOverlay(open = true)
        val secondKey = secondKeyboardKey()

        // One arrow to the second key of the first row, then OK: if the key
        // went to the grid behind the overlay, the field would stay empty.
        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        sendKey(KeyEvent.KEYCODE_DPAD_CENTER)
        waitForText(secondKey)

        // A hardware keyboard arrives as a key code and types through the
        // event's unicode char; no on-screen key does that.
        sendKey(KeyEvent.KEYCODE_A)
        waitForText("${secondKey}a")

        sendKey(KeyEvent.KEYCODE_DEL)
        waitForText(secondKey)
        sendKey(KeyEvent.KEYCODE_DEL)
        waitForText(string(R.string.search_placeholder))

        sendKey(KeyEvent.KEYCODE_BACK)
        waitForSearchOverlay(open = false)
        assertNotNull(resumedMain())
        waitForVisible(FOLDER_NAME)
    }

    // Some remotes report a held OK as repeated Down/Up pairs, and the emulator
    // forwarding a host keyboard does the same. Five such pairs on one keyboard
    // key must count as one press: a filter that misses them types five letters.
    @Test
    fun a_held_ok_sent_as_pairs_types_one_character() {
        waitForVisible(FOLDER_NAME)
        waitForWindowFocus()

        sendKey(KeyEvent.KEYCODE_SEARCH)
        waitForSearchOverlay(open = true)
        val secondKey = secondKeyboardKey()
        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)

        val resumed = resumedMain()
        assertNotNull(resumed)
        val main = resumed!!
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            repeat(5) {
                val now = android.os.SystemClock.uptimeMillis()
                main.dispatchKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0),
                )
                main.dispatchKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0),
                )
            }
        }
        // Only the letters are compared: the release lands after the settle.
        waitForText(secondKey)

        sendKey(KeyEvent.KEYCODE_BACK)
        waitForSearchOverlay(open = false)
        assertNotNull(resumedMain())
    }

    private companion object {
        const val FOLDER_ID = "input-test-folder"
        const val FOLDER_NAME = "Input test folder"
    }
}
