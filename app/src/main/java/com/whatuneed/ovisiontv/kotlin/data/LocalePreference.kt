package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-launcher interface language. Android's own per-app language store only
 * exists from API 33 and minSdk is 28, so the choice lives in the launcher
 * preferences and is applied through a wrapped configuration context.
 */
object LocalePreference {
    const val SYSTEM = "system"
    const val RUSSIAN = "ru"
    const val ENGLISH = "en"

    private const val KEY = "ui_language"

    fun get(context: Context): String {
        val value = prefs(context).getString(KEY, SYSTEM)
        return if (value == RUSSIAN || value == ENGLISH) value else SYSTEM
    }

    fun set(context: Context, language: String) {
        prefs(context).edit().putString(KEY, language).apply()
    }

    fun wrap(context: Context): Context {
        val language = get(context)
        if (language == SYSTEM) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
}
