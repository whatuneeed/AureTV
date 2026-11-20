package app.aure.tv.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.aure.tv.data.AppArtCache

/**
 * Registers a painted bitmap in the app art cache and returns the key a
 * [app.aure.tv.data.TvApp] now carries instead of pixels.
 * Tests that only need a solid colour use this; the grid resolves it back in
 * the same frame because the entry is already in the cache.
 */
fun testArtKey(name: String, bitmap: Bitmap): String {
    val key = "test|$name|banner|400x240"
    AppArtCache.put(key, bitmap.asImageBitmap())
    return key
}
