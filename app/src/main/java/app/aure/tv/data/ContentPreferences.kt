package app.aure.tv.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Shelf content is opt-out, like tvOS: the provider rows only describe what the
 * user already watches, and neither switch uploads anything.
 */
object ContentPreferences {
    const val KEY_RECOMMENDATIONS = "content_recommendations"
    const val KEY_CONTINUE_WATCHING = "content_continue_watching"

    // Manifest.permission.READ_TV_LISTINGS is a system constant and is not in
    // the public SDK stubs, so the manifest name is spelled out here.
    const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

    internal fun prefs(context: Context) =
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)

    fun recommendations(context: Context): Boolean =
        SafePreferences.boolean(prefs(context), KEY_RECOMMENDATIONS, true)

    fun setRecommendations(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RECOMMENDATIONS, enabled).apply()
    }

    fun continueWatching(context: Context): Boolean =
        SafePreferences.boolean(prefs(context), KEY_CONTINUE_WATCHING, true)

    fun setContinueWatching(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONTINUE_WATCHING, enabled).apply()
    }

    /** True when the launcher may read the provider rows at all. */
    fun canReadListings(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, READ_TV_LISTINGS) ==
            PackageManager.PERMISSION_GRANTED
}
