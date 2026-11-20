package app.aure.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.aure.tv.data.AppArtCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a [app.aure.tv.data.TvApp] artwork key to pixels.
 *
 * The tile keeps no bitmap of its own (audit A-01): after [AppArtCache.trim] the
 * cache is the only owner, so the eviction really returns memory. The epoch that
 * the cache bumps on every trim is read here, which re-runs the lookup and the
 * reload — a tile that still remembered a dropped bitmap would pin the whole
 * grid in memory again, exactly the defect being fixed.
 *
 * The reload is asynchronous: while it runs the card draws its own fallback
 * (letter on the card gradient). Home's entrance transition covers the resume
 * that follows a background trim, so this is not a visible flash.
 */
@Composable
internal fun rememberArt(key: String?): ImageBitmap? {
    if (key == null) return null
    val context = LocalContext.current
    val epoch = AppArtCache.epoch.intValue
    var art by remember(key, epoch) { mutableStateOf(AppArtCache.get(key)) }
    LaunchedEffect(key, epoch) {
        if (art == null) art = withContext(Dispatchers.IO) { AppArtCache.load(context, key) }
    }
    return art
}
