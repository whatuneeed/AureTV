package com.whatuneed.ovisiontv.kotlin.online

/** Flux is an optional installed player; the launcher never bundles it. */
internal const val FLUX_PACKAGE = "app.flux.tv"

/**
 * Builds only the documented movie deep link. An empty, non-numeric or
 * overlong id is rejected instead of being interpolated into a URI.
 */
internal fun fluxMovieUri(tmdbId: String?): String? {
    val id = tmdbId?.takeIf { it.length in 1..12 && it.all(Char::isDigit) } ?: return null
    return "flux://movie/$id"
}
