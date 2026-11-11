package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.AppRepository
import com.whatuneed.ovisiontv.kotlin.data.HIDDEN_FOLDER_ID
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.HomeOrderStore
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings edits the saved order in another activity. Home only sees it on the
 * resume that follows, so this pins the hand-off: a changed saved order must
 * rebuild the grid, an unchanged one must not be touched.
 */
@RunWith(AndroidJUnit4::class)
class LauncherReloadTest {

    @get:Rule
    val rule = createComposeRule()

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedPrefs: Map<String, Any?>

    @Before
    fun clearPrefs() {
        savedPrefs = PrefsSnapshot.capture(context)
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun restorePrefs() {
        PrefsSnapshot.restore(context, savedPrefs)
    }

    @Test
    fun the_first_grid_is_saved_so_a_language_change_cannot_reorder_it() {
        val apps = runBlocking { AppRepository(context).load(withArtwork = false) }
        val orderStore = HomeOrderStore(context)

        rule.setContent {
            OVisionTheme {
                LauncherRoot()
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(apps.first().label)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Without this first save the fallback follows localized labels, so a
        // language switch would silently re-sort the whole grid.
        rule.runOnIdle {
            val saved = orderStore.savedOrder()
            assertNotNull(saved)
            val entries = JSONArray(saved)
            assertEquals(
                apps.map { it.packageName },
                (0 until entries.length()).map { entries.getString(it) },
            )
        }
    }

    @Test
    fun a_changed_saved_order_rebuilds_the_grid_on_resume() {
        val apps = runBlocking { AppRepository(context).load(withArtwork = false) }
        val first = apps.first()
        val hiddenName = context.getString(R.string.hidden_folder_name)
        val orderStore = HomeOrderStore(context)

        var tick by mutableIntStateOf(0)
        rule.setContent {
            OVisionTheme {
                LauncherRoot(resumeTick = tick)
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(first.label)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The same write Settings does: one app parked in the hidden folder.
        orderStore.saveOrder(
            listOf(HomeItem.FolderItem(HIDDEN_FOLDER_ID, hiddenName, listOf(first))),
        )
        rule.runOnIdle { tick++ }
        // The reload reads the package list off the main thread.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(hiddenName)
                .fetchSemanticsNodes().isNotEmpty()
        }

        orderStore.clearOrder()
        rule.runOnIdle { tick++ }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription(hiddenName)
                .fetchSemanticsNodes().isEmpty()
        }
        rule.onAllNodesWithContentDescription(first.label)
            .fetchSemanticsNodes()
            .let { assert(it.isNotEmpty()) }
    }
}
