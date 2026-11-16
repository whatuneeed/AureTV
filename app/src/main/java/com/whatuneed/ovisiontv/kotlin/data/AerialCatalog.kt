package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import org.json.JSONObject

/**
 * One real Aerial clip from the bundled Apple catalogue. Only 1080p H.264
 * entries are used: every TV decoder handles them, while the 4K/HEVC renditions
 * are not guaranteed by minSdk 28 hardware.
 */
data class AerialClip(
    val id: String,
    val nameEn: String,
    val nameRu: String,
    val theme: SaverTheme,
    val videoUrl: String,
    val previewUrl: String,
    val order: Int,
) {
    fun name(language: String): String =
        if (language.lowercase().startsWith("ru")) nameRu else nameEn
}

/**
 * Apple's public CDN answers on both schemes. Some TV trust stores do not
 * accept the Apple certificate chain, so the video and still paths use HTTP
 * for this one public host; every other request stays HTTPS. The exception is
 * scoped in network_security_config.xml and covered by tests, not a global
 * cleartext switch.
 */
private const val APPLE_CDN = "sylvan.apple.com"

/** True for HTTPS or for the one explicitly scoped Apple CDN over HTTP. */
internal fun isAerialMediaUrl(url: String): Boolean =
    url.startsWith("https://") || url.startsWith("http://$APPLE_CDN/")

/**
 * Normalize both historical and current Apple URLs to HTTP. A saved catalogue
 * from an older build may carry either scheme; external HTTPS URLs are left
 * alone because they are not covered by the Apple cleartext exception.
 */
internal fun aerialStreamUrl(url: String): String {
    val secure = "https://$APPLE_CDN/"
    val cleartext = "http://$APPLE_CDN/"
    return if (url.startsWith(secure)) {
        cleartext + url.removePrefix(secure)
    } else {
        url
    }
}
/**
 * A saved catalogue from an older build may be the 2019 flat schema with no
 * stills, or a smaller stale refresh. The bundled tvOS 27 catalogue is the
 * known-good floor: use the saved one only when it is at least as complete and
 * can supply a still.
 */
internal fun preferredCatalogue(
    saved: List<AerialClip>,
    bundled: List<AerialClip>,
): List<AerialClip> = when {
    saved.isEmpty() -> bundled
    saved.none { it.previewUrl.isNotEmpty() } -> bundled
    saved.size < bundled.size -> bundled
    else -> saved
}

private fun isNetworkUrl(url: String): Boolean = isAerialMediaUrl(url)

object AerialCatalog {
    const val ASSET_NAME = "aerial_catalog.json"

    fun load(context: Context): List<AerialClip> {
        val saved = AerialStore.load(context)
        val bundled = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }
        }.getOrDefault(emptyList())
        return preferredCatalogue(saved, bundled)
    }

    fun parse(json: String): List<AerialClip> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val clips = root.optJSONArray("clips") ?: return emptyList()
        return buildList {
            for (index in 0 until clips.length()) {
                clips.optJSONObject(index)?.let(::parseClip)?.let(::add)
            }
        }.sortedWith(compareBy({ it.order }, { it.id }))
    }

    private fun parseClip(json: JSONObject): AerialClip? {
        val id = json.optString("id")
        val nameEn = json.optString("nameEn")
        val nameRu = json.optString("nameRu")
        val themeKey = json.optString("theme")
        val videoUrl = aerialStreamUrl(json.optString("video"))
        val previewUrl = aerialStreamUrl(json.optString("preview"))
        val theme = SaverTheme.entries.firstOrNull { it.key == themeKey }
        if (id.isEmpty() || nameEn.isEmpty() || nameRu.isEmpty() || theme == null) return null
        if (!isNetworkUrl(videoUrl)) return null
        if (previewUrl.isNotEmpty() && !isNetworkUrl(previewUrl)) return null
        return AerialClip(
            id = id,
            nameEn = nameEn,
            nameRu = nameRu,
            theme = theme,
            videoUrl = videoUrl,
            previewUrl = previewUrl,
            order = json.optInt("order", Int.MAX_VALUE),
        )
    }
}
