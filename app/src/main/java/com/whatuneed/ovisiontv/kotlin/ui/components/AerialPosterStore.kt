package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-resolution first frames seen by the saver. Apple's catalogue only ships
 * a 900×580 still, which is soft on a TV; after a clip has played once, the
 * entry still is the decoder's own frame, so the next entrance starts from the
 * exact picture the video begins with. The cache is disposable.
 */
internal object AerialPosterStore {
    private const val DIR = "aerial_posters"
    private const val KEEP_FILES = 16
    // One 1920×1080 ARGB frame is ~8 MiB; the cache has to hold a few of them
    // or the entrance loses its synchronous memory hit on the way in. The
    // profile trims the number of frames it holds on a smaller TV.
    private const val JPEG_QUALITY = 88
    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1080
    // The masters fade in from black; a captured lead-in frame would make the
    // next entrance open on black. Night clips stay well above this.
    private const val MIN_LUMA = 14

    private val memory = object : LruCache<String, Bitmap>(Adaptive.budgets.aerialPosterBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Memory hit without the IO hop: the saver's first frame needs it now. */
    fun cached(clipId: String): Bitmap? = memory.get(clipId)

    fun load(context: Context, clipId: String): Bitmap? {
        memory.get(clipId)?.let { return it }
        val file = file(context, clipId)
        if (!file.isFile) return null
        val bitmap = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
        if (bitmap == null) {
            // A corrupt JPEG would otherwise be decoded and rejected on every
            // saver entrance; it cannot be repaired, so remove that one file.
            file.delete()
            return null
        }
        // Older builds saved the black lead-in; drop such entries so the
        // preview still can take over instead of a black entrance.
        if (isBlack(bitmap)) {
            file.delete()
            return null
        }
        memory.put(clipId, bitmap)
        return bitmap
    }

    suspend fun save(context: Context, clipId: String, frame: Bitmap?) {
        if (frame == null || frame.width <= 0 || frame.height <= 0) return
        withContext(Dispatchers.IO) {
            val poster = fit(frame)
            if (isBlack(poster)) return@withContext
            runCatching {
                val dir = File(context.applicationContext.cacheDir, DIR)
                if (!dir.isDirectory && !dir.mkdirs()) return@runCatching
                val temp = File(dir, "${name(clipId)}.tmp")
                FileOutputStream(temp).use { out ->
                    poster.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                val target = file(context, clipId)
                // Same rule as the still cache: a rename hides half-written files.
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.renameTo(target)
                }
                prune(dir)
            }
            memory.put(clipId, poster)
        }
    }

    fun size(context: Context): Long =
        File(context.applicationContext.cacheDir, DIR).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    fun clear(context: Context) {
        memory.evictAll()
        runCatching {
            File(context.applicationContext.cacheDir, DIR).deleteRecursively()
        }
    }

    /** Drops decoded frames under pressure; the JPEG copies on disk stay. */
    fun trim(percent: Int) {
        if (percent >= 100) memory.evictAll()
        else memory.trimToSize(memory.maxSize() * (100 - percent) / 100)
    }

    /** Thermal throttling may lower the profile after the cache was sized. */
    fun shrinkTo(bytes: Int) {
        memory.trimToSize(bytes)
    }

    private fun isBlack(frame: Bitmap): Boolean {
        val sample = runCatching {
            Bitmap.createScaledBitmap(frame, 16, 9, false)
        }.getOrNull() ?: return false
        val pixels = IntArray(16 * 9)
        sample.getPixels(pixels, 0, 16, 0, 0, 16, 9)
        if (sample !== frame) sample.recycle()
        var sum = 0L
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            sum += (r * 299 + g * 587 + b * 114) / 1000
        }
        return sum / pixels.size < MIN_LUMA
    }

    private fun fit(frame: Bitmap): Bitmap {
        if (frame.width <= MAX_WIDTH && frame.height <= MAX_HEIGHT) return frame
        val ratio = minOf(
            MAX_WIDTH.toFloat() / frame.width,
            MAX_HEIGHT.toFloat() / frame.height,
        )
        return Bitmap.createScaledBitmap(
            frame,
            (frame.width * ratio).toInt().coerceAtLeast(1),
            (frame.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun file(context: Context, clipId: String): File =
        File(File(context.applicationContext.cacheDir, DIR), "${name(clipId)}.jpg")

    private fun prune(dir: File) {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".jpg") } ?: return
        if (files.size <= KEEP_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - KEEP_FILES)
            .forEach { it.delete() }
    }

    private fun name(clipId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(clipId.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
