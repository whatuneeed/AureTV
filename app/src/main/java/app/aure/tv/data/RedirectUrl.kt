package app.aure.tv.data

import java.net.URL

private const val MAX_REDIRECT_URL_CHARS = 4_096

/**
 * Resolves one `Location` header against the URL that produced it. The caller
 * still decides whether the target is allowed; this function only rejects a
 * missing, oversized or malformed header and never lets a blank header turn
 * into an infinite same-URL loop.
 */
internal fun resolveRedirectUrl(current: String, location: String?): String? {
    val target = location?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (target.length > MAX_REDIRECT_URL_CHARS) return null
    return runCatching { URL(URL(current), target).toString() }.getOrNull()
}
