package app.aure.tv.ambient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import app.aure.tv.data.Adaptive
import app.aure.tv.data.LauncherUserAgent
import app.aure.tv.data.resolveRedirectUrl
import app.aure.tv.data.writeFileAtomically
import app.aure.tv.core.network.readAtMost
import kotlinx.coroutines.CancellationException
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Remote images from Reddit's image CDN. The feed is third-party input, so the
 * loader validates every URL and every redirect against the same host allowlist
 * as the parser, never follows a redirect to an arbitrary host, and caps both
 * the bytes and the decoded pixels. Images are memory/disk cache only.
 */
object RedditImageStore {
    private const val DIR = "reddit_images"
    private const val KEEP_FILES = 180
    private const val DISK_MAX_BYTES = 64L * 1024 * 1024
    private const val TIMEOUT_MS = 12_000
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val COPY_CHUNK = 64 * 1024
    private const val MAX_REDIRECTS = 3
    private const val TAG = "RedditImage"

    private val thumbs = object : LruCache<String, Bitmap>(
        (Adaptive.budgets.previewThumbBytes / 2).coerceIn(2 * 1024 * 1024, 8 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val full = object : LruCache<String, Bitmap>(
        (Adaptive.budgets.previewBytes / 2).coerceIn(2 * 1024 * 1024, 6 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(url: String): Bitmap? = thumbs.get(url) ?: full.get(url)

    suspend fun loadThumbnail(context: Context, url: String): Bitmap? =
        load(context, url, thumbnail = true)

    suspend fun loadFull(context: Context, url: String): Bitmap? =
        load(context, url, thumbnail = false)

    fun clear(context: Context) {
        thumbs.evictAll()
        full.evictAll()
        runCatching {
            File(context.applicationContext.cacheDir, DIR).deleteRecursively()
        }
    }

    private suspend fun load(context: Context, raw: String, thumbnail: Boolean): Bitmap? {
        val url = RedditFeedParser.safeImageUrl(raw) ?: return null
        val memory = if (thumbnail) thumbs else full
        memory.get(url)?.let { return it }

        val dir = File(context.applicationContext.cacheDir, DIR)
        val fileName = name(url)
        val file = File(dir, fileName)
        val bytes = if (file.isFile) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            download(url)?.also { save(dir, fileName, it) }
        } ?: return null

        val bitmap = decode(bytes, if (thumbnail) 1_000_000 else 4_000_000)
        if (bitmap == null) {
            // Do not decode a corrupt cache entry on every visit.
            file.delete()
            return null
        }
        memory.put(url, bitmap)
        return bitmap
    }

    private fun decode(bytes: ByteArray, maxPixels: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > maxPixels) {
            sample *= 2
        }
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    private suspend fun download(url: String): ByteArray? = runCatching {
        var current = url
        repeat(MAX_REDIRECTS + 1) { attempt ->
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // A redirect is followed manually so every hop is revalidated.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "image/*")
            connection.setRequestProperty("User-Agent", LauncherUserAgent.value)
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    if (attempt >= MAX_REDIRECTS) return null
                    val location = connection.getHeaderField("Location") ?: return null
                    val next = resolveRedirectUrl(current, location) ?: return null
                    current = RedditFeedParser.safeImageUrl(next) ?: return null
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                if (connection.contentLengthLong > MAX_BYTES) return null
                return connection.inputStream.use { it.readAtMost(MAX_BYTES) }
            } finally {
                connection.disconnect()
            }
        }
        null
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "reddit image download failed", error)
        null
    }

    private fun save(dir: File, name: String, bytes: ByteArray) {
        runCatching {
            if (!writeFileAtomically(File(dir, name), bytes)) return
            val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".img") }
                ?: return
            var total = files.sumOf { it.length() }
            var remaining = files.size
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (remaining > KEEP_FILES || total > DISK_MAX_BYTES) {
                    if (file.delete()) {
                        remaining--
                        total -= file.length()
                    }
                }
            }
        }
    }

    private fun name(url: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".img"
}
