package app.aure.tv.data

import android.content.Context
import org.json.JSONObject

/**
 * One real Aerial clip from the bundled Apple catalogue. [videoUrl] stays the
 * 1080p H.264 rendition for compatibility. [variants] holds the other real
 * files Apple publishes; the quality policy only selects one when the display
 * and decoder report support for it.
 */
data class AerialClip(
    val id: String,
    val nameEn: String,
    val nameRu: String,
    val theme: SaverTheme,
    val videoUrl: String,
    val previewUrl: String,
    val order: Int,
    val variants: List<AerialVariant> = emptyList(),
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
 * A still may be an Apple URL or a bundled real frame for a legacy clip whose
 * public catalogue no longer ships a preview image. The bundled frame was
 * captured from that clip's own video; it is not invented artwork.
 */
internal fun isAerialPreviewUrl(url: String): Boolean =
    isAerialMediaUrl(url) || url.startsWith("asset:aerial_previews/")

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
}.map { clip ->
    // A refresh rebuilds the catalogue from Apple's live entries, which do not
    // carry the quality table or the bundled legacy frames. Keep both by clip
    // id so an automatic refresh never silently drops 4K/HDR or a real preview.
    val source = bundled.firstOrNull { it.id == clip.id }
    val previewUrl = clip.previewUrl.ifEmpty { source?.previewUrl.orEmpty() }
    val variants = if (clip.variants.isNotEmpty()) clip.variants else source?.variants.orEmpty()
    // A catalogue saved by an older build can still carry https:// Apple
    // media URLs. Some TV trust stores reject Apple's chain, so normalize the
    // saved clip and its renditions to the same scoped HTTP host as a fresh
    // catalogue; external URLs are left untouched.
    clip.copy(
        videoUrl = aerialStreamUrl(clip.videoUrl),
        previewUrl = if (previewUrl.startsWith("asset:")) previewUrl else aerialStreamUrl(previewUrl),
        variants = variants.map { variant -> variant.copy(url = aerialStreamUrl(variant.url)) },
    )
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
        if (previewUrl.isNotEmpty() && !isAerialPreviewUrl(previewUrl)) return null
        return AerialClip(
            id = id,
            nameEn = nameEn,
            nameRu = nameRu,
            theme = theme,
            videoUrl = videoUrl,
            previewUrl = previewUrl,
            order = json.optInt("order", Int.MAX_VALUE),
            variants = parseVariants(json.optJSONObject("variants")),
        )
    }

    private fun parseVariants(json: JSONObject?): List<AerialVariant> {
        if (json == null) return emptyList()
        return VARIANT_SPECS.mapNotNull { (label, spec) ->
            val url = aerialStreamUrl(json.optString(label))
            if (url.isEmpty() || !isNetworkUrl(url)) null else AerialVariant(
                label = label,
                url = url,
                width = spec.width,
                height = spec.height,
                codec = spec.codec,
                hdr = spec.hdr,
            )
        }
    }
}

private data class VariantSpec(
    val width: Int,
    val height: Int,
    val codec: AerialCodec,
    val hdr: Boolean,
)

// The keys are the public catalogue's labels, not a local invention. H.264 is
// the only codec kept as a universal fallback; all HEVC/HDR entries are gated
// by device capabilities before they are used.
private val VARIANT_SPECS = linkedMapOf(
    "1080p" to VariantSpec(1920, 1080, AerialCodec.HEVC, hdr = false),
    "1080p_hdr" to VariantSpec(1920, 1080, AerialCodec.HEVC, hdr = true),
    "h264" to VariantSpec(1920, 1080, AerialCodec.H264, hdr = false),
    "4k" to VariantSpec(3840, 2160, AerialCodec.HEVC, hdr = false),
    "4k_hdr" to VariantSpec(3840, 2160, AerialCodec.HEVC, hdr = true),
    "240p" to VariantSpec(426, 240, AerialCodec.H264, hdr = false),
)
