package com.whatuneed.ovisiontv.kotlin.ui.components

import com.whatuneed.ovisiontv.kotlin.data.resolveRedirectUrl
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.whatuneed.ovisiontv.kotlin.data.Adaptive
import com.whatuneed.ovisiontv.kotlin.data.isAerialMediaUrl
import com.whatuneed.ovisiontv.kotlin.data.isAerialPreviewUrl
import com.whatuneed.ovisiontv.kotlin.data.trimmedCapacity
import com.whatuneed.ovisiontv.kotlin.data.writeFileAtomically
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/**
 * Still frames for the Settings preview. Current Apple aerials ship a 900×580
 * PNG; legacy clips whose public entry no longer has one use a real frame
 * captured from that same clip's video and bundled under `assets/`. Settings
 * never starts a second video decoder just to draw a tile. Network bytes live
 * in the app cache; losing them only costs one download on the next visit.
 */
internal object AerialPreviewStore {
    private const val DIR = "aerial_previews"
    private const val THUMB_DIR = "aerial_previews/thumbs"
    private const val KEEP_FILES = 8
    // The picker keeps a thumbnail for every catalogue clip, so 120 covers the
    // whole 117-clip catalogue and still prunes a catalogue that grows.
    private const val KEEP_THUMBS = 120
    // Count alone is not a budget: 120 full-size PNGs can occupy more disk
    // than the whole app cache allowance. Thumbnails are the first to go.
    private const val THUMB_MAX_BYTES = 32L * 1024 * 1024
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_REDIRECTS = 3
    private const val COPY_CHUNK = 64 * 1024
    private const val THUMB_JPEG_QUALITY = 82

    private val memory = object : LruCache<String, Bitmap>(Adaptive.budgets.previewBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val thumbMemory =
        object : LruCache<String, Bitmap>(Adaptive.budgets.previewThumbBytes) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

    /** Memory hit without the IO hop: the saver's first frame needs it now. */
    fun cached(url: String): Bitmap? = memory.get(url)

    /** A tile seeded from memory must not wait for an IO dispatcher hop. */
    fun cachedThumbnail(url: String): Bitmap? = thumbMemory.get(url)

    /** Grid tiles draw 450×290 stills; half sampling keeps them affordable. */
    suspend fun loadThumbnail(context: Context, url: String): Bitmap? {
        if (!isAerialPreviewUrl(url)) return null
        thumbMemory.get(url)?.let { return it }
        val dir = File(context.applicationContext.cacheDir, THUMB_DIR)
        val fileName = name(url)
        val file = File(dir, fileName)
        if (file.isFile) {
            val cached = runCatching { file.readBytes() }.getOrNull()?.let { decodeSampled(it, 2) }
            if (cached != null) {
                thumbMemory.put(url, cached)
                return cached
            }
            // A corrupt entry must not be re-decoded on every visit.
            file.delete()
        }
        val full = read(url, context) ?: return null
        val bitmap = decodeSampled(full, 2) ?: return null
        // Keep a 450×290 JPEG, not the 0.5–1 MB Apple PNG. The picker can
        // then hold the whole catalogue on disk instead of pruning most of it.
        val thumb = ByteArrayOutputStream().also { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, out)
        }.toByteArray()
        save(dir, fileName, thumb, KEEP_THUMBS, THUMB_MAX_BYTES)
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

    suspend fun load(context: Context, url: String): Bitmap? {
        if (!isAerialPreviewUrl(url)) return null
        memory.get(url)?.let { return it }

        val dir = File(context.applicationContext.cacheDir, DIR)
        val fileName = name(url)
        val file = File(dir, fileName)
        val bytes = if (file.isFile) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            read(url, context)?.also { save(dir, fileName, it, KEEP_FILES) }
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

    private suspend fun read(url: String, context: Context): ByteArray? =
        if (url.startsWith("asset:")) {
            runCatching {
                context.assets.open(url.removePrefix("asset:")).use { it.readBytes() }
            }.getOrNull()
        } else {
            download(url)
        }

    private suspend fun download(url: String): ByteArray? {
        var current = url
        repeat(MAX_REDIRECTS + 1) { attempt ->
            if (!isAerialMediaUrl(current)) return null
            val result = runCatching {
                val connection = URL(current).openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "image/*")
                try {
                    val code = connection.responseCode
                    if (code in 300..399) {
                        if (attempt >= MAX_REDIRECTS) return null
                        val location = connection.getHeaderField("Location") ?: return null
                        val next = resolveRedirectUrl(current, location) ?: return null
                        if (!isAerialMediaUrl(next) || !imageRedirectIsTrusted(current, next)) {
                            return null
                        }
                        current = next
                        return@repeat
                    }
                    if (code != HttpURLConnection.HTTP_OK) return null
                    if (connection.contentLengthLong > MAX_BYTES) return null
                    return connection.inputStream.use { it.readAtMost(MAX_BYTES) }
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                null
            }
            return result
        }
        return null
    }

    private fun save(
        dir: File,
        name: String,
        bytes: ByteArray,
        keep: Int,
        maxBytes: Long = Long.MAX_VALUE,
    ) {
        runCatching {
            if (!dir.isDirectory && !dir.mkdirs()) return
            val target = File(dir, name)
            // A unique temp plus a checked replace keeps a half-written preview
            // from being decoded later and never deletes the old file first.
            if (!writeFileAtomically(target, bytes)) return
            prune(dir, keep, maxBytes)
        }
    }

    private fun prune(dir: File, keep: Int, maxBytes: Long) {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".png") } ?: return
        previewFilesToDelete(files.toList(), keep, maxBytes).forEach { it.delete() }
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


/**
 * Oldest files to delete until both the count and the byte budget fit. Kept as
 * a pure function so the disk cap can be checked without a device cache.
 */
internal fun previewFilesToDelete(files: List<File>, keep: Int, maxBytes: Long): List<File> {
    if (files.isEmpty()) return emptyList()
    val ordered = files.sortedBy { it.lastModified() }
    var count = ordered.size
    var total = ordered.sumOf { it.length() }
    val remove = ArrayList<File>()
    for (file in ordered) {
        if (count <= keep && total <= maxBytes) break
        remove += file
        total -= file.length()
        count--
    }
    return remove
}
