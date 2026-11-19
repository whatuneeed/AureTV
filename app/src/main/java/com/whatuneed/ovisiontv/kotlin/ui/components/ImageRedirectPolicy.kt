package com.whatuneed.ovisiontv.kotlin.ui.components

import java.net.URI

/**
 * An image redirect may not leave the host the image started on. A CDN hop
 * inside the same host is allowed; a different host is treated as a failed
 * image, because the loader has no allowlist that would justify sending the
 * request there.
 *
 * Cleartext stays limited to Apple's scoped Aerial exception.
 */
internal fun imageRedirectIsTrusted(fromRaw: String, toRaw: String): Boolean {
    val from = runCatching { URI(fromRaw) }.getOrNull() ?: return false
    val to = runCatching { URI(toRaw) }.getOrNull() ?: return false
    val scheme = to.scheme?.lowercase() ?: return false
    if (scheme != "https" && scheme != "http") return false
    if (to.userInfo != null) return false
    if (to.port != -1 && to.port != 80 && to.port != 443) return false
    val fromHost = from.host ?: return false
    val toHost = to.host ?: return false
    if (!fromHost.equals(toHost, ignoreCase = true)) return false
    if (scheme == "http" && !toHost.equals("sylvan.apple.com", ignoreCase = true)) return false
    return true
}
