package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context

/** Start delay of tvOS Screen Saver: the system value is user-configurable (11_SCREENSAVER §2.2). */
enum class SaverDelay(val minutes: Int) {
    TWO(2),
    FIVE(5),
    TEN(10),
    TWENTY(20),
    NEVER(-1),
    ;

    companion object {
        fun fromMinutes(value: Int): SaverDelay =
            entries.firstOrNull { it.minutes == value } ?: FIVE
    }
}

/** tvOS "Download Frequency" values (10_SETTINGS §4.4). */
enum class SaverRefresh {
    DAILY,
    WEEKLY,
    MONTHLY,
    NEVER,
}

/**
 * Scene categories of the Apple aerial catalogue. The keys are stored, so they
 * must not be renamed; labels live in strings.xml.
 */
enum class SaverTheme(val key: String) {
    LANDSCAPES("landscapes"),
    CITIES("cities"),
    UNDERWATER("underwater"),
    EARTH("earth"),
}

/** Pure rules behind the Screen Saver page, kept outside Android for unit tests. */
object SaverSettings {
    val DEFAULT_THEMES: Set<SaverTheme> = SaverTheme.entries.toSet()

    /**
     * "space" was the old fourth category: it had no Apple clips and only drawn
     * scenes. Stored selections keep their count by mapping it to Underwater,
     * while Earth was already a theme of its own.
     */
    private val LEGACY_KEYS = mapOf("space" to SaverTheme.UNDERWATER.key)

    /**
     * tvOS refuses to hide every aerial category (`AerialAllCategoriesHiddenAlert`),
     * so unchecking the last theme keeps the set as it was. Turning a theme off
     * must also leave at least one playable clip: every clip of the remaining
     * themes may already be hidden (AQ-12).
     */
    fun toggleTheme(
        current: Set<SaverTheme>,
        theme: SaverTheme,
        clips: List<AerialClip> = emptyList(),
        hidden: Set<String> = emptySet(),
    ): Set<SaverTheme> {
        val next = if (theme in current) current - theme else current + theme
        if (next.isEmpty()) return current
        if (theme in current && clips.isNotEmpty() &&
            clips.none { it.theme in next && it.id !in hidden }
        ) {
            return current
        }
        return next
    }

    /**
     * tvOS never lets the last aerial be hidden: the saver would have nothing
     * to play. [visible] is the number of clips the themes still allow.
     */
    fun toggleHidden(hidden: Set<String>, id: String, visible: Int): Set<String> {
        if (id in hidden) return hidden - id
        if (visible <= 1) return hidden
        return hidden + id
    }

    /**
     * Whole-category switch: hides every clip of a category at once, or brings
     * the already hidden ones back. The last visible aerial of the whole pack
     * stays on, the same rule [toggleHidden] applies to a single clip.
     */
    fun toggleGroup(hidden: Set<String>, ids: Set<String>, visible: Int): Set<String> {
        if (ids.isEmpty()) return hidden
        if (ids.all { it in hidden }) return hidden - ids
        val hiding = ids.count { it !in hidden }
        if (visible - hiding < 1) return hidden
        return hidden + ids
    }

    fun encodeHidden(ids: Set<String>): String = ids.sorted().joinToString(",")

    fun decodeHidden(raw: String?): Set<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun encodeThemes(themes: Set<SaverTheme>): String =
        themes.sortedBy { it.ordinal }.joinToString(",") { it.key }

    fun decodeThemes(raw: String?): Set<SaverTheme> {
        val parsed = raw.orEmpty()
            .split(',')
            .map { key -> LEGACY_KEYS[key] ?: key }
            .mapNotNull { key -> SaverTheme.entries.firstOrNull { it.key == key } }
            .toSet()
        return parsed.ifEmpty { DEFAULT_THEMES }
    }
}

object SaverPreferences {
    internal const val KEY_DELAY_MINUTES = "saver_delay_minutes"
    internal const val KEY_MUSIC = "saver_music"
    internal const val KEY_REFRESH = "saver_refresh"
    internal const val KEY_REFRESH_ATTEMPT = "saver_refresh_attempt_ms"
    internal const val KEY_THEMES = "saver_themes"
    internal const val KEY_HIDDEN_CLIPS = "saver_hidden_clips"

    fun delay(context: Context): SaverDelay =
        SaverDelay.fromMinutes(
            SafePreferences.int(prefs(context), KEY_DELAY_MINUTES, SaverDelay.FIVE.minutes),
        )

    fun setDelay(context: Context, delay: SaverDelay) {
        prefs(context).edit().putInt(KEY_DELAY_MINUTES, delay.minutes).apply()
    }

    fun music(context: Context): Boolean =
        SafePreferences.boolean(prefs(context), KEY_MUSIC, true)

    fun setMusic(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MUSIC, enabled).apply()
    }

    fun refresh(context: Context): SaverRefresh {
        val raw = SafePreferences.string(prefs(context), KEY_REFRESH, null)
        return SaverRefresh.entries.firstOrNull { it.name == raw } ?: SaverRefresh.WEEKLY
    }

    fun setRefresh(context: Context, refresh: SaverRefresh) {
        prefs(context).edit().putString(KEY_REFRESH, refresh.name).apply()
    }

    fun refreshAttempt(context: Context): Long =
        SafePreferences.long(prefs(context), KEY_REFRESH_ATTEMPT, 0L)

    fun setRefreshAttempt(context: Context, atMs: Long) {
        prefs(context).edit().putLong(KEY_REFRESH_ATTEMPT, atMs).apply()
    }

    fun themes(context: Context): Set<SaverTheme> =
        SaverSettings.decodeThemes(SafePreferences.string(prefs(context), KEY_THEMES, null))

    fun toggleTheme(context: Context, theme: SaverTheme, clips: List<AerialClip>): Set<SaverTheme> {
        val next = SaverSettings.toggleTheme(themes(context), theme, clips, hidden(context))
        prefs(context).edit().putString(KEY_THEMES, SaverSettings.encodeThemes(next)).apply()
        return next
    }

    fun toggleTheme(context: Context, theme: SaverTheme): Set<SaverTheme> =
        toggleTheme(context, theme, emptyList())

    fun hidden(context: Context): Set<String> =
        SaverSettings.decodeHidden(SafePreferences.string(prefs(context), KEY_HIDDEN_CLIPS, null))

    fun toggleHidden(context: Context, id: String, visible: Int): Set<String> {
        val next = SaverSettings.toggleHidden(hidden(context), id, visible)
        prefs(context).edit().putString(KEY_HIDDEN_CLIPS, SaverSettings.encodeHidden(next)).apply()
        return next
    }

    fun toggleGroup(context: Context, ids: Set<String>, visible: Int): Set<String> {
        val next = SaverSettings.toggleGroup(hidden(context), ids, visible)
        prefs(context).edit().putString(KEY_HIDDEN_CLIPS, SaverSettings.encodeHidden(next)).apply()
        return next
    }

    fun reset(context: Context) {
        prefs(context).edit()
            .putInt(KEY_DELAY_MINUTES, SaverDelay.FIVE.minutes)
            .putBoolean(KEY_MUSIC, true)
            .putString(KEY_REFRESH, SaverRefresh.WEEKLY.name)
            .putString(KEY_THEMES, SaverSettings.encodeThemes(SaverSettings.DEFAULT_THEMES))
            .remove(KEY_HIDDEN_CLIPS)
            .apply()
    }

    internal fun prefs(context: Context) =
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
}
