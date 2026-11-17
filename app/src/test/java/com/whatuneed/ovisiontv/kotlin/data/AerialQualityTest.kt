package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AerialQualityTest {

    private val clip = AerialClip(
        id = "clip",
        nameEn = "Clip",
        nameRu = "Клип",
        theme = SaverTheme.CITIES,
        videoUrl = "http://sylvan.apple.com/Videos/fallback_AVC.mov",
        previewUrl = "http://sylvan.apple.com/Videos/fallback.png",
        order = 1,
        variants = listOf(
            variant("1080p", "http://sylvan.apple.com/1080p_HEVC.mov"),
            variant("1080p_hdr", "http://sylvan.apple.com/1080p_HDR_HEVC.mov", hdr = true),
            variant("h264", "http://sylvan.apple.com/1080p_AVC.mov", codec = AerialCodec.H264),
            variant("4k", "http://sylvan.apple.com/4k_HEVC.mov", width = 3840, height = 2160),
            variant(
                "4k_hdr",
                "http://sylvan.apple.com/4k_HDR_HEVC.mov",
                width = 3840,
                height = 2160,
                hdr = true,
            ),
        ),
    )

    private fun variant(
        label: String,
        url: String,
        width: Int = 1920,
        height: Int = 1080,
        codec: AerialCodec = AerialCodec.HEVC,
        hdr: Boolean = false,
    ) = AerialVariant(label, url, width, height, codec, hdr)

    @Test
    fun compatible_mode_never_asks_a_tv_for_hevc_or_hdr() {
        val caps = AerialCapabilities(uhd = true, hdr = true, hevc = true)
        val order = AerialQualityPolicy.order(clip, AerialQuality.COMPATIBLE, caps)

        assertEquals("1080p_AVC.mov", order.first().url.substringAfterLast('/'))
        assertTrue(order.all { it.codec == AerialCodec.H264 })
    }

    @Test
    fun best_mode_falls_back_to_h264_when_the_device_reports_nothing() {
        val order = AerialQualityPolicy.order(clip, AerialQuality.BEST, AerialCapabilities())

        assertEquals("1080p_AVC.mov", order.first().url.substringAfterLast('/'))
        assertTrue(order.all { it.codec == AerialCodec.H264 })
    }

    @Test
    fun best_mode_uses_hdr_only_when_the_panel_and_decoder_both_support_it() {
        val noHdr = AerialQualityPolicy.order(
            clip,
            AerialQuality.BEST,
            AerialCapabilities(uhd = true, hdr = false, hevc = true),
        )
        assertEquals("4k_HEVC.mov", noHdr.first().url.substringAfterLast('/'))

        val hdr = AerialQualityPolicy.order(
            clip,
            AerialQuality.BEST,
            AerialCapabilities(uhd = true, hdr = true, hevc = true),
        )
        assertEquals("4k_HDR_HEVC.mov", hdr.first().url.substringAfterLast('/'))
        assertTrue(hdr.first().hdr)
    }

    @Test
    fun a_1080p_hdr_panel_gets_hdr_without_claiming_4k() {
        val order = AerialQualityPolicy.order(
            clip,
            AerialQuality.BEST,
            AerialCapabilities(uhd = false, hdr = true, hevc = true),
        )

        assertEquals("1080p_HDR_HEVC.mov", order.first().url.substringAfterLast('/'))
        assertTrue(order.none { it.width == 3840 })
    }

    @Test
    fun four_k_is_not_tried_without_an_hevc_decoder() {
        val order = AerialQualityPolicy.order(
            clip,
            AerialQuality.BEST,
            AerialCapabilities(uhd = true, hdr = true, hevc = false),
        )

        assertTrue(order.none { it.width == 3840 || it.codec == AerialCodec.HEVC })
        assertEquals("1080p_AVC.mov", order.first().url.substringAfterLast('/'))
    }

    @Test
    fun the_chain_always_ends_with_the_compatibility_file() {
        val order = AerialQualityPolicy.order(
            clip,
            AerialQuality.BEST,
            AerialCapabilities(uhd = true, hdr = true, hevc = true),
        )

        assertEquals(clip.videoUrl, order.last().url)
    }

    @Test
    fun a_clip_without_a_quality_table_keeps_its_existing_file() {
        val plain = clip.copy(variants = emptyList())
        val order = AerialQualityPolicy.order(
            plain,
            AerialQuality.BEST,
            AerialCapabilities(uhd = true, hdr = true, hevc = true),
        )

        assertEquals(listOf(plain.videoUrl), order.map { it.url })
    }

    @Test
    fun unknown_or_missing_stored_quality_is_compatible() {
        assertEquals(AerialQuality.COMPATIBLE, AerialQuality.fromKey(null))
        assertEquals(AerialQuality.COMPATIBLE, AerialQuality.fromKey(""))
        assertEquals(AerialQuality.COMPATIBLE, AerialQuality.fromKey("8k"))
        assertEquals(AerialQuality.BEST, AerialQuality.fromKey("best"))
    }
}
