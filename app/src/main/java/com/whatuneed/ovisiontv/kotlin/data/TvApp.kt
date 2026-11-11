package com.whatuneed.ovisiontv.kotlin.data

import androidx.compose.ui.graphics.ImageBitmap
import com.whatuneed.ovisiontv.kotlin.SettingsActivity

data class TvApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    // System and preinstalled apps cannot be removed by the user; the app menu
    // offers Uninstall only when this is false.
    val system: Boolean = false,
)

/** The launcher's own Settings tile: in kids mode it is a gated surface, not an app. */
fun TvApp.isSettingsTile(ownPackage: String): Boolean =
    packageName == ownPackage &&
        activityName == SettingsActivity::class.java.name
