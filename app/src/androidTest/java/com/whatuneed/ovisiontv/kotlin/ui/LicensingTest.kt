package com.whatuneed.ovisiontv.kotlin.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The licences page must describe the APK it ships in. The previous text named
 * a font licence and a catalogue file that were never bundled, so a reader
 * could not verify any of it.
 */
@RunWith(AndroidJUnit4::class)
class LicensingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun document(): String = context.resources.openRawResource(R.raw.open_source_licenses)
        .bufferedReader()
        .use { it.readText() }

    @Test
    fun the_licences_document_names_only_files_that_are_bundled() {
        val text = document()
        val assets = context.assets.list("").orEmpty().toSet()

        assertTrue("aerial_catalog.json is missing from assets", assets.contains("aerial_catalog.json"))
        assertFalse(assets.contains("aerials.json"))
        assertFalse(assets.contains("fonts"))

        assertTrue(text.contains("Apache License"))
        assertTrue(text.contains("aerial_catalog.json"))
        assertTrue(text.contains("GPL-3.0"))
        assertTrue(text.contains("Apple"))
        assertFalse(text.contains("Inter-LICENSE"))
        assertFalse(text.contains("assets/aerials.json"))
    }
}
