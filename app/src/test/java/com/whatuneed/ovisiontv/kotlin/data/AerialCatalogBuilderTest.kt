package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AerialCatalogBuilderTest {

    private fun fixture(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("aerial/$name")!!.readBytes()

    @Test
    fun the_public_resources_build_the_same_four_themes() {
        val json = AerialCatalogBuilder.build(
            fixture("entries.json").toString(Charsets.UTF_8),
            fixture("strings-en.bplist"),
            fixture("strings-ru.bplist"),
        )
        assertNotNull(json)
        val clips = AerialCatalog.parse(json!!)

        assertEquals(100, clips.size)
        assertEquals(57, clips.count { it.theme == SaverTheme.LANDSCAPES })
        assertEquals(11, clips.count { it.theme == SaverTheme.CITIES })
        assertEquals(14, clips.count { it.theme == SaverTheme.UNDERWATER })
        // The public resources still say Space; tvOS 27 calls the same set Earth.
        assertEquals(18, clips.count { it.theme == SaverTheme.EARTH })
        assertEquals(0, clips.count { it.theme == SaverTheme.LANDSCAPES && it.nameEn.isBlank() })

        clips.forEach { clip ->
            assertTrue(clip.videoUrl, clip.videoUrl.startsWith("https://sylvan.apple.com/"))
            assertTrue(clip.nameEn.isNotBlank())
            assertTrue(clip.nameRu.isNotBlank())
        }
        assertEquals(clips.size, clips.map { it.id }.toSet().size)
    }

    @Test
    fun entries_that_cannot_play_are_left_out() {
        val entries = """
            {"categories":[
              {"id":"city","localizedNameKey":"AerialCategoryCities"},
              {"id":"space","localizedNameKey":"AerialCategorySpace"},
              {"id":"other","localizedNameKey":"AerialCategoryUnknown"}
            ],"assets":[
              {"id":"kept","showInTopLevel":true,"categories":["city"],
               "localizedNameKey":"DB_D002_C003_NAME",
               "url-1080-H264":"https://cdn.example/kept_AVC.mov",
               "previewImage":"https://cdn.example/kept.png"},
              {"id":"hidden","showInTopLevel":false,"categories":["city"],
               "localizedNameKey":"DB_D002_C003_NAME",
               "url-1080-H264":"https://cdn.example/hidden_AVC.mov"},
              {"id":"ftp","showInTopLevel":true,"categories":["city"],
               "localizedNameKey":"DB_D002_C003_NAME",
               "url-1080-H264":"ftp://cdn.example/ftp_AVC.mov"},
              {"id":"future","showInTopLevel":true,"categories":["space"],
               "localizedNameKey":"DB_D002_C003_NAME",
               "url-1080-H264":"https://cdn.example/future_AVC.mov"},
              {"id":"unknown","showInTopLevel":true,"categories":["other"],
               "localizedNameKey":"DB_D002_C003_NAME",
               "url-1080-H264":"https://cdn.example/unknown_AVC.mov"}
            ]}
        """.trimIndent()

        val clips = AerialCatalog.parse(
            AerialCatalogBuilder.build(
                entries,
                fixture("strings-en.bplist"),
                fixture("strings-ru.bplist"),
            )!!,
        )

        assertEquals(setOf("kept", "future"), clips.map { it.id }.toSet())
        assertEquals(2, clips.size)
        assertEquals("Dubai Skyline", clips.first { it.id == "kept" }.nameEn)
        assertEquals(SaverTheme.CITIES, clips.first { it.id == "kept" }.theme)
        assertEquals(SaverTheme.EARTH, clips.first { it.id == "future" }.theme)
    }

    @Test
    fun a_name_missing_from_the_table_falls_back_to_the_label() {
        val entries = """
            {"categories":[{"id":"city","localizedNameKey":"AerialCategoryCities"}],
             "assets":[{"id":"labelled","showInTopLevel":true,"categories":["city"],
                        "localizedNameKey":"NO_SUCH_KEY","accessibilityLabel":"Label Name",
                        "url-1080-H264":"https://cdn.example/labelled_AVC.mov"}]}
        """.trimIndent()

        val clip = AerialCatalog.parse(
            AerialCatalogBuilder.build(entries, fixture("strings-en.bplist"), ByteArray(0))!!,
        ).single()

        assertEquals("Label Name", clip.nameEn)
        assertEquals("Label Name", clip.nameRu)
        assertEquals("", clip.previewUrl)
    }

    @Test
    fun a_broken_entries_file_yields_no_catalogue() {
        assertEquals(null, AerialCatalogBuilder.build("{", ByteArray(0), ByteArray(0)))
        assertEquals(
            null,
            AerialCatalogBuilder.build(
                """{"categories":[],"assets":[]}""",
                fixture("strings-en.bplist"),
                fixture("strings-ru.bplist"),
            ),
        )
    }

    @Test
    fun the_2019_flat_catalogue_is_not_accepted_from_the_network() {
        val flat = """
            {"version":1,"initialAssetCount":4,"assets":[
              {"id":"D388F00A-5A32-4431-A95C-38BF7FF7268D",
               "accessibilityLabel":"Greenland",
               "url-1080-H264":"https://sylvan.apple.com/Videos/comp_GL_G004_C010_v03_6Mbps.mov"}]}
        """.trimIndent()

        assertTrue(AerialCatalogBuilder.supports(fixture("entries.json").toString(Charsets.UTF_8)))
        assertEquals(false, AerialCatalogBuilder.supports(flat))
        assertEquals(false, AerialCatalogBuilder.supports("{"))
    }
}
