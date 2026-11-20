package com.whatuneed.ovisiontv.kotlin.ui

import com.whatuneed.ovisiontv.kotlin.data.TvApp
import org.junit.Assert.assertEquals
import org.junit.Test

class KidsSettingsTest {

    private fun app(pkg: String, label: String) = TvApp(pkg, "$pkg.Main", label, null, null)

    @Test
    fun theLauncherItselfNeverAppearsInTheAllowlist() {
        val apps = listOf(
            app("tv.kinopoisk", "КиноПоиск"),
            app("com.whatuneed.ovisiontv.kotlin", "AURE TV"),
        )
        assertEquals(
            listOf("tv.kinopoisk"),
            kidsAllowlistApps(apps, "com.whatuneed.ovisiontv.kotlin").map { it.packageName },
        )
    }

    @Test
    fun appsAreSortedByLabelIgnoringCase() {
        // Java sorted with compareToIgnoreCase, which orders by code point:
        // Latin labels come before Cyrillic ones. The port keeps the same
        // rule so both launchers list the allowlist identically.
        val apps = listOf(
            app("b", "youtube"),
            app("a", "Кинопоиск"),
            app("c", "IVI"),
            app("d", "ivi"),
        )
        assertEquals(
            listOf("IVI", "ivi", "youtube", "Кинопоиск"),
            kidsAllowlistApps(apps, "launcher").map { it.label },
        )
    }
}
