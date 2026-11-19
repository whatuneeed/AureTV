package com.whatuneed.ovisiontv.kotlin.update

import com.whatuneed.ovisiontv.kotlin.data.resolveRedirectUrl
import java.net.URI
import java.time.Instant

/**
 * URL rules shared by metadata, redirects and APK downloads. The same parser
 * is used for the configured metadata URL, every redirect target and every
 * asset, so a redirect cannot quietly leave the allowed hosts.
 */
internal object UpdateUrlPolicy {
    const val MAX_URL_CHARS = 2048

    // A GitHub Release asset URL redirects to one of these hosts. They are
    // explicit, not a wildcard: a compromised metadata document still cannot
    // point the downloader at an arbitrary host.
    val GITHUB_ASSET_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    fun parse(value: String?): URI? {
        if (value.isNullOrBlank() || value.length > MAX_URL_CHARS) return null
        return runCatching { URI(value) }.getOrNull()
    }

    fun isHttps(uri: URI): Boolean {
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.host.isNullOrBlank()) return false
        if (uri.userInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        return uri.fragment == null
    }

    fun isAllowed(value: String?, hosts: Set<String>): Boolean {
        val uri = parse(value) ?: return false
        if (!isHttps(uri)) return false
        val host = uri.host.lowercase()
        return hosts.any { it.equals(host, ignoreCase = true) }
    }

    fun isAllowedMetadata(value: String?, extraHosts: Set<String> = emptySet()): Boolean {
        val uri = parse(value) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return isHttps(uri) && (extraHosts + GITHUB_ASSET_HOSTS).any { it == host }
    }

    fun allowedAssetHosts(metadataUrl: String?): Set<String> {
        val configured = parse(metadataUrl)?.host?.lowercase()
        return GITHUB_ASSET_HOSTS + listOfNotNull(configured)
    }

    fun resolveRedirect(current: String, location: String): String? {
        val resolved = resolveRedirectUrl(current, location) ?: return null
        return resolved.takeIf { isHttps(parse(it) ?: return@takeIf false) }
    }
}

internal sealed interface UpdateMetadataResult {
    data class Success(val releases: List<UpdateRelease>) : UpdateMetadataResult
    data class Failure(val error: UpdateError, val reason: String) : UpdateMetadataResult
}

/**
 * Strict parser. Unknown top-level fields are ignored, but every field that can
 * change what gets installed is checked; a bad asset rejects the document
 * instead of producing a partially trusted release.
 */
internal object UpdateMetadataParser {
    private const val MAX_BYTES = 256 * 1024
    private const val MAX_RELEASES = 20
    private const val MAX_NOTES = 20
    private const val MAX_NOTE_CHARS = 200
    private const val MAX_ASSET_BYTES = 100L * 1024L * 1024L
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val VERSION_NAME = Regex("^[0-9A-Za-z._+\\-]{1,64}$")

    fun parse(
        json: String,
        expectedChannel: UpdateChannel,
        allowedAssetHosts: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
    ): UpdateMetadataResult {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            return failure(UpdateError.METADATA, "metadata is too large")
        }
        val root = runCatching { org.json.JSONObject(json) }.getOrNull()
            ?: return failure(UpdateError.METADATA, "invalid json")
        if (root.optInt("schema", -1) != 1) {
            return failure(UpdateError.METADATA, "unsupported schema")
        }
        val channel = root.optString("channel", "")
        if (channel != expectedChannel.key) {
            return failure(UpdateError.METADATA, "channel mismatch")
        }
        val generatedAt = isoInstant(root.optString("generatedAt", ""))
            ?: return failure(UpdateError.METADATA, "invalid generatedAt")
        val expiresAt = isoInstant(root.optString("expiresAt", ""))
            ?: return failure(UpdateError.METADATA, "invalid expiresAt")
        if (generatedAt.toEpochMilli() > nowMs + DAY_MS) {
            return failure(UpdateError.METADATA, "generatedAt is in the future")
        }
        if (expiresAt.toEpochMilli() <= nowMs) {
            return failure(UpdateError.METADATA, "metadata expired")
        }
        val array = root.optJSONArray("releases")
            ?: return failure(UpdateError.METADATA, "releases missing")
        if (array.length() !in 1..MAX_RELEASES) {
            return failure(UpdateError.METADATA, "releases count")
        }

        val releases = ArrayList<UpdateRelease>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: return failure(UpdateError.METADATA, "release is not an object")
            releases += parseRelease(
                item = item,
                expectedChannel = expectedChannel,
                generatedAt = root.optString("generatedAt"),
                expiresAt = root.optString("expiresAt"),
                allowedAssetHosts = allowedAssetHosts,
                nowMs = nowMs,
            ) ?: return failure(UpdateError.METADATA, "invalid release")
        }
        if (releases.map { it.versionCode }.toSet().size != releases.size) {
            return failure(UpdateError.METADATA, "duplicate versionCode")
        }
        releases.sortByDescending { it.versionCode }
        return UpdateMetadataResult.Success(releases)
    }

    private fun parseRelease(
        item: org.json.JSONObject,
        expectedChannel: UpdateChannel,
        generatedAt: String,
        expiresAt: String,
        allowedAssetHosts: Set<String>,
        nowMs: Long,
    ): UpdateRelease? {
        val versionCode = item.optInt("versionCode", -1)
        if (versionCode <= 0) return null
        val versionName = item.optString("versionName", "")
        if (!VERSION_NAME.matches(versionName)) return null
        val channel = UpdateChannel.fromKey(item.optString("channel", ""))
        if (channel != expectedChannel || item.optString("channel") != expectedChannel.key) return null
        val releaseDate = isoInstant(item.optString("releaseDate", "")) ?: return null
        if (releaseDate.toEpochMilli() > nowMs + DAY_MS) return null
        val minimumAndroidApi = item.optInt("minimumAndroidApi", -1)
        if (minimumAndroidApi !in 1..100) return null
        val minimumSupported = item.optInt("minimumSupportedVersionCode", -1)
        if (minimumSupported < 0) return null
        val mandatory = item.optBoolean("mandatory", false)

        val notes = parseNotes(item.optJSONObject("notes")) ?: return null
        val assets = item.optJSONArray("assets") ?: return null
        if (assets.length() != 1) return null
        val assetJson = assets.optJSONObject(0) ?: return null
        val url = assetJson.optString("url", "")
        if (!UpdateUrlPolicy.isAllowed(url, allowedAssetHosts)) return null
        val digest = assetJson.optString("sha256", "")
        if (!SHA256.matches(digest)) return null
        val size = assetJson.optLong("sizeBytes", -1L)
        if (size !in 1..MAX_ASSET_BYTES) return null
        val abi = assetJson.optString("abi", "universal")
        if (abi != "universal" && abi !in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
            return null
        }

        return UpdateRelease(
            versionCode = versionCode,
            versionName = versionName,
            channel = channel,
            generatedAt = generatedAt,
            expiresAt = expiresAt,
            releaseDate = releaseDate.toString(),
            minAndroidApi = minimumAndroidApi,
            minSupportedVersionCode = minimumSupported,
            mandatory = mandatory,
            notes = notes,
            asset = UpdateAsset(
                url = url,
                sha256 = digest,
                sizeBytes = size,
                abi = abi,
            ),
        )
    }

    private fun parseNotes(value: org.json.JSONObject?): Map<String, List<String>>? {
        if (value == null) return null
        val result = LinkedHashMap<String, List<String>>()
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.length !in 2..8) return null
            val array = value.optJSONArray(key) ?: return null
            if (array.length() !in 1..MAX_NOTES) return null
            val lines = ArrayList<String>(array.length())
            for (index in 0 until array.length()) {
                val line = array.optString(index, "").trim()
                if (line.isEmpty() || line.length > MAX_NOTE_CHARS) return null
                lines += line
            }
            result[key.lowercase()] = lines
        }
        if (result.isEmpty() || result.size > 4) return null
        if (result["en"].isNullOrEmpty() && result["ru"].isNullOrEmpty()) return null
        return result
    }

    private fun isoInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private fun failure(error: UpdateError, reason: String): UpdateMetadataResult.Failure =
        UpdateMetadataResult.Failure(error, reason)

    private const val DAY_MS = 24L * 60L * 60L * 1000L
}
