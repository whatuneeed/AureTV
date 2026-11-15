package com.whatuneed.ovisiontv.kotlin.discovery

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Turns a card into the exact intent it promised. A provider deep link is only
 * opened when it resolves back to the app that published it: a row must never
 * be able to start an arbitrary package. An app card uses the component the
 * launcher already resolved, with a leanback launch intent as the fallback.
 */
object DiscoveryIntents {

    fun contentIntent(context: Context, item: DiscoveryItem): Intent? {
        val intent = when {
            !item.intentUri.isNullOrBlank() -> runCatching {
                Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME)
            }.getOrNull()

            !item.activityName.isNullOrBlank() -> Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(item.packageName, item.activityName)
            }

            else -> context.packageManager.getLeanbackLaunchIntentForPackage(item.packageName)
                ?: context.packageManager.getLaunchIntentForPackage(item.packageName)
        } ?: return null

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, 0)
        }.getOrNull() ?: return null
        if (resolved.activityInfo?.packageName != item.packageName) return null
        return intent
    }
}
