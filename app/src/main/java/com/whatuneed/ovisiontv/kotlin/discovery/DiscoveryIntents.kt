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

        // A provider row must only reach the app that published it. Clear any
        // URI grant before adding NEW_TASK: a row is untrusted provider data.
        intent.selector = null
        if (intent.`package` != null && intent.`package` != item.packageName) return null
        if (intent.component != null && intent.component?.packageName != item.packageName) {
            return null
        }
        intent.`package` = item.packageName
        val uriGrants = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        intent.flags = (intent.flags and uriGrants.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, 0)
        }.getOrNull() ?: return null
        val target = resolved.activityInfo ?: return null
        if (target.packageName != item.packageName || !target.exported) return null
        return intent
    }
}
