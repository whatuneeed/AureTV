package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
