package com.whatuneed.ovisiontv.kotlin.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes one photo at the size the TV can actually draw. A 40 MP camera file
 * must not allocate 160 MB on a 1 GB television; the decoder is given a target
 * size before the bitmap is allocated. Software allocation keeps the bitmap
 * readable by Compose without a hardware bitmap lifetime surprise.
 */
object AmbientPhotoDecoder {
    const val MAX_WIDTH = 1920
    const val MAX_HEIGHT = 1080
    private const val TAG = "AmbientPhotoDecoder"

    suspend fun decode(
        context: Context,
        uri: Uri,
        maxWidth: Int = MAX_WIDTH,
        maxHeight: Int = MAX_HEIGHT,
    ): Bitmap? = withContext(Dispatchers.IO) {
        decodeNow(context, uri, maxWidth, maxHeight)
    }

    internal fun decodeNow(
        context: Context,
        uri: Uri,
        maxWidth: Int = MAX_WIDTH,
        maxHeight: Int = MAX_HEIGHT,
    ): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val width = info.size.width
            val height = info.size.height
            if (width > 0 && height > 0 && maxWidth > 0 && maxHeight > 0) {
                val scale = min(
                    min(maxWidth.toFloat() / width, maxHeight.toFloat() / height),
                    1f,
                )
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "photo decode failed", error)
        null
    } catch (error: OutOfMemoryError) {
        Log.w(TAG, "photo decode ran out of memory")
        null
    }
}
