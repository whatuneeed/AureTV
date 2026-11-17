package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AerialCatalogTest {

    private val clips = AerialCatalog.parse(
        File("src/main/assets/${AerialCatalog.ASSET_NAME}").readText(),
    )

    @Test
    fun the_bundled_catalogue_holds_the_four_real_aerial_categories() {
        assertEquals(117, clips.size)
        assertEquals(15, clips.count { it.theme == SaverTheme.CITIES })
        assertEquals(60, clips.count { it.theme == SaverTheme.LANDSCAPES })
        assertEquals(21, clips.count { it.theme == SaverTheme.UNDERWATER })
        assertEquals(21, clips.count { it.theme == SaverTheme.EARTH })
    }

    @Test
    fun every_clip_streams_1080p_h264_from_the_apple_cdn_with_both_names() {
        clips.forEach { clip ->
            assertTrue(clip.nameEn.isNotBlank())
            assertTrue(clip.nameRu.isNotBlank())
            assertTrue(clip.videoUrl, clip.videoUrl.startsWith("http://sylvan.apple.com/"))
            assertTrue(clip.videoUrl, clip.videoUrl.endsWith(".mov"))
            // Apple's Hong Kong file is H.264 but named ..._tag0.mov, not ..._AVC.mov.
            assertTrue(
                clip.videoUrl,
                clip.videoUrl.contains("_AVC") || clip.videoUrl.contains("_tag0"),
            )
            assertTrue(
                clip.previewUrl,
                clip.previewUrl.startsWith("http://sylvan.apple.com/") ||
                    clip.previewUrl.startsWith("asset:aerial_previews/"),
            )
        }
    }

    @Test
    fun audited_quality_variants_are_parsed_as_real_apple_files() {
        val withVariants = clips.filter { it.variants.isNotEmpty() }
        assertEquals(92, withVariants.size)
        withVariants.forEach { clip ->
            assertTrue(clip.nameEn, clip.variants.any { it.label == "1080p" })
            assertTrue(clip.nameEn, clip.variants.any { it.label == "h264" })
            assertTrue(clip.nameEn, clip.variants.any { it.label == "4k_hdr" })
            clip.variants.forEach { variant ->
                assertTrue(variant.url, variant.url.startsWith("http://sylvan.apple.com/"))
                assertTrue(variant.url, variant.url.endsWith(".mov"))
            }
        }
    }

    @Test
    fun legacy_clips_use_real_frames_captured_from_their_own_videos() {
        val legacy = clips.filter { it.previewUrl.startsWith("asset:aerial_previews/") }
        assertEquals(17, legacy.size)
        legacy.forEach { clip ->
            val file = File("src/main/assets/${clip.previewUrl.removePrefix("asset:")}")
            assertTrue(clip.nameEn, file.isFile)
            assertTrue(clip.nameEn, file.length() > 0)
        }
    }

    @Test
    fun a_refreshed_catalogue_keeps_variants_and_bundled_frames() {
        val variant = AerialVariant(
            label = "4k_hdr",
            url = "http://sylvan.apple.com/4k_hdr.mov",
            width = 3840,
            height = 2160,
            codec = AerialCodec.HEVC,
            hdr = true,
        )
        val bundled = listOf(
            clip("a", "asset:aerial_previews/a.jpg").copy(variants = listOf(variant)),
        )
        val refreshed = listOf(clip("a", ""))

        val preferred = preferredCatalogue(refreshed, bundled)

        assertEquals(listOf(variant), preferred.single().variants)
        assertEquals("asset:aerial_previews/a.jpg", preferred.single().previewUrl)
    }

    @Test
    fun a_stale_smaller_or_previewless_saved_catalogue_falls_back_to_bundled() {
        val bundled = listOf(clip("a", "https://sylvan.apple.com/a.png"), clip("b", "https://sylvan.apple.com/b.png"))
        assertEquals(bundled, preferredCatalogue(emptyList(), bundled))
        assertEquals(bundled, preferredCatalogue(listOf(clip("a", "")), bundled))
        assertEquals(bundled, preferredCatalogue(listOf(clip("a", "https://x/a.png")), bundled))
        val saved = bundled + clip("c", "https://sylvan.apple.com/c.png")
        assertEquals(saved, preferredCatalogue(saved, bundled))
    }

    private fun clip(id: String, preview: String) = AerialClip(
        id = id,
        nameEn = id,
        nameRu = id,
        theme = SaverTheme.CITIES,
        videoUrl = "https://sylvan.apple.com/$id.mov",
        previewUrl = preview,
        order = 0,
    )

    @Test
    fun apple_urls_use_the_cleartext_cdn_and_other_hosts_stay_https() {
        assertEquals(
            "http://sylvan.apple.com/Videos/a.mov",
            aerialStreamUrl("https://sylvan.apple.com/Videos/a.mov"),
        )
        assertEquals(
            "http://sylvan.apple.com/Videos/a.mov",
            aerialStreamUrl("http://sylvan.apple.com/Videos/a.mov"),
        )
        assertEquals(
            "https://cdn.example/b.mov",
            aerialStreamUrl("https://cdn.example/b.mov"),
        )
        assertEquals(
            "http://cdn.example/b.mov",
            aerialStreamUrl("http://cdn.example/b.mov"),
        )
    }

    @Test
    fun a_saved_catalogue_uses_http_for_apple_and_keeps_external_https() {
        val json = """
            {"clips":[
              {"id":"apple","nameEn":"Apple","nameRu":"Эппл","theme":"cities",
               "video":"http://sylvan.apple.com/Videos/a.mov",
               "preview":"http://sylvan.apple.com/Videos/a.png","order":1},
              {"id":"other","nameEn":"Other","nameRu":"Другой","theme":"cities",
               "video":"https://cdn.example/b.mov","preview":"","order":2}
            ]}
        """.trimIndent()
        val parsed = AerialCatalog.parse(json).associateBy { it.id }
        assertEquals("http://sylvan.apple.com/Videos/a.mov", parsed.getValue("apple").videoUrl)
        assertEquals("http://sylvan.apple.com/Videos/a.png", parsed.getValue("apple").previewUrl)
        assertEquals("https://cdn.example/b.mov", parsed.getValue("other").videoUrl)
    }

    @Test
    fun ids_are_unique_and_the_order_is_stable() {
        assertEquals(clips.size, clips.map { it.id }.toSet().size)
        assertEquals(clips, clips.sortedWith(compareBy({ it.order }, { it.id })))
    }

    @Test
    fun names_follow_the_interface_language() {
        val clip = clips.first()
        assertEquals(clip.nameRu, clip.name("ru"))
        assertEquals(clip.nameRu, clip.name("ru-RU"))
        assertEquals(clip.nameEn, clip.name("en"))
        assertEquals(clip.nameEn, clip.name("de"))
    }

    @Test
    fun a_broken_catalogue_yields_no_clips() {
        assertEquals(emptyList<AerialClip>(), AerialCatalog.parse("{"))
        assertEquals(emptyList<AerialClip>(), AerialCatalog.parse("""{"clips":[]}"""))
    }

    @Test
    fun incomplete_or_non_network_entries_are_skipped() {
        val json = """
            {"clips":[
              {"id":"ok","nameEn":"Ok","nameRu":"Ок","theme":"cities",
               "video":"https://cdn.example/ok.mov","preview":"https://cdn.example/ok.png","order":2},
              {"id":"http","nameEn":"Http","nameRu":"Хттп","theme":"cities",
               "video":"http://cdn.example/plain.mov","preview":"","order":3},
              {"id":"ftp","nameEn":"Ftp","nameRu":"Фтп","theme":"cities",
               "video":"ftp://cdn.example/bad.mov","preview":"","order":4},
              {"id":"theme","nameEn":"Theme","nameRu":"Тема","theme":"space?",
               "video":"https://cdn.example/bad.mov","preview":"","order":5},
              {"id":"novideo","nameEn":"No video","nameRu":"Без видео","theme":"earth",
               "preview":"","order":6}
            ]}
        """.trimIndent()
        val parsed = AerialCatalog.parse(json)
        assertEquals(listOf("ok"), parsed.map { it.id })
        assertEquals(SaverTheme.CITIES, parsed.first().theme)
    }
}
