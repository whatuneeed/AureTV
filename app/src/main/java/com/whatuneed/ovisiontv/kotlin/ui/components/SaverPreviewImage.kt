package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.trimmedCapacity
import com.whatuneed.ovisiontv.kotlin.data.writeFileAtomically
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Still frames for the Settings preview. Apple ships a 900×580 PNG next to
 * every aerial, so Settings shows a real frame instead of starting a second
 * video decoder. The bytes live in the app cache; losing them only costs one
 * download on the next visit.
 */
internal object AerialPreviewStore {
    private const val DIR = "aerial_previews"
    private const val THUMB_DIR = "aerial_previews/thumbs"
    private const val KEEP_FILES = 8
    // The picker keeps a thumbnail for every catalogue clip, so 120 covers the
    // whole 100-clip catalogue and still prunes a catalogue that grows.
    private const val KEEP_THUMBS = 120
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val COPY_CHUNK = 64 * 1024

    private val memory = object : LruCache<String, Bitmap>(Adaptive.budgets.previewBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val thumbMemory =
        object : LruCache<String, Bitmap>(Adaptive.budgets.previewThumbBytes) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

    /** Memory hit without the IO hop: the saver's first frame needs it now. */
    fun cached(url: String): Bitmap? = memory.get(url)

    /** Grid tiles draw 450×290 stills; half sampling keeps 100 of them affordable. */
    fun loadThumbnail(context: Context, url: String): Bitmap? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        thumbMemory.get(url)?.let { return it }
        val dir = File(context.applicationContext.cacheDir, THUMB_DIR)
        val fileName = name(url)
        val file = File(dir, fileName)
        val bytes = if (file.isFile) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            download(url)?.also { save(dir, fileName, it, KEEP_THUMBS) }
        } ?: return null
        val bitmap = decodeSampled(bytes, 2)
        if (bitmap == null) {
            // A corrupt entry must not be re-decoded on every visit.
            file.delete()
            return null
        }
        thumbMemory.put(url, bitmap)
        return bitmap
    }

    fun size(context: Context): Long =
        File(context.applicationContext.cacheDir, DIR).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }

    fun clear(context: Context) {
        memory.evictAll()
        thumbMemory.evictAll()
        runCatching {
            File(context.applicationContext.cacheDir, DIR).deleteRecursively()
        }
    }

    /** Thermal throttling may lower the profile after the caches were sized. */
    fun shrinkTo(bytes: Int, thumbBytes: Int) {
        val target = bytes.coerceAtLeast(1)
        val thumbTarget = thumbBytes.coerceAtLeast(1)
        if (target < memory.maxSize()) {
            memory.resize(target)
            memory.trimToSize(target)
        }
        if (thumbTarget < thumbMemory.maxSize()) {
            thumbMemory.resize(thumbTarget)
            thumbMemory.trimToSize(thumbTarget)
        }
    }

    /** Drops decoded stills under pressure; the files on disk stay. */
    fun trim(percent: Int) {
        if (percent >= 100) {
            val max = memory.maxSize()
            val thumbMax = thumbMemory.maxSize()
            memory.evictAll()
            thumbMemory.evictAll()
            memory.resize(trimmedCapacity(max, 100))
            thumbMemory.resize(trimmedCapacity(thumbMax, 100))
        } else {
            val target = trimmedCapacity(memory.maxSize(), percent)
            val thumbTarget = trimmedCapacity(thumbMemory.maxSize(), percent)
            memory.resize(target)
            memory.trimToSize(target)
            thumbMemory.resize(thumbTarget)
            thumbMemory.trimToSize(thumbTarget)
        }
    }

    fun load(context: Context, url: String): Bitmap? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        memory.get(url)?.let { return it }

        val dir = File(context.applicationContext.cacheDir, DIR)
        val fileName = name(url)
        val file = File(dir, fileName)
        val bytes = if (file.isFile) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            download(url)?.also { save(dir, fileName, it, KEEP_FILES) }
        } ?: return null

        val bitmap = decodeSampled(bytes, 1)
        if (bitmap == null) {
            // A corrupt entry must not be re-decoded on every visit.
            file.delete()
            return null
        }
        memory.put(url, bitmap)
        return bitmap
    }

    private fun decodeSampled(bytes: ByteArray, sample: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, sample)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private fun download(url: String): ByteArray? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "image/*")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            val out = ByteArrayOutputStream(COPY_CHUNK)
            connection.inputStream.use { input ->
                val buffer = ByteArray(COPY_CHUNK)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return null
                    out.write(buffer, 0, read)
                }
            }
            out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun save(dir: File, name: String, bytes: ByteArray, keep: Int) {
        runCatching {
            if (!dir.isDirectory && !dir.mkdirs()) return
            val target = File(dir, name)
            // A unique temp plus a checked replace keeps a half-written preview
            // from being decoded later and never deletes the old file first.
            if (!writeFileAtomically(target, bytes)) return
            prune(dir, keep)
        }
    }

    private fun prune(dir: File, keep: Int) {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".png") } ?: return
        if (files.size <= keep) return
        files.sortedBy { it.lastModified() }
            .take(files.size - keep)
            .forEach { it.delete() }
    }

    private fun name(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".png"
}

// ~16 MB of ARGB_8888; Apple's 900x580 stills pass without downsampling.
private const val MAX_PIXELS = 4_000_000

internal fun sampleFor(width: Int, height: Int, minSample: Int): Int {
    var sample = minSample.coerceAtLeast(1)
    while ((width / sample).toLong() * (height / sample) > MAX_PIXELS) sample *= 2
    return sample
}
