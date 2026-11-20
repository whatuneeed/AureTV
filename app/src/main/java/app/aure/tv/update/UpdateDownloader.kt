package app.aure.tv.update

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import app.aure.tv.data.LauncherUserAgent
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal sealed interface UpdateDownloadResult {
    data class Success(val file: File, val sha256: String, val sizeBytes: Long) :
        UpdateDownloadResult

    data class Failure(val error: UpdateError, val detail: String? = null) :
        UpdateDownloadResult
}

/**
 * Streams the APK into app-private cache. It never loads the whole file into
 * memory, accepts a Range only when the server supplies a stable validator,
 * and checks size + SHA-256 before exposing a READY file.
 */
internal class UpdateDownloader(
    context: Context,
    metadataUrl: String = app.aure.tv.BuildConfig.UPDATE_METADATA_URL,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    private val dir = File(context.applicationContext.cacheDir, DIR)
    private val allowedHosts = UpdateUrlPolicy.allowedAssetHosts(metadataUrl)

    fun apkFile(release: UpdateRelease): File = File(dir, "update-${release.versionCode}.apk")

    private fun partFile(release: UpdateRelease): File =
        File(dir, "update-${release.versionCode}.apk.part")

    private fun validatorFile(release: UpdateRelease): File =
        File(dir, "update-${release.versionCode}.validator")

    suspend fun download(
        release: UpdateRelease,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = apkFile(release)
        val part = partFile(release)
        val validator = validatorFile(release)
        if (target.isFile && target.length() == release.asset.sizeBytes && sha256(target) == release.asset.sha256) {
            return@withContext UpdateDownloadResult.Success(target, release.asset.sha256, target.length())
        }
        if (target.isFile) target.delete()
        if (part.isFile && part.length() == release.asset.sizeBytes) {
            if (sha256(part) == release.asset.sha256) {
                move(part, target)
                validator.delete()
                return@withContext UpdateDownloadResult.Success(
                    target,
                    release.asset.sha256,
                    target.length(),
                )
            }
            part.delete()
            validator.delete()
        }

        val free = runCatching {
            StatFs(dir.absolutePath).availableBytes
        }.getOrNull()
        val required = UpdateCompatibility.requiredCacheBytes(release.asset.sizeBytes)
        val existing = part.length().coerceAtLeast(0L)
        if (free != null && free + existing < required) {
            return@withContext UpdateDownloadResult.Failure(UpdateError.STORAGE)
        }
        if (existing > 0L && readValidator(validator) == null) {
            // Appending bytes without knowing the server still serves the same
            // object is a corruption path; start from zero instead.
            part.delete()
            validator.delete()
        }
        val initial = part.length().coerceAtLeast(0L)
        val saved = readValidator(validator)
        val resume = initial > 0L && saved != null
        try {
            val result = downloadFrom(
                url = release.asset.url,
                release = release,
                target = target,
                part = part,
                validator = validator,
                resume = resume,
                initial = initial,
                redirects = 0,
                onProgress = onProgress,
            )
            result
        } catch (error: CancellationException) {
            part.delete()
            validator.delete()
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "download failed", error)
            UpdateDownloadResult.Failure(UpdateError.NETWORK)
        }
    }

    private suspend fun downloadFrom(
        url: String,
        release: UpdateRelease,
        target: File,
        part: File,
        validator: File,
        resume: Boolean,
        initial: Long,
        redirects: Int,
        onProgress: (Long, Long) -> Unit,
    ): UpdateDownloadResult {
        currentCoroutineContext().ensureActive()
        if (!UpdateUrlPolicy.isAllowed(url, allowedHosts)) {
            return UpdateDownloadResult.Failure(UpdateError.NETWORK, "untrusted asset host")
        }
        val connection = runCatching { openConnection(URL(url)) }.getOrNull()
            ?: return UpdateDownloadResult.Failure(UpdateError.NETWORK)
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("User-Agent", LauncherUserAgent.value)
        if (resume) {
            connection.setRequestProperty("Range", "bytes=$initial-")
            savedValidatorHeader(validator)?.let { connection.setRequestProperty("If-Range", it) }
        }
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                if (redirects >= MAX_REDIRECTS) {
                    return UpdateDownloadResult.Failure(UpdateError.NETWORK, "too many redirects")
                }
                val location = connection.getHeaderField("Location")
                val next = location?.let { UpdateUrlPolicy.resolveRedirect(url, it) }
                if (next == null || !UpdateUrlPolicy.isAllowed(next, allowedHosts)) {
                    return UpdateDownloadResult.Failure(UpdateError.NETWORK, "untrusted redirect")
                }
                return downloadFrom(
                    url = next,
                    release = release,
                    target = target,
                    part = part,
                    validator = validator,
                    resume = resume,
                    initial = initial,
                    redirects = redirects + 1,
                    onProgress = onProgress,
                )
            }
            if (code == 429) return UpdateDownloadResult.Failure(UpdateError.RATE_LIMITED)
            if (code in 500..599) return UpdateDownloadResult.Failure(UpdateError.SERVER)
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                if (!resume || !validPartial(connection, initial)) {
                    part.delete()
                    validator.delete()
                    return UpdateDownloadResult.Failure(UpdateError.INTEGRITY, "bad partial response")
                }
            } else if (code != HttpURLConnection.HTTP_OK) {
                part.delete()
                validator.delete()
                return UpdateDownloadResult.Failure(
                    if (code == 416) UpdateError.INTEGRITY else UpdateError.NETWORK,
                    "HTTP $code",
                )
            } else if (resume) {
                // The server ignored Range or the validator changed; restart.
                part.delete()
                validator.delete()
            }
            saveValidator(connection, validator)
            val append = code == HttpURLConnection.HTTP_PARTIAL
            val start = if (append) initial else 0L
            val declared = connection.contentLengthLong
            if (declared > 0 && start + declared > release.asset.sizeBytes) {
                part.delete()
                validator.delete()
                return UpdateDownloadResult.Failure(UpdateError.INTEGRITY, "asset is too large")
            }
            var downloaded = start
            onProgress(downloaded, release.asset.sizeBytes)
            FileOutputStream(part, append).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(CHUNK)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        if (downloaded > release.asset.sizeBytes) {
                            part.delete()
                            validator.delete()
                            return UpdateDownloadResult.Failure(UpdateError.INTEGRITY, "asset is too large")
                        }
                        output.write(buffer, 0, read)
                        onProgress(downloaded, release.asset.sizeBytes)
                    }
                }
                output.fd.sync()
            }
            if (downloaded != release.asset.sizeBytes) {
                return UpdateDownloadResult.Failure(UpdateError.INTEGRITY, "size mismatch")
            }
            val digest = sha256(part)
            if (digest != release.asset.sha256) {
                part.delete()
                validator.delete()
                return UpdateDownloadResult.Failure(UpdateError.INTEGRITY, "sha256 mismatch")
            }
            move(part, target)
            validator.delete()
            return UpdateDownloadResult.Success(target, digest, target.length())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "download request failed", error)
            return UpdateDownloadResult.Failure(UpdateError.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    private fun validPartial(connection: HttpURLConnection, start: Long): Boolean {
        val range = connection.getHeaderField("Content-Range") ?: return false
        return range.startsWith("bytes $start-")
    }

    private fun saveValidator(connection: HttpURLConnection, file: File) {
        val etag = connection.getHeaderField("ETag")
        val lastModified = connection.getHeaderField("Last-Modified")
        if (etag.isNullOrBlank() && lastModified.isNullOrBlank()) {
            file.delete()
            return
        }
        runCatching {
            file.writeText("${etag.orEmpty()}\n${lastModified.orEmpty()}", Charsets.UTF_8)
        }
    }

    private fun readValidator(file: File): String? {
        if (!file.isFile) return null
        return runCatching {
            val lines = file.readLines(Charsets.UTF_8)
            lines.firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    private fun savedValidatorHeader(file: File): String? {
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() ?: return null
        return lines.firstOrNull { it.isNotBlank() }
    }

    fun cleanup() {
        val cutoff = System.currentTimeMillis() - KEEP_PART_MS
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part") && file.lastModified() < cutoff) file.delete()
            if (file.name.endsWith(".validator") && file.lastModified() < cutoff) file.delete()
        }
    }

    private fun move(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(CHUNK)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private companion object {
        const val TAG = "UpdateDownloader"
        const val DIR = "updates"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 3
        const val CHUNK = 64 * 1024
        const val KEEP_PART_MS = 24L * 60L * 60L * 1000L
    }
}
