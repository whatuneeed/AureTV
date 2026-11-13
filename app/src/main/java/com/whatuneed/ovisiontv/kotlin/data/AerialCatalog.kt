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
 * Parser for `assets/aerial_catalog.json`, generated from the tvOS 27 idle
 * screen resources. A malformed or truncated file yields no clips at all, and
 * the screen saver then keeps its drawn scenes instead of going black.
 */
/**
 * Apple's CDN still serves the same files over cleartext, and some TV networks
 * block 443 while 80 works (the reason the Java launcher streamed over HTTP).
 * HTTP stays the primary media scheme so the known-good path is preserved;
 * [aerialFallbackUrl] offers TLS when port 80 is the blocked one.
 */
internal fun aerialStreamUrl(url: String): String {
    val secure = "https://sylvan.apple.com/"
    return if (url.startsWith(secure)) {
        "http://sylvan.apple.com/" + url.removePrefix(secure)
    } else {
        url
    }
}

/** The TLS variant of an Apple HTTP URL, for the playback fallback. */
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

internal fun aerialFallbackUrl(url: String): String? {
    val insecure = "http://sylvan.apple.com/"
    return if (url.startsWith(insecure)) {
        "https://sylvan.apple.com/" + url.removePrefix(insecure)
    } else {
        null
    }
}

private fun isNetworkUrl(url: String): Boolean =
    url.startsWith("https://") || url.startsWith("http://")

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
        val videoUrl = json.optString("video")
        val previewUrl = json.optString("preview")
        val theme = SaverTheme.entries.firstOrNull { it.key == themeKey }
        if (id.isEmpty() || nameEn.isEmpty() || nameRu.isEmpty() || theme == null) return null
        if (!isNetworkUrl(videoUrl)) return null
        if (previewUrl.isNotEmpty() && !isNetworkUrl(previewUrl)) return null
        return AerialClip(
            id = id,
            nameEn = nameEn,
            nameRu = nameRu,
            theme = theme,
            videoUrl = aerialStreamUrl(videoUrl),
            previewUrl = aerialStreamUrl(previewUrl),
            order = json.optInt("order", Int.MAX_VALUE),
        )
    }
}
