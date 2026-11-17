package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ui.PrefsSnapshot
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * buildHomeOrder is covered by unit tests; this class checks the store itself —
 * the JSON written by saveOrder has to read back through applyOrder on the real
 * preferences file, because that is the only path the launcher uses.
 */
@RunWith(AndroidJUnit4::class)
class HomeOrderStoreTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var savedPrefs: Map<String, Any?>

    @Before
    fun clearOrder() {
        savedPrefs = PrefsSnapshot.capture(context)
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun restoreOrder() {
        PrefsSnapshot.restore(context, savedPrefs)
    }

    private fun app(packageName: String) = TvApp(
        packageName = packageName,
        activityName = "$packageName.MainActivity",
        label = packageName,
        banner = null,
        icon = null,
    )

    @Test
    fun a_folder_round_trips_through_the_preferences() {
        val apps = listOf(app("a.one"), app("a.two"), app("a.three"))
        val store = HomeOrderStore(context)
        store.saveOrder(
            listOf(
                HomeItem.FolderItem("keep-me", "Кино", listOf(apps[1], apps[2])),
                HomeItem.AppItem(apps[0]),
            ),
        )

        val restored = store.applyOrder(apps)

        assertEquals(2, restored.size)
        val folder = restored[0] as HomeItem.FolderItem
        assertEquals("keep-me", folder.id)
        assertEquals("Кино", folder.name)
        assertEquals(listOf("a.two", "a.three"), folder.apps.map { it.packageName })
        assertEquals("a.one", (restored[1] as HomeItem.AppItem).app.packageName)
    }

    @Test
    fun corrupt_order_falls_back_to_the_install_order() {
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LauncherPrefs.KEY_HOME_ORDER, "{not json")
            .commit()

        val apps = listOf(app("a.one"), app("a.two"))
        val restored = HomeOrderStore(context).applyOrder(apps)

        assertEquals(listOf("a.one", "a.two"), restored.map { it.key })
        assertTrue(restored.all { it is HomeItem.AppItem })
        assertEquals(
            "{not json",
            context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
                .getString("order_quarantine", null),
        )
    }

    @Test
    fun an_app_installed_later_is_appended_and_remembered() {
        val apps = listOf(app("a.one"), app("a.two"))
        val store = HomeOrderStore(context)
        store.saveOrder(listOf(HomeItem.AppItem(apps[0])))

        val restored = store.applyAndRememberOrder(apps)

        assertEquals(listOf("a.one", "a.two"), restored.map { it.key })
        assertEquals(listOf("a.one", "a.two"), savedPackages(store))
    }

    @Test
    fun a_complete_order_is_not_rewritten_on_every_load() {
        val raw = """[ "a.two" , "a.one" ]"""
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LauncherPrefs.KEY_HOME_ORDER, raw)
            .commit()
        val store = HomeOrderStore(context)

        val restored = store.applyAndRememberOrder(listOf(app("a.one"), app("a.two")))

        assertEquals(listOf("a.two", "a.one"), restored.map { it.key })
        assertEquals(raw, store.savedOrder())
    }

    @Test
    fun a_corrupt_order_is_replaced_when_remembering() {
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LauncherPrefs.KEY_HOME_ORDER, "{not json")
            .commit()
        val store = HomeOrderStore(context)

        store.applyAndRememberOrder(listOf(app("a.one"), app("a.two")))

        assertEquals(listOf("a.one", "a.two"), savedPackages(store))
    }

    // The folder is a system bucket, so its stored caption must not survive a
    // language change that the launcher now renders in another language. The
    // stored name is a sentinel: a real caption would match on a device that
    // already runs in that language and hide the bug.
    @Test
    fun the_hidden_folder_name_follows_the_launcher_language() {
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                LauncherPrefs.KEY_HOME_ORDER,
                """[{"id":"hidden","name":"Stale name","apps":["a.one"]}]""",
            )
            .commit()

        val restored = HomeOrderStore(context).applyOrder(listOf(app("a.one")))

        assertEquals(
            context.getString(R.string.hidden_folder_name),
            (restored.single() as HomeItem.FolderItem).name,
        )
    }

    @Test
    fun a_repaired_folder_id_survives_the_next_read() {
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                LauncherPrefs.KEY_HOME_ORDER,
                """[{"name":"Folder","apps":["a.one"]}]""",
            )
            .commit()

        val store = HomeOrderStore(context)
        val first = store.applyOrder(listOf(app("a.one")))
        val firstId = (first.single() as HomeItem.FolderItem).id
        // applyOrder must persist the generated id; the next read cannot
        // invent a different UUID (AQ-28).
        val second = store.applyOrder(listOf(app("a.one")))
        assertEquals(firstId, (second.single() as HomeItem.FolderItem).id)
    }

    @Test
    fun a_missing_package_keeps_its_saved_position_and_is_marked_unavailable() {
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(
                LauncherPrefs.KEY_HOME_ORDER,
                """["a.one","a.gone","a.two"]""",
            )
            .commit()

        val restored = HomeOrderStore(context).applyOrder(
            listOf(app("a.one"), app("a.two")),
        )

        assertEquals(listOf("a.one", "a.gone", "a.two"), restored.map { it.key })
        val missing = (restored[1] as HomeItem.AppItem).app
        assertEquals("a.gone", missing.packageName)
        assertFalse(missing.available)
    }

    @Test
    fun a_missing_built_in_is_dropped_while_a_missing_user_app_keeps_its_placeholder() {
        val repaired = buildHomeOrderRepair(
            saved = """["sys.gone","user.gone","a.one"]""",
            apps = listOf(app("a.one")),
            defaultFolderName = "Папка",
            keepMissing = { it == "user.gone" },
        )

        assertEquals(listOf("user.gone", "a.one"), repaired.items.map { it.key })
        assertFalse((repaired.items[0] as HomeItem.AppItem).app.available)
    }

    private fun savedPackages(store: HomeOrderStore): List<String> {
        val entries = JSONArray(store.savedOrder())
        return (0 until entries.length()).map { entries.getString(it) }
    }
}
