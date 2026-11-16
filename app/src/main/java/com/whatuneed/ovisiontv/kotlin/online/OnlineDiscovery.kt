package com.whatuneed.ovisiontv.kotlin.online

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryKind
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource

/**
 * Maps a public chart entry to the same immutable card the shelf already
 * understands. The card keeps the preview URL in [DiscoveryItem.intentUri];
 * Home recognises [DiscoverySource.ONLINE] and plays it in the launcher.
 */
internal fun OnlineMovie.toDiscoveryItem(
    ownPackage: String,
    sourceLabel: String,
    previewLabel: String,
): DiscoveryItem = DiscoveryItem(
    id = id,
    source = DiscoverySource.ONLINE,
    kind = DiscoveryKind.VIDEO,
    packageName = ownPackage,
    packageLabel = sourceLabel,
    title = title,
    subtitle = year?.let { "$it · $previewLabel" } ?: previewLabel,
    posterUri = posterUrl,
    intentUri = previewUrl,
    reason = DiscoveryReason.TRENDING,
)
