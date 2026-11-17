package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Display
import android.view.WindowManager

/** Which rendition chain the saver should try for an Aerial clip. */
enum class AerialQuality(val key: String) {
    /** 1080p H.264: the one rendition every minSdk 28 decoder can play. */
    COMPATIBLE("compatible"),

    /** Highest rendition the display and decoder report, with a safe fallback. */
    BEST("best");

    companion object {
        /**
         * Unknown values from an older or newer backup fall back to the safest
         * rendition instead of failing the restore.
         */
        fun fromKey(key: String?): AerialQuality =
            entries.firstOrNull { it.key == key } ?: COMPATIBLE
    }
}

enum class AerialCodec {
    H264,
    HEVC,
}

/** One real Apple file for a clip. The label matches the public catalogue table. */
data class AerialVariant(
    val label: String,
    val url: String,
    val width: Int,
    val height: Int,
    val codec: AerialCodec,
    val hdr: Boolean,
)

/**
 * What the current display and decoder can actually use. Kept as a value so
 * the selection policy is pure and testable without a TV.
 */
data class AerialCapabilities(
    val uhd: Boolean = false,
    val hdr: Boolean = false,
    val hevc: Boolean = false,
)

/**
 * Reads the panel and decoder once per saver surface. A TV may downscale a
 * 4K signal in the app's display metrics, so the physical mode is preferred;
 * the metrics are only a fallback.
 */
object AerialDeviceCapabilities {
    fun from(context: Context): AerialCapabilities {
        val display = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            }
        }.getOrNull()
        val metrics = context.resources.displayMetrics
        val mode = runCatching { display?.mode }.getOrNull()
        val width = mode?.physicalWidth ?: metrics.widthPixels
        val height = mode?.physicalHeight ?: metrics.heightPixels
        val hdr = runCatching {
            display?.hdrCapabilities?.supportedHdrTypes?.any { type ->
                type == Display.HdrCapabilities.HDR_TYPE_HDR10 ||
                    type == Display.HdrCapabilities.HDR_TYPE_HLG
            } == true
        }.getOrDefault(false)
        val hevc = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { type ->
                    type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)
                }
            }
        }.getOrDefault(false)
        return AerialCapabilities(
            uhd = width >= 3840 && height >= 2160,
            hdr = hdr,
            hevc = hevc,
        )
    }
}

/**
 * Chooses real catalogue variants, best first. There is no invented URL and no
 * infinite retry: [order] is a finite chain, and the caller walks it once.
 */
object AerialQualityPolicy {
    private const val LABEL_FHD_HEVC = "1080p"
    private const val LABEL_FHD_HDR = "1080p_hdr"
    private const val LABEL_FHD_H264 = "h264"
    private const val LABEL_UHD_HEVC = "4k"
    private const val LABEL_UHD_HDR = "4k_hdr"

    /**
     * The compatible mode always uses 1080p H.264. Best mode only adds a
     * rendition whose display and decoder are both reported as capable, so a
     * TV that lies about 4K still falls back to a file it can decode.
     */
    fun order(
        clip: AerialClip,
        quality: AerialQuality,
        capabilities: AerialCapabilities,
    ): List<AerialVariant> {
        val byLabel = clip.variants.associateBy { it.label }
        val fallback = AerialVariant(
            label = LABEL_FHD_H264,
            url = clip.videoUrl,
            width = 1920,
            height = 1080,
            codec = AerialCodec.H264,
            hdr = false,
        )
        val h264 = byLabel[LABEL_FHD_H264] ?: fallback
        if (quality == AerialQuality.COMPATIBLE) {
            return listOf(h264, fallback)
                .distinctBy { it.url }
                .filter { it.url.isNotBlank() }
        }

        val preferred = buildList {
            if (capabilities.uhd && capabilities.hevc && capabilities.hdr) {
                byLabel[LABEL_UHD_HDR]?.let(::add)
            }
            if (capabilities.uhd && capabilities.hevc) {
                byLabel[LABEL_UHD_HEVC]?.let(::add)
            }
            if (capabilities.hevc && capabilities.hdr) {
                byLabel[LABEL_FHD_HDR]?.let(::add)
            }
            if (capabilities.hevc) {
                byLabel[LABEL_FHD_HEVC]?.let(::add)
            }
            add(h264)
            add(fallback)
        }
        return preferred.distinctBy { it.url }.filter { it.url.isNotBlank() }
    }
}

