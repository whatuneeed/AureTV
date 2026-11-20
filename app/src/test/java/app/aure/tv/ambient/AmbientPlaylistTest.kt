package app.aure.tv.ambient

import app.aure.tv.data.AerialClip
import app.aure.tv.data.SaverDelay
import app.aure.tv.data.SaverRefresh
import app.aure.tv.data.SaverTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientPlaylistTest {

    private val clips = listOf(
        clip("city", SaverTheme.CITIES),
        clip("coast", SaverTheme.LANDSCAPES),
    )

    private fun settings(
        mode: AmbientMode,
        themes: Set<SaverTheme> = SaverTheme.entries.toSet(),
        hidden: Set<String> = emptySet(),
    ) = AmbientSettings(
        mode = mode,
        delay = SaverDelay.FIVE,
        duringMusic = true,
        themes = themes,
        hidden = hidden,
        refresh = SaverRefresh.WEEKLY,
    )

    @Test
    fun aerial_still_uses_the_existing_playlist_rules() {
        val plan = AmbientPlaylist.scenes(
            clips,
            settings(AmbientMode.AERIAL, themes = setOf(SaverTheme.CITIES)),
        )
        assertEquals(listOf("city"), plan.map { it.id })
    }

    @Test
    fun clock_and_minimal_have_no_aerial_scenes() {
        assertTrue(
            AmbientPlaylist.scenes(clips, settings(AmbientMode.CLOCK)).isEmpty(),
        )
        assertTrue(
            AmbientPlaylist.scenes(clips, settings(AmbientMode.MINIMAL)).isEmpty(),
        )
        assertTrue(
            AmbientPlaylist.scenes(clips, settings(AmbientMode.PHOTOS)).isEmpty(),
        )
        assertTrue(
            AmbientPlaylist.scenes(clips, settings(AmbientMode.REDDIT)).isEmpty(),
        )
    }

    @Test
    fun clock_and_minimal_are_content_even_without_a_catalogue() {
        assertTrue(AmbientPlaylist.hasContent(emptyList(), settings(AmbientMode.CLOCK)))
        assertTrue(AmbientPlaylist.hasContent(emptyList(), settings(AmbientMode.MINIMAL)))
        assertTrue(AmbientPlaylist.hasContent(emptyList(), settings(AmbientMode.PHOTOS)))
        assertTrue(AmbientPlaylist.hasContent(emptyList(), settings(AmbientMode.REDDIT)))
        assertFalse(AmbientPlaylist.hasContent(emptyList(), settings(AmbientMode.AERIAL)))
    }

    @Test
    fun a_hidden_aerial_is_not_part_of_the_plan() {
        val plan = AmbientPlaylist.scenes(
            clips,
            settings(AmbientMode.AERIAL, hidden = setOf("coast")),
        )
        assertEquals(listOf("city"), plan.map { it.id })
    }

    private fun clip(id: String, theme: SaverTheme) = AerialClip(
        id = id,
        nameEn = id,
        nameRu = id,
        theme = theme,
        videoUrl = "https://cdn.example/$id.mov",
        previewUrl = "",
        order = 0,
    )
}
