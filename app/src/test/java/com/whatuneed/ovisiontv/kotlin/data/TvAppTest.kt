package com.whatuneed.ovisiontv.kotlin.data

import com.whatuneed.ovisiontv.kotlin.SettingsActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvAppTest {

    private fun app(pkg: String, activity: String) =
        TvApp(pkg, activity, "label", null, null)

    @Test
    fun theOwnSettingsActivityIsTheGatedTile() {
        val own = "com.whatuneed.ovisiontv.kotlin"
        assertTrue(
            app(own, SettingsActivity::class.java.name).isSettingsTile(own),
        )
    }

    @Test
    fun otherActivitiesOfTheSamePackageAreNotTheTile() {
        val own = "com.whatuneed.ovisiontv.kotlin"
        assertFalse(app(own, "$own.MainActivity").isSettingsTile(own))
    }

    @Test
    fun theSettingsActivityOfAnotherPackageIsNotTheTile() {
        val own = "com.whatuneed.ovisiontv.kotlin"
        assertFalse(
            app("com.android.settings", SettingsActivity::class.java.name)
                .isSettingsTile(own),
        )
    }
}
