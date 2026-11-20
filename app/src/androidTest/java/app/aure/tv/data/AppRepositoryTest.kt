package app.aure.tv.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aure.tv.MainActivity
import app.aure.tv.R
import app.aure.tv.SettingsActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The grid is built from the real PackageManager, so the rules that keep the
 * launcher's own tile usable are checked against the installed package: the Home
 * activity must not become a tile, and Settings must keep the gear instead of
 * the app banner (05_FOLDERS / 02_HOME_SCREEN §2).
 */
@RunWith(AndroidJUnit4::class)
class AppRepositoryTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private fun load(): List<TvApp> = runBlocking { AppRepository(context).load() }

    @Test
    fun the_settings_activity_is_the_launchers_only_tile() {
        val apps = load()
        val own = apps.filter { it.packageName == context.packageName }

        assertEquals(1, own.size)
        assertEquals(SettingsActivity::class.java.name, own[0].activityName)
        assertEquals(LocalePreference.wrap(context).getString(R.string.settings_tile_name), own[0].label)
        // No banner: an application banner would replace the slider gear.
        assertNull(own[0].banner)
        assertTrue("settings tile needs its icon", own[0].icon != null)
        assertTrue(apps.none { it.activityName == MainActivity::class.java.name })
    }

    @Test
    fun a_saved_custom_name_replaces_the_package_label_and_can_be_reset() {
        AppLabelStore.setLabel(context, context.packageName, "  Мой дом  ")
        try {
            val own = load().single { it.packageName == context.packageName }
            assertEquals("Мой дом", own.label)
            assertEquals(
                LocalePreference.wrap(context).getString(R.string.settings_tile_name),
                AppLabelStore.defaultLabel(context, own),
            )
        } finally {
            AppLabelStore.clearLabel(context, context.packageName)
        }
        val reset = load().single { it.packageName == context.packageName }
        assertEquals(LocalePreference.wrap(context).getString(R.string.settings_tile_name), reset.label)
    }

    @Test
    fun a_profile_reads_its_own_custom_name() = runBlocking {
        val profilePrefs = LauncherPrefs.NAME + "_labels_test"
        AppLabelStore.setLabel(context, context.packageName, "Второй профиль", profilePrefs)
        try {
            val main = AppRepository(context).load(prefsName = LauncherPrefs.NAME)
                .single { it.packageName == context.packageName }
            val other = AppRepository(context).load(prefsName = profilePrefs)
                .single { it.packageName == context.packageName }
            assertEquals(LocalePreference.wrap(context).getString(R.string.settings_tile_name), main.label)
            assertEquals("Второй профиль", other.label)
        } finally {
            AppLabelStore.clear(context, profilePrefs)
        }
    }

    @Test
    fun a_successful_scan_is_reported_as_loaded() = runBlocking {
        val result = AppRepository(context).loadResult(withArtwork = false)

        assertTrue(result is AppLoadResult.Loaded)
        assertTrue((result as AppLoadResult.Loaded).apps.isNotEmpty())
    }

    @Test
    fun every_tile_has_a_label_and_a_unique_package() {
        val apps = load()

        assertTrue(apps.isNotEmpty())
        assertTrue(apps.all { it.label.isNotBlank() })
        assertEquals(apps.size, apps.map { it.packageName }.toSet().size)
        assertTrue(apps.all { it.activityName.isNotBlank() })
    }
}
