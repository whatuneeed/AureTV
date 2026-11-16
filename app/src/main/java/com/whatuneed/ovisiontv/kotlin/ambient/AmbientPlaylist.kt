package com.whatuneed.ovisiontv.kotlin.ambient

import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.data.SaverPlaylist
import kotlin.random.Random

/**
 * The ambient layer's view of the aerial catalogue. Keeping this indirection
 * means Home, the DreamService and Settings all build the same loop from the
 * same [AmbientSettings], instead of each calling SaverPlaylist with a
 * slightly different set of preferences.
 */
object AmbientPlaylist {
    fun aerial(
        clips: List<AerialClip>,
        settings: AmbientSettings,
        random: Random? = null,
    ): List<AerialClip> = SaverPlaylist.build(
        clips,
        settings.themes,
        settings.hidden,
        random,
    )

    fun previewAerial(
        clips: List<AerialClip>,
        settings: AmbientSettings,
    ): AerialClip? = SaverPlaylist.previewClip(
        clips,
        settings.themes,
        settings.hidden,
    )

    /**
     * A clock or minimal surface never shows an aerial, so it has no clips.
     * Aerial with an empty catalogue also stays empty rather than inventing a
     * scene: the host then leaves Home alone instead of opening a dark overlay.
     */
    fun scenes(
        clips: List<AerialClip>,
        settings: AmbientSettings,
        random: Random? = null,
    ): List<AerialClip> = when (settings.mode) {
        AmbientMode.AERIAL -> aerial(clips, settings, random)
        AmbientMode.CLOCK, AmbientMode.MINIMAL, AmbientMode.PHOTOS, AmbientMode.REDDIT -> emptyList()
    }

    fun hasContent(
        scenes: List<AerialClip>,
        settings: AmbientSettings,
    ): Boolean = when (settings.mode) {
        AmbientMode.AERIAL -> scenes.isNotEmpty()
        AmbientMode.CLOCK, AmbientMode.MINIMAL, AmbientMode.PHOTOS, AmbientMode.REDDIT -> true
    }
}
