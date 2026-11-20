package app.aure.tv.data

import android.content.Context
import java.io.File

/**
 * Older builds kept up to 512 MiB of streamed clips in `cache/aerial_media`.
 * Clips are streamed now, so that directory is removed on launch instead of
 * sitting on the TV's storage.
 */
internal object AerialClipCache {
    private const val DIR = "aerial_media"

    fun cleanup(context: Context) {
        runCatching {
            File(context.applicationContext.cacheDir, DIR).deleteRecursively()
        }
    }
}
