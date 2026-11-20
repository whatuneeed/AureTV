package app.aure.tv.ui

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins when the launcher reloads the grid. An update looks like an uninstall
 * followed by an install, so only the install half may rebuild the home.
 */
class PackageChangeTest {

    @Test
    fun update_removal_does_not_reload() {
        assertFalse(reloadsHomeFor(Intent.ACTION_PACKAGE_REMOVED, replacing = true))
    }

    @Test
    fun real_uninstall_reloads() {
        assertTrue(reloadsHomeFor(Intent.ACTION_PACKAGE_REMOVED, replacing = false))
    }

    @Test
    fun install_reloads_with_or_without_replacing() {
        assertTrue(reloadsHomeFor(Intent.ACTION_PACKAGE_ADDED, replacing = false))
        assertTrue(reloadsHomeFor(Intent.ACTION_PACKAGE_ADDED, replacing = true))
    }

    @Test
    fun change_and_replace_reload() {
        assertTrue(reloadsHomeFor(Intent.ACTION_PACKAGE_CHANGED, replacing = false))
        assertTrue(reloadsHomeFor(Intent.ACTION_PACKAGE_REPLACED, replacing = false))
    }

    @Test
    fun actions_outside_the_filter_are_ignored() {
        assertFalse(reloadsHomeFor(null, replacing = false))
        assertFalse(reloadsHomeFor(Intent.ACTION_PACKAGE_FULLY_REMOVED, replacing = false))
        assertFalse(reloadsHomeFor(Intent.ACTION_PACKAGE_DATA_CLEARED, replacing = false))
    }
}
