package com.whatuneed.ovisiontv.kotlin.online

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource

/**
 * A hero preview is only the already-published official preview of an online
 * card. It never starts for a local app, a provider row or a cleartext URL.
 * Focus, Kids Mode, Reduce Motion and the user setting are decided by the
 * caller; this function only answers "is this a playable online preview?".
 */
internal fun onlinePreviewUri(item: DiscoveryItem?, enabled: Boolean): String? {
    if (!enabled) return null
    if (item?.source != DiscoverySource.ONLINE) return null
    val uri = item.intentUri ?: return null
    return uri.takeIf { it.startsWith("https://") }
}
