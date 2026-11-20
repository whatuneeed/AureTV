package com.whatuneed.ovisiontv.kotlin.data

import com.whatuneed.ovisiontv.kotlin.BuildConfig

/**
 * Every outbound request identifies the same launcher and the same build.
 * A hard-coded version goes stale after one release; `BuildConfig` cannot.
 */
object LauncherUserAgent {
    val value: String = "AURETV/${BuildConfig.VERSION_NAME} (Android TV launcher)"
}
