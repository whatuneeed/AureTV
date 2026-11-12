package com.whatuneed.ovisiontv.kotlin.data

import com.whatuneed.ovisiontv.kotlin.SettingsActivity

/**
 * A home grid entry. The artwork fields are **cache keys**, not decoded
 * bitmaps: the grid can hold fifty tiles, and a tile that owned its bitmap kept
 * the whole set alive after [AppArtCache] had already evicted it, so a memory
 * trim freed nothing (audit A-01). The key encodes package, version stamp, kind
 * and size, which is exactly what [AppArtCache.load] needs to re-read the art
 * from the APK after an eviction.
 */
data class TvApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val banner: String?,
    val icon: String?,
    // System and preinstalled apps cannot be removed by the user; the app menu
    // offers Uninstall only when this is false.
    val system: Boolean = false,
)

/** The launcher's own Settings tile: in kids mode it is a gated surface, not an app. */
fun TvApp.isSettingsTile(ownPackage: String): Boolean =
    packageName == ownPackage &&
        activityName == SettingsActivity::class.java.name
