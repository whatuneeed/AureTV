package com.whatuneed.ovisiontv.kotlin.online

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryKind
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource

/**
 * Maps a public chart entry to the same immutable card the shelf already
 * understands. The card keeps the official preview URL in
 * [DiscoveryItem.trailerUri] for one purpose: an exact JustWatch/TMDB match can
 * lend it to a provider row, where OK can finally open the film in its own app.
 * The ONLINE rail itself has no owning app: without a matching provider row it
 * stays information-only. When an installed app publishes the same title, Home
 * can use this metadata for the trailer → OK → open-in-app flow. It never opens
 * a browser or a foreign package.
 */
internal fun OnlineMovie.toDiscoveryItem(
    ownPackage: String,
    sourceLabel: String,
    infoLabel: String,
): DiscoveryItem = DiscoveryItem(
    id = id,
    source = DiscoverySource.ONLINE,
    kind = DiscoveryKind.VIDEO,
    packageName = ownPackage,
    packageLabel = sourceLabel,
    title = title,
    originalTitle = originalTitle,
    tmdbId = tmdbId,
    year = year,
    subtitle = buildString {
        year?.let { append(it).append(" · ") }
        append(infoLabel)
    },
    synopsis = synopsis,
    landscapeUri = landscapeUrl,
    posterUri = posterUrl,
    trailerUri = previewUrl,
    reason = DiscoveryReason.TRENDING,
)
