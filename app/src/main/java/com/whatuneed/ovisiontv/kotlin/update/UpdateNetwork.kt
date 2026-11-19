package com.whatuneed.ovisiontv.kotlin.update

import android.util.Log
import com.whatuneed.ovisiontv.kotlin.BuildConfig
import com.whatuneed.ovisiontv.kotlin.data.LauncherUserAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal enum class MetadataFetchStatus {
    OK,
    NOT_MODIFIED,
    RATE_LIMITED,
    SERVER_ERROR,
    NETWORK_ERROR,
    HTTP_ERROR,
    TOO_LARGE,
}

internal data class MetadataFetchResult(
    val status: MetadataFetchStatus,
    val body: ByteArray? = null,
    val etag: String? = null,
    val httpCode: Int = 0,
)

/**
 * Minimal HTTPS metadata client. It follows at most three redirects, rechecks
 * every target against the allowlist, and never falls back to HTTP.
 */
internal class UpdateMetadataClient(
    private val userAgent: String = LauncherUserAgent.value,
) {
    suspend fun fetch(
        url: String,
        etag: String?,
        allowedHosts: Set<String>,
    ): MetadataFetchResult = withContext(Dispatchers.IO) {
        fetch(url, etag, allowedHosts, redirects = 0)
    }

    private suspend fun fetch(
        url: String,
        etag: String?,
        allowedHosts: Set<String>,
        redirects: Int,
    ): MetadataFetchResult {
        currentCoroutineContext().ensureActive()
        if (!UpdateUrlPolicy.isAllowed(url, allowedHosts)) {
            return MetadataFetchResult(MetadataFetchStatus.HTTP_ERROR)
        }
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }
            .getOrNull()
            ?: return MetadataFetchResult(MetadataFetchStatus.NETWORK_ERROR)
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("User-Agent", userAgent)
        if (!etag.isNullOrBlank()) connection.setRequestProperty("If-None-Match", etag)
        return try {
            val code = connection.responseCode
            if (code in 300..399) {
                if (redirects >= MAX_REDIRECTS) {
                    return MetadataFetchResult(MetadataFetchStatus.HTTP_ERROR, httpCode = code)
                }
                val location = connection.getHeaderField("Location")
                val next = location?.let { UpdateUrlPolicy.resolveRedirect(url, it) }
                if (next == null || !UpdateUrlPolicy.isAllowed(next, allowedHosts)) {
                    return MetadataFetchResult(MetadataFetchStatus.HTTP_ERROR, httpCode = code)
                }
                return fetch(next, etag, allowedHosts, redirects + 1)
            }
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return MetadataFetchResult(
                    status = MetadataFetchStatus.NOT_MODIFIED,
                    etag = connection.getHeaderField("ETag") ?: etag,
                    httpCode = code,
                )
            }
            if (code == 429) {
                return MetadataFetchResult(MetadataFetchStatus.RATE_LIMITED, httpCode = code)
            }
            if (code in 500..599) {
                return MetadataFetchResult(MetadataFetchStatus.SERVER_ERROR, httpCode = code)
            }
            if (code != HttpURLConnection.HTTP_OK) {
                return MetadataFetchResult(MetadataFetchStatus.HTTP_ERROR, httpCode = code)
            }
            val declared = connection.contentLengthLong
            if (declared > MAX_BYTES) {
                return MetadataFetchResult(MetadataFetchStatus.TOO_LARGE, httpCode = code)
            }
            val out = java.io.ByteArrayOutputStream(INITIAL_BUFFER)
            connection.inputStream.use { input ->
                val buffer = ByteArray(CHUNK)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) {
                        return MetadataFetchResult(MetadataFetchStatus.TOO_LARGE, httpCode = code)
                    }
                    out.write(buffer, 0, read)
                }
            }
            MetadataFetchResult(
                status = MetadataFetchStatus.OK,
                body = out.toByteArray(),
                etag = connection.getHeaderField("ETag"),
                httpCode = code,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "metadata request failed", error)
            MetadataFetchResult(MetadataFetchStatus.NETWORK_ERROR)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "UpdateMetadataClient"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 3
        const val MAX_BYTES = 256 * 1024
        const val CHUNK = 16 * 1024
        const val INITIAL_BUFFER = 32 * 1024
    }
}

internal sealed interface UpdateCheckResult {
    data object NotConfigured : UpdateCheckResult
    data class Found(val release: UpdateRelease) : UpdateCheckResult
    data class Failed(val error: UpdateError, val detail: String? = null) : UpdateCheckResult
}

/**
 * Reads metadata, validates it and keeps only a bounded app-private cache. It
 * does not decide whether an update is compatible or what the installed version
 * is; UpdateManager owns that policy.
 */
internal class UpdateRepository(
    context: android.content.Context,
    private val metadataUrl: String = BuildConfig.UPDATE_METADATA_URL,
    private val client: UpdateMetadataClient = UpdateMetadataClient(),
) {
    private val cache = UpdateMetadataCache(context)
    private val store = UpdateStateStore(context)
    private val assetHosts = UpdateUrlPolicy.allowedAssetHosts(metadataUrl)

    fun isConfigured(): Boolean = metadataUrl.isNotBlank()

    fun cached(channel: UpdateChannel): UpdateRelease? {
        val json = cache.load(channel) ?: return null
        val parsed = UpdateMetadataParser.parse(json, channel, assetHosts)
        return (parsed as? UpdateMetadataResult.Success)?.releases?.firstOrNull()
    }

    suspend fun check(channel: UpdateChannel, force: Boolean = false): UpdateCheckResult {
        if (!isConfigured()) return UpdateCheckResult.NotConfigured
        val uri = UpdateUrlPolicy.parse(metadataUrl)
        val host = uri?.host?.lowercase()
        if (uri == null || !UpdateUrlPolicy.isAllowedMetadata(metadataUrl, setOfNotNull(host))) {
            return UpdateCheckResult.Failed(UpdateError.METADATA, "metadata URL is not HTTPS")
        }
        val allowedMetadataHosts = setOfNotNull(host) + UpdateUrlPolicy.GITHUB_ASSET_HOSTS
        val oldEtag = if (force) null else store.etag()
        var response = client.fetch(metadataUrl, oldEtag, allowedMetadataHosts)
        if (response.status == MetadataFetchStatus.NOT_MODIFIED && cache.load(channel) == null) {
            // A 304 without a local cache is unusable; ask once for the body.
            response = client.fetch(metadataUrl, null, allowedMetadataHosts)
        }
        val body = when (response.status) {
            MetadataFetchStatus.OK -> response.body
            MetadataFetchStatus.NOT_MODIFIED -> cache.load(channel)?.toByteArray(Charsets.UTF_8)
            MetadataFetchStatus.RATE_LIMITED ->
                return UpdateCheckResult.Failed(UpdateError.RATE_LIMITED)
            MetadataFetchStatus.SERVER_ERROR ->
                return UpdateCheckResult.Failed(UpdateError.SERVER, "HTTP ${response.httpCode}")
            MetadataFetchStatus.NETWORK_ERROR ->
                return UpdateCheckResult.Failed(UpdateError.NETWORK)
            MetadataFetchStatus.TOO_LARGE ->
                return UpdateCheckResult.Failed(UpdateError.METADATA, "metadata is too large")
            MetadataFetchStatus.HTTP_ERROR ->
                return UpdateCheckResult.Failed(UpdateError.SERVER, "HTTP ${response.httpCode}")
        } ?: return UpdateCheckResult.Failed(UpdateError.METADATA, "empty metadata")

        val json = runCatching { body.toString(Charsets.UTF_8) }.getOrNull()
            ?: return UpdateCheckResult.Failed(UpdateError.METADATA, "not UTF-8")
        val parsed = UpdateMetadataParser.parse(json, channel, assetHosts)
        if (parsed is UpdateMetadataResult.Failure) {
            return UpdateCheckResult.Failed(parsed.error, parsed.reason)
        }
        val releases = (parsed as UpdateMetadataResult.Success).releases
        if (response.status == MetadataFetchStatus.OK) {
            if (!cache.save(channel, json)) {
                return UpdateCheckResult.Failed(UpdateError.STORAGE, "could not cache metadata")
            }
            store.setEtag(response.etag)
        }
        return UpdateCheckResult.Found(releases.first())
    }

    fun clearCache(channel: UpdateChannel? = null) {
        cache.clear(channel)
    }

    private companion object {
        const val TAG = "UpdateRepository"
    }
}
