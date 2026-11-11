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
        assertEquals(100, clips.size)
        assertEquals(11, clips.count { it.theme == SaverTheme.CITIES })
        assertEquals(57, clips.count { it.theme == SaverTheme.LANDSCAPES })
        assertEquals(14, clips.count { it.theme == SaverTheme.UNDERWATER })
        assertEquals(18, clips.count { it.theme == SaverTheme.EARTH })
    }

    @Test
    fun every_clip_streams_1080p_h264_from_the_cleartext_cdn_with_both_names() {
        clips.forEach { clip ->
            assertTrue(clip.nameEn.isNotBlank())
            assertTrue(clip.nameRu.isNotBlank())
            assertTrue(clip.videoUrl, clip.videoUrl.startsWith("http://sylvan.apple.com/"))
            assertTrue(clip.videoUrl, clip.videoUrl.endsWith(".mov"))
            assertTrue(clip.videoUrl, clip.videoUrl.contains("_AVC"))
            assertTrue(clip.previewUrl, clip.previewUrl.startsWith("http://sylvan.apple.com/"))
        }
    }

    @Test
    fun the_cdn_url_is_rewritten_to_cleartext_and_other_hosts_keep_their_scheme() {
        val json = """
            {"clips":[
              {"id":"apple","nameEn":"Apple","nameRu":"Эппл","theme":"cities",
               "video":"https://sylvan.apple.com/Videos/a.mov",
               "preview":"https://sylvan.apple.com/Videos/a.png","order":1},
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
        assertEquals(listOf("ok", "http"), parsed.map { it.id })
        assertEquals(SaverTheme.CITIES, parsed.first().theme)
    }
}
