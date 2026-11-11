package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SaverPlaylistTest {

    private fun clip(id: String, theme: SaverTheme, order: Int) = AerialClip(
        id = id,
        nameEn = "Name $id",
        nameRu = "Имя $id",
        theme = theme,
        videoUrl = "https://cdn.example/$id.mov",
        previewUrl = "",
        order = order,
    )

    private val clips = listOf(
        clip("city", SaverTheme.CITIES, 1),
        clip("coast", SaverTheme.LANDSCAPES, 2),
        clip("orbit", SaverTheme.EARTH, 3),
    )

    @Test
    fun only_the_selected_themes_are_played() {
        val plan = SaverPlaylist.build(clips, setOf(SaverTheme.CITIES, SaverTheme.EARTH))
        assertEquals(listOf("city", "orbit"), plan.map { it.id })
    }

    @Test
    fun a_theme_without_clips_yields_no_loop() {
        assertTrue(SaverPlaylist.build(clips, setOf(SaverTheme.UNDERWATER)).isEmpty())
    }

    @Test
    fun an_empty_catalogue_yields_no_loop() {
        assertTrue(SaverPlaylist.build(emptyList(), SaverTheme.entries.toSet()).isEmpty())
    }

    @Test
    fun an_empty_selection_keeps_every_theme() {
        val plan = SaverPlaylist.build(clips, emptySet())
        assertEquals(listOf("city", "coast", "orbit"), plan.map { it.id })
    }

    @Test
    fun the_settings_preview_follows_the_first_enabled_theme() {
        assertEquals("coast", SaverPlaylist.previewClip(clips, SaverTheme.entries.toSet())?.id)
        assertEquals(
            "city",
            SaverPlaylist.previewClip(clips, setOf(SaverTheme.CITIES, SaverTheme.EARTH))?.id,
        )
        assertEquals(
            "orbit",
            SaverPlaylist.previewClip(clips, setOf(SaverTheme.EARTH))?.id,
        )
        assertEquals(null, SaverPlaylist.previewClip(emptyList(), SaverTheme.entries.toSet()))
    }

    @Test
    fun hidden_clips_are_left_out_of_the_loop() {
        val plan = SaverPlaylist.build(clips, emptySet(), hidden = setOf("coast"))
        assertEquals(listOf("city", "orbit"), plan.map { it.id })
    }

    @Test
    fun the_settings_preview_skips_hidden_clips() {
        assertEquals(
            null,
            SaverPlaylist.previewClip(clips, setOf(SaverTheme.EARTH), hidden = setOf("orbit")),
        )
        assertEquals(
            "orbit",
            SaverPlaylist.previewClip(clips, setOf(SaverTheme.EARTH), hidden = setOf("coast"))?.id,
        )
        assertEquals(
            "coast",
            SaverPlaylist.previewClip(clips, emptySet(), hidden = setOf("city"))?.id,
        )
    }

    @Test
    fun hiding_every_clip_yields_no_loop_instead_of_fallback_content() {
        assertTrue(
            SaverPlaylist.build(clips, emptySet(), hidden = clips.map { it.id }.toSet()).isEmpty(),
        )
    }

    @Test
    fun a_shuffled_pass_plays_every_clip_exactly_once() {
        val many = (1..30).map { clip("c$it", SaverTheme.LANDSCAPES, it) }
        val plan = SaverPlaylist.build(many, setOf(SaverTheme.LANDSCAPES), random = Random(7))
        assertEquals(many.map { it.id }.toSet(), plan.map { it.id }.toSet())
        assertEquals(many.size, plan.size)
    }

    @Test
    fun a_shuffled_pass_is_not_the_catalogue_order() {
        val many = (1..30).map { clip("c$it", SaverTheme.LANDSCAPES, it) }
        val plan = SaverPlaylist.build(many, setOf(SaverTheme.LANDSCAPES), random = Random(7))
        assertNotEquals(many.map { it.id }, plan.map { it.id })
    }

    @Test
    fun a_loop_never_repeats_the_clip_it_ended_with() {
        val many = (1..4).map { clip("c$it", SaverTheme.CITIES, it) }
        val plan = SaverPlaylist.build(many, setOf(SaverTheme.CITIES), random = Random(11))
        assertNotEquals(plan.first().id, plan.last().id)
    }
}
