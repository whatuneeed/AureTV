package com.whatuneed.ovisiontv.kotlin.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The wizard's backdrop is a rendered bitmap, so it is exercised on a device:
 * the field has to be screen-sized, every style has to paint something, an
 * unknown name has to fall back to the default, and no two styles may look
 * alike — a copy-paste slip in the palettes would show as one identical colour.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyStylePaintsADistinctField() {
        val rendered = Wallpaper.KEYS.associateWith { Wallpaper.render(it, 320, 180) }
        assertEquals(Wallpaper.KEYS.size, rendered.size)
        rendered.forEach { (style, bitmap) ->
            assertEquals(320, bitmap.width)
            assertEquals(180, bitmap.height)
            // A field that failed to draw would be fully transparent.
            assertNotEquals(0, bitmap.getPixel(160, 90))
        }
        // The five palettes are different colours by design; a duplicated lookup
        // would make two styles byte-identical.
        val colours = Wallpaper.KEYS.map { rendered.getValue(it).getPixel(160, 90) }
        assertEquals(colours.size, colours.toSet().size)
    }

    @Test
    fun anUnknownStyleFallsBackToTheDefault() {
        val fallback = Wallpaper.render("no-such-style", 200, 120)
        val aurora = Wallpaper.render(Wallpaper.DEFAULT, 200, 120)
        assertEquals(aurora.getPixel(100, 60), fallback.getPixel(100, 60))
        assertEquals(aurora.getPixel(10, 10), fallback.getPixel(10, 10))
    }

    @Test
    fun theScreenFieldFollowsTheDisplay() {
        val metrics = context.resources.displayMetrics
        val bitmap = Wallpaper.render(context, Wallpaper.DEFAULT)
        // Java capped the field at 2560 wide; below that it matches the panel.
        assertEquals(metrics.widthPixels.coerceIn(192, 2560), bitmap.width)
        assertTrue(bitmap.height > 0)
    }
}
