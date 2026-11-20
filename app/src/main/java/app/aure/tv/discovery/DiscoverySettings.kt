package app.aure.tv.discovery

import android.content.Context
import android.content.SharedPreferences
import app.aure.tv.data.SafePreferences

/**
 * Profile-scoped switches. p1 writes the same keys ContentPreferences used
 * before profiles, so the wizard and an upgraded install keep their choice;
 * every other profile starts with recommendations on and its own history.
 */
class DiscoverySettings(context: Context, prefsName: String) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun recommendations(): Boolean =
        SafePreferences.boolean(prefs, DiscoveryKeys.RECOMMENDATIONS, true)

    fun setRecommendations(enabled: Boolean) {
        prefs.edit().putBoolean(DiscoveryKeys.RECOMMENDATIONS, enabled).apply()
    }

    fun continueWatching(): Boolean =
        SafePreferences.boolean(prefs, DiscoveryKeys.CONTINUE, true)

    fun setContinueWatching(enabled: Boolean) {
        prefs.edit().putBoolean(DiscoveryKeys.CONTINUE, enabled).apply()
    }

    fun online(): Boolean =
        SafePreferences.boolean(prefs, DiscoveryKeys.ONLINE, false)

    fun setOnline(enabled: Boolean) {
        prefs.edit().putBoolean(DiscoveryKeys.ONLINE, enabled).apply()
    }

    /**
     * tvOS starts a muted preview when the eye rests on a card. The setting
     * only matters while [online] is on; Kids Mode and Reduce Motion still
     * disable it regardless of this value.
     */
    fun previewAutoplay(): Boolean =
        SafePreferences.boolean(prefs, DiscoveryKeys.PREVIEW_AUTOPLAY, true)

    fun setPreviewAutoplay(enabled: Boolean) {
        prefs.edit().putBoolean(DiscoveryKeys.PREVIEW_AUTOPLAY, enabled).apply()
    }

    fun personalization(): Boolean =
        SafePreferences.boolean(prefs, DiscoveryKeys.PERSONALIZATION, true)

    fun setPersonalization(enabled: Boolean) {
        prefs.edit().putBoolean(DiscoveryKeys.PERSONALIZATION, enabled).apply()
    }
}
