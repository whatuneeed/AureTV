package app.aure.tv.ui

import app.aure.tv.data.AerialClip
import app.aure.tv.data.SaverTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupBackdropTest {

    private fun clip(
        id: String,
        theme: SaverTheme,
        preview: String,
        order: Int,
    ) = AerialClip(
        id = id,
        nameEn = id,
        nameRu = id,
        theme = theme,
        videoUrl = "https://sylvan.apple.com/$id.m3u8",
        previewUrl = preview,
        order = order,
    )

    @Test
    fun keepsBundledLandscapeFramesInCatalogueOrder() {
        val clips = listOf(
            clip("city", SaverTheme.CITIES, "asset:aerial_previews/city.jpg", 1),
            clip("lake", SaverTheme.LANDSCAPES, "asset:aerial_previews/lake.jpg", 3),
            clip("mountains", SaverTheme.LANDSCAPES, "asset:aerial_previews/mountains.jpg", 2),
            clip("earth", SaverTheme.EARTH, "asset:aerial_previews/earth.jpg", 0),
        )

        assertEquals(
            listOf("mountains", "lake"),
            setupWelcomeClips(clips).map { it.id },
        )
    }

    @Test
    fun ignoresNetworkStillsAndCapsTheWelcomeLoopAtTwoFrames() {
        val clips = listOf(
            clip("network", SaverTheme.LANDSCAPES, "https://sylvan.apple.com/network.jpg", 0),
            clip("first", SaverTheme.LANDSCAPES, "asset:aerial_previews/first.jpg", 1),
            clip("second", SaverTheme.LANDSCAPES, "asset:aerial_previews/second.jpg", 2),
            clip("third", SaverTheme.LANDSCAPES, "asset:aerial_previews/third.jpg", 3),
        )

        assertEquals(
            listOf("first", "second"),
            setupWelcomeClips(clips).map { it.id },
        )
    }

    @Test
    fun fallsBackToBundledFramesWhenNoLandscapeSurvives() {
        val clips = listOf(
            clip("earth", SaverTheme.EARTH, "asset:aerial_previews/earth.jpg", 2),
            clip("city", SaverTheme.CITIES, "asset:aerial_previews/city.jpg", 1),
            clip("network", SaverTheme.LANDSCAPES, "https://sylvan.apple.com/network.jpg", 0),
        )

        assertEquals(
            listOf("city", "earth"),
            setupWelcomeClips(clips).map { it.id },
        )
    }
}
