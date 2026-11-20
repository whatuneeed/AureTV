package app.aure.tv.ui

import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.aure.tv.MainActivity
import app.aure.tv.R
import app.aure.tv.SettingsActivity
import app.aure.tv.data.AppRepository
import app.aure.tv.data.ContentPreferences
import app.aure.tv.data.HomeItem
import app.aure.tv.data.HomeOrderStore
import app.aure.tv.data.LocalePreference
import app.aure.tv.data.TvApp
import app.aure.tv.ui.components.SEARCH_FIELD_TAG
import kotlinx.coroutines.runBlocking
import java.util.Locale
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The language is chosen in Settings, a separate activity. Home applies it on
 * the resume that follows without recreating its window. This walks that path
 * with the real Settings screen: the grid starts in Russian, English is picked
 * in Settings, BACK returns, and Home must come back in English with the saved
 * layout intact. A second test sends real remote keys after that switch: the
 * same window and composition must still receive input.
 */
@RunWith(AndroidJUnit4::class)
class LanguageReturnTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

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
            // A known starting language keeps the test independent of the
            // device locale and of the language the user left behind.
            LocalePreference.set(context, LocalePreference.RUSSIAN)
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

    private fun string(id: Int, language: String): String {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration).getString(id)
    }

    private fun click(description: String, substring: Boolean = false) {
        composeRule.onAllNodesWithContentDescription(description, substring)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun waitForVisible(description: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun resumedSettings(): SettingsActivity? {
        var found: SettingsActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<SettingsActivity>()
                .firstOrNull()
        }
        return found
    }

    // The language page redraws in place: the English row label only exists
    // after the switch, and the activity instance must be the same one.
    private fun waitForEnglishSettings(sameAs: SettingsActivity?) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithContentDescription(
                string(R.string.settings_language_english, LocalePreference.ENGLISH),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        if (sameAs != null) assertSame(sameAs, resumedSettings())
    }

    private fun back() {
        val settings = checkNotNull(resumedSettings())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            settings.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    // Picks English in the real Settings screen and closes it. Neither activity
    // recreates: Settings redraws in place and Home follows on resume.
    private fun pickEnglishAndReturn() {
        // The tile label is the Russian one: the test starts in Russian.
        click(string(R.string.settings_tile_name, LocalePreference.RUSSIAN))
        val settings = checkNotNull(resumedSettings())

        // The redesign nests the language under Appearance.
        click(string(R.string.settings_interface, LocalePreference.RUSSIAN), substring = true)
        click(string(R.string.settings_language, LocalePreference.RUSSIAN), substring = true)
        click(string(R.string.settings_language_english, LocalePreference.RUSSIAN))

        // The same Settings window must already show English before BACK.
        waitForEnglishSettings(settings)

        // Language page -> General -> root, then close Settings.
        back()
        back()
        back()
        composeRule.waitUntil(20_000) { resumedSettings() == null }
        assertNotNull(resumedMain())
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

    // A key sent by the remote reaches the app through the window, not through
    // the Compose test harness, so it lands only while the launcher owns focus.
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
            composeRule.onAllNodesWithTag(SEARCH_FIELD_TAG)
                .fetchSemanticsNodes().isNotEmpty() == open
        }
    }

    @Test
    fun a_language_picked_in_settings_reaches_home_in_place() {
        waitForVisible(FOLDER_NAME)
        pickEnglishAndReturn()

        // Home sees the changed preference on resume and redraws the same
        // window; the launcher's own tile is renamed in English.
        waitForVisible(string(R.string.settings_tile_name, LocalePreference.ENGLISH))
        // The saved layout survives the switch.
        waitForVisible(FOLDER_NAME)
    }

    // The switch keeps the activity and its window. The grid is drawn from the
    // first frame, but the remote only works once Home has focus again.
    @Test
    fun the_remote_works_after_home_switches_language() {
        waitForVisible(FOLDER_NAME)
        pickEnglishAndReturn()
        waitForVisible(string(R.string.settings_tile_name, LocalePreference.ENGLISH))
        waitForWindowFocus()

        sendKey(KeyEvent.KEYCODE_SEARCH)
        waitForSearchOverlay(open = true)

        // BACK belongs to the overlay: the launcher itself must stay in front.
        sendKey(KeyEvent.KEYCODE_BACK)
        waitForSearchOverlay(open = false)
        assertNotNull(resumedMain())

        // The saved layout survived the round trip and the search surface.
        waitForVisible(FOLDER_NAME)
    }

    private companion object {
        const val FOLDER_ID = "language-return-folder"
        const val FOLDER_NAME = "Language test folder"
    }
}
