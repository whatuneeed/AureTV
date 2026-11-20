package app.aure.tv.ui

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pins the receiver registration, not just the reload decision. */
@RunWith(AndroidJUnit4::class)
class PackageChangeFilterTest {

    @Test
    fun filter_covers_every_package_event_the_launcher_reloads_on() {
        val filter = packageChangeFilter()
        for (action in listOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_CHANGED,
            Intent.ACTION_PACKAGE_REPLACED,
        )) {
            assertTrue("missing $action", filter.hasAction(action))
        }
        assertEquals(1, filter.countDataSchemes())
        assertEquals("package", filter.getDataScheme(0))
    }
}
