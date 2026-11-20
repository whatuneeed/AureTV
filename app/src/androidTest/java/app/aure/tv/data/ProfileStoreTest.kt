package app.aure.tv.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A deleted profile's id must never come back: a new profile would inherit the
 * old layout, PIN and allowlist. The identity floor is persisted before the
 * list write, so even a crash between the two durable steps is safe (AQ-10).
 */
@RunWith(AndroidJUnit4::class)
class ProfileStoreTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val files = listOf(
        ProfileStore.FILE,
        LauncherPrefs.NAME + "_p2",
        KidsStore.FILE,
    )
    private lateinit var backup: Map<String, Map<String, *>>

    @Before
    fun savePrefs() {
        backup = files.associateWith { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all
        }
    }

    @After
    fun restorePrefs() {
        files.forEach { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            @Suppress("UNCHECKED_CAST")
            val values = backup[name].orEmpty() as Map<String, Any>
            val editor: android.content.SharedPreferences.Editor = prefs.edit()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
        }
    }

    @Test
    fun a_deleted_profile_id_is_not_reused() {
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = ProfileStore(context)
        assertEquals("p1", store.list().first().id)
        val second = store.add("A")!!
        assertEquals("p2", second.id)
        assertTrue(store.delete(second.id))
        val third = store.add("B")!!
        assertNotEquals(second.id, third.id)
        assertEquals("p3", third.id)
    }

    @Test
    fun a_repaired_profile_list_keeps_the_original_raw_value() {
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit().clear().putString("list", "{not json").commit()

        val profiles = ProfileStore(context).list()

        assertEquals(1, profiles.size)
        assertEquals(
            "{not json",
            context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
                .getString("list_quarantine", null),
        )
    }

    @Test
    fun a_repeated_delete_finishes_cleanup_of_orphaned_profile_data() {
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = ProfileStore(context)
        store.list()
        val second = store.add("A")!!
        assertTrue(store.delete(second.id))

        // Simulate a crash after the list write but before the cleanup writes:
        // stale layout, discovery history, allowlist and PIN remain behind.
        val layout = context.getSharedPreferences(
            ProfileStore.homePrefsName(second.id),
            Context.MODE_PRIVATE,
        )
        layout.edit()
            .putString(LauncherPrefs.KEY_HOME_ORDER, "[]")
            .putString("discovery_usage", "{}")
            .commit()
        AppLabelStore.setLabel(
            context,
            "com.example.orphan",
            "Старое имя",
            ProfileStore.homePrefsName(second.id),
        )
        val kids = KidsStore(context)
        kids.setAllowlist(second.id, setOf("orphan.app"))
        runBlocking { kids.setPin(second.id, "1234") }

        assertEquals(false, store.delete(second.id))

        assertEquals(null, layout.all[LauncherPrefs.KEY_HOME_ORDER])
        assertEquals(null, layout.all["discovery_usage"])
        assertEquals(
            null,
            AppLabelStore.label(
                context,
                "com.example.orphan",
                ProfileStore.homePrefsName(second.id),
            ),
        )
        assertTrue(kids.allowlist(second.id).isEmpty())
        assertEquals(false, kids.hasPin(second.id))
    }

    @Test
    fun the_floor_survives_a_restart_of_the_store() {
        context.getSharedPreferences(ProfileStore.FILE, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = ProfileStore(context)
        store.list()
        val second = store.add("A")!!
        store.delete(second.id)
        // A fresh instance reads the persisted floor, not just the live list.
        val reopened = ProfileStore(context)
        assertEquals("p3", reopened.add("B")!!.id)
    }
}
