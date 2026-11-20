package app.aure.tv.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
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
import app.aure.tv.data.HIDDEN_FOLDER_ID
import app.aure.tv.data.HomeItem
import app.aure.tv.data.HomeOrderStore
import app.aure.tv.data.LocalePreference
import app.aure.tv.data.TvApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Settings saves the layout in another activity; the launcher only sees it on
 * the resume that follows. One test drives the real `MainActivity.onResume`
 * through a stop/start cycle instead of a simulated tick; the other walks the
 * whole path the user takes: tile, real `SettingsActivity`, reset, BACK.
 *
 * The layout is seeded before the activity starts, and any write made by the
 * test happens while the activity is stopped. Writing a new layout right after
 * a resume would race the reload that resume started: that reload reads the
 * value again when it finishes and would then treat the test's write as already
 * applied.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityResumeTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    // The launcher's own Settings tile is not used as a fixture: it is the tile
    // the round-trip test presses, and a folder holding it would hide it.
    private val catalog: List<TvApp> by lazy {
        runBlocking { AppRepository(context).load(withArtwork = false) }
            .filterNot { it.packageName == context.packageName }
    }

    private val firstApp: TvApp get() = catalog.first()
    private val secondApp: TvApp get() = catalog[1]

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val seedLayout = object : ExternalResource() {
        private lateinit var savedPrefs: Map<String, Any?>
        private lateinit var savedSetup: Map<String, Any?>

        override fun before() {
            savedPrefs = PrefsSnapshot.capture(context)
            savedSetup = PrefsSnapshot.captureSetup(context)
            PrefsSnapshot.markSetupDone(context)
            HomeOrderStore(context).saveOrder(listOf(folder(), hiddenFolder()))
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

    // A layout that belongs to this test alone, so no real folder can stand in for it.
    private fun folder(): HomeItem.FolderItem =
        HomeItem.FolderItem(FOLDER_ID, FOLDER_NAME, listOf(firstApp))

    // One hidden app gives the Settings screen a load marker: the count on the
    // Apps page row is built from the saved order, so seeing it means the
    // screen has the layout Reset needs.
    private fun hiddenFolder(): HomeItem.FolderItem =
        HomeItem.FolderItem(HIDDEN_FOLDER_ID, HIDDEN_FOLDER_NAME, listOf(secondApp))

    private fun waitForVisible(description: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForGone(description: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun leaveAndReturn(write: (HomeOrderStore) -> Unit) {
        val scenario = composeRule.activityRule.scenario
        val store = HomeOrderStore(context)
        scenario.moveToState(Lifecycle.State.CREATED)
        write(store)
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    private fun string(id: Int): String = LocalePreference.wrap(context).getString(id)

    private fun plural(id: Int, count: Int): String =
        LocalePreference.wrap(context).resources.getQuantityString(id, count, count)

    private fun click(description: String, substring: Boolean = false) {
        composeRule.onAllNodesWithContentDescription(description, substring)
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    private fun waitForDescriptionContaining(part: String) {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithContentDescription(part, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Settings lives in its own activity, so its state is read from the
    // lifecycle monitor instead of the compose rule's own activity.
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

    private fun waitForSettings(): SettingsActivity {
        composeRule.waitUntil(20_000) { resumedSettings() != null }
        return checkNotNull(resumedSettings())
    }

    private fun back(activity: SettingsActivity) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun a_resume_after_another_activity_rebuilds_the_grid() {
        // The seeded folder is on the grid once the first load has finished.
        waitForVisible(FOLDER_NAME)

        leaveAndReturn { it.clearOrder() }
        waitForGone(FOLDER_NAME)
        waitForVisible(firstApp.label)

        leaveAndReturn { it.saveOrder(listOf(folder())) }
        waitForVisible(FOLDER_NAME)
    }

    @Test
    fun a_settings_round_trip_rebuilds_the_grid() {
        waitForVisible(FOLDER_NAME)

        // The tile starts the real SettingsActivity through the same code as OK.
        click(string(R.string.settings_tile_name))
        val settings = waitForSettings()

        click(string(R.string.settings_home_screen), substring = true)

        // Reset returns early until the screen has loaded the catalog and the
        // order; the hidden count is drawn from that order, so it is the marker.
        waitForDescriptionContaining(plural(R.plurals.settings_hidden_count, 1))

        click(string(R.string.settings_reset_layout))
        click(string(R.string.settings_reset_confirm))
        assertNull(HomeOrderStore(context).savedOrder())

        // The first BACK leaves the Home Screen page, the second closes the
        // activity.
        back(settings)
        back(settings)
        composeRule.waitUntil(20_000) { resumedSettings() == null }

        waitForGone(FOLDER_NAME)
        waitForVisible(firstApp.label)
        // Reset also brings the hidden app back, so the reload really rebuilt
        // the grid from the catalog instead of keeping the old items.
        waitForVisible(secondApp.label)
    }

    private companion object {
        const val FOLDER_ID = "resume-test-folder"
        const val FOLDER_NAME = "Resume test folder"
        const val HIDDEN_FOLDER_NAME = "Resume test hidden folder"
    }
}
