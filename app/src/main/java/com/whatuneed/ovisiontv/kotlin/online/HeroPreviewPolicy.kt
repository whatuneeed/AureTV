package com.whatuneed.ovisiontv.kotlin.online

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource

/**
 * A hero preview is only an already-published official preview attached to a
 * provider/online card. It never starts for a local app tile or a cleartext URL.
 * Focus, Kids Mode, Reduce Motion and the user setting are decided by the
 * caller; this function only answers "is this a playable preview?".
 */
internal fun onlinePreviewUri(item: DiscoveryItem?, enabled: Boolean): String? {
    if (!enabled) return null
    if (item?.source != DiscoverySource.ONLINE && item?.source != DiscoverySource.TV_PROVIDER) {
        return null
    }
    val uri = item.trailerUri ?: return null
    return uri.takeIf { it.startsWith("https://") }
}
