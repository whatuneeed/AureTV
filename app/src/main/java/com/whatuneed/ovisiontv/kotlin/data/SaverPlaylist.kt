package com.whatuneed.ovisiontv.kotlin.data

/**
 * Builds the clip loop shown by the saver. Only real clips are ever shown; a
 * selection that matches no clip yields an empty loop and the host leaves the
 * home screen alone instead of inventing content.
 */
object SaverPlaylist {
    /**
     * One pass of the loop. [random] shuffles the clips for a session, like
     * tvOS' shuffle/recency engine; null keeps the catalogue order for tests
     * and for a predictable first pass.
     */
    fun build(
        clips: List<AerialClip>,
        themes: Set<SaverTheme>,
        hidden: Set<String> = emptySet(),
        random: kotlin.random.Random? = null,
    ): List<AerialClip> {
        val selected = themes.ifEmpty { SaverTheme.entries.toSet() }
        val real = clips.filter { it.theme in selected && it.id !in hidden }
        return if (random == null) real else real.shuffled(random)
    }

    /**
     * The clip whose still stands in for the saver in Settings: the first
     * catalogue clip of the first enabled theme. The preview follows the theme
     * toggles without starting a second video decoder inside Settings.
     */
    fun previewClip(
        clips: List<AerialClip>,
        themes: Set<SaverTheme>,
        hidden: Set<String> = emptySet(),
    ): AerialClip? {
        val selected = themes.ifEmpty { SaverTheme.entries.toSet() }
        return SaverTheme.entries
            .filter { it in selected }
            .firstNotNullOfOrNull { theme ->
                clips.firstOrNull { it.theme == theme && it.id !in hidden }
            }
    }
}
