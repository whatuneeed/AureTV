package com.whatuneed.ovisiontv.kotlin.discovery

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Profile isolation is a storage promise, not a ranking one: the JVM codecs are
 * covered by DiscoveryStoreCodecTest, while this suite proves the Android
 * SharedPreferences file and the legacy keys behave as the engine assumes.
 */
@RunWith(AndroidJUnit4::class)
class DiscoveryStoreTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableSetOf<String>()

    private fun prefs(name: String): SharedPreferences {
        names += name
        return context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    @After
    fun cleanUp() {
        names.forEach { name ->
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    private fun item(id: String, packageName: String = "app.one") = DiscoveryItem(
        id = id,
        source = DiscoverySource.TV_PROVIDER,
        kind = DiscoveryKind.VIDEO,
        packageName = packageName,
        title = id,
        intentUri = "intent://$id#Intent;package=$packageName;end",
    )

    @Test
    fun two_profiles_never_share_usage_favorites_or_hidden_cards() {
        val a = DiscoveryStore(context, "test_discovery_a")
        val b = DiscoveryStore(context, "test_discovery_b")

        a.recordLaunch("app.one", nowUtc = 1_000L)
        a.addFavorite(item("fav"))
        a.hide(item("hidden"))

        assertTrue(b.usage().isEmpty())
        assertTrue(b.favorites().isEmpty())
        assertTrue(b.hidden().isEmpty())
        assertEquals(1, a.usage().getValue("app.one").count)
    }

    @Test
    fun clearing_history_keeps_explicit_favorites_and_hidden_cards() {
        val store = DiscoveryStore(context, "test_discovery_clear")
        store.recordLaunch("app.one", nowUtc = 1_000L)
        store.addFavorite(item("fav"))
        store.hide(item("hidden"))
        store.markShown(DiscoverySection.CONTINUE, listOf(item("shown")), nowUtc = 2_000L)

        store.clearHistory()

        assertTrue(store.usage().isEmpty())
        assertTrue(store.shownAt().isEmpty())
        assertEquals(listOf("fav"), store.favorites().map { it.id })
        assertEquals(listOf("hidden"), store.hidden().map { it.id })
    }

    @Test
    fun deleting_a_profile_erases_its_discovery_keys_but_nothing_else() {
        val name = "test_discovery_delete"
        val store = DiscoveryStore(context, name)
        val settings = DiscoverySettings(context, name)
        store.recordLaunch("app.one")
        store.addFavorite(item("fav"))
        settings.setPersonalization(false)
        // A layout key lives in the same file and must survive profile cleanup.
        prefs(name).edit().putString("home_order", "[]").commit()

        store.clearAll()

        assertTrue(store.usage().isEmpty())
        assertTrue(store.favorites().isEmpty())
        // Defaults come back: a reused profile id starts clean.
        assertTrue(settings.personalization())
        assertEquals("[]", prefs(name).getString("home_order", null))
    }

    @Test
    fun settings_read_and_write_the_legacy_content_keys() {
        val name = "test_discovery_legacy"
        val settings = DiscoverySettings(context, name)
        prefs(name).edit()
            .putBoolean("content_recommendations", false)
            .putBoolean("content_continue_watching", false)
            .commit()

        assertFalse(settings.recommendations())
        assertFalse(settings.continueWatching())

        settings.setRecommendations(true)
        settings.setContinueWatching(true)
        assertTrue(prefs(name).getBoolean("content_recommendations", false))
        assertTrue(prefs(name).getBoolean("content_continue_watching", false))
    }

    @Test
    fun a_second_launch_updates_the_count_and_the_time() {
        val store = DiscoveryStore(context, "test_discovery_usage")
        store.recordLaunch("app.one", nowUtc = 1_000L)
        store.recordLaunch("app.one", nowUtc = 5_000L)

        val usage = store.usage().getValue("app.one")
        assertEquals(2, usage.count)
        assertEquals(5_000L, usage.lastUtc)
    }
}
