package com.whatuneed.ovisiontv.kotlin.ambient

import android.content.Context
import com.whatuneed.ovisiontv.kotlin.data.AerialQuality
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.SafePreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverDelay
import com.whatuneed.ovisiontv.kotlin.data.SaverPreferences
import com.whatuneed.ovisiontv.kotlin.data.SaverRefresh
import com.whatuneed.ovisiontv.kotlin.data.SaverSchedule
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme

/**
 * The screensaver is one ambient surface with a mode on top of it. The keys
 * are stored and go into backups, so they must not be renamed.
 */
enum class AmbientMode(val key: String) {
    AERIAL("aerial"),
    CLOCK("clock"),
    MINIMAL("minimal"),
    PHOTOS("photos"),
    REDDIT("reddit"),
    ;

    companion object {
        fun fromKey(raw: String?): AmbientMode =
            entries.firstOrNull { it.key == raw } ?: AERIAL
    }
}

/**
 * Clock follows the system by default. An explicit choice is stored because a
 * TV can report 24-hour time while the viewer prefers 12-hour, or the reverse.
 */
enum class AmbientClockFormat(val key: String) {
    SYSTEM("system"),
    TWELVE("twelve"),
    TWENTY_FOUR("twenty_four"),
    ;

    fun use24Hour(systemIs24Hour: Boolean): Boolean = when (this) {
        SYSTEM -> systemIs24Hour
        TWELVE -> false
        TWENTY_FOUR -> true
    }

    companion object {
        fun fromKey(raw: String?): AmbientClockFormat =
            entries.firstOrNull { it.key == raw } ?: SYSTEM
    }
}

/** Dimming starts after the chosen delay; it never blacks the surface out. */
enum class AmbientDim(val key: String, val minutes: Int) {
    NEVER("never", 0),
    TEN("ten", 10),
    TWENTY("twenty", 20),
    THIRTY("thirty", 30),
    ;

    companion object {
        fun fromKey(raw: String?): AmbientDim =
            entries.firstOrNull { it.key == raw } ?: NEVER
    }
}

/** A voluntary safety limit for an unattended ambient surface. */
enum class AmbientSession(val key: String, val minutes: Int) {
    NEVER("never", 0),
    THIRTY("thirty", 30),
    SIXTY("sixty", 60),
    ONE_TWENTY("one_twenty", 120),
    ;

    companion object {
        fun fromKey(raw: String?): AmbientSession =
            entries.firstOrNull { it.key == raw } ?: NEVER
    }
}

/**
 * Everything the ambient surface needs for one session. It is a snapshot, not
 * a live prefs reader: Home and the DreamService read it once and then keep a
 * consistent set of options while the overlay is visible.
 */
data class AmbientSettings(
    val mode: AmbientMode,
    val delay: SaverDelay,
    val duringMusic: Boolean,
    val themes: Set<SaverTheme>,
    val hidden: Set<String>,
    val refresh: SaverRefresh,
    val clockFormat: AmbientClockFormat = AmbientClockFormat.SYSTEM,
    val pixelShift: Boolean = true,
    val dimAfter: AmbientDim = AmbientDim.NEVER,
    val maxSession: AmbientSession = AmbientSession.NEVER,
    val showWeather: Boolean = false,
    val aerialQuality: AerialQuality = AerialQuality.COMPATIBLE,
    val redditSubreddit: String = AmbientRedditStore.DEFAULT_SUBREDDIT,
    val redditRefresh: RedditRefresh = RedditRefresh.TWELVE,
    val redditSelection: RedditSelection = RedditSelection.ALL,
    val redditSelected: Set<String> = emptySet(),
)

/**
 * The single owner of the new ambient key. The old `saver_*` keys stay the
 * source for the Aerial options, so an upgrade does not reset a user's themes
 * or delay.
 */
object AmbientSettingsStore {
    const val KEY_MODE = "ambient_mode"
    const val KEY_CLOCK_FORMAT = "ambient_clock_format"
    const val KEY_PIXEL_SHIFT = "ambient_pixel_shift"
    const val KEY_DIM_AFTER = "ambient_dim_after"
    const val KEY_MAX_SESSION = "ambient_max_session"
    const val KEY_SHOW_WEATHER = "ambient_show_weather"
    const val KEY_AERIAL_QUALITY = "ambient_aerial_quality"

    fun load(context: Context): AmbientSettings = AmbientSettings(
        mode = mode(context),
        delay = SaverPreferences.delay(context),
        duringMusic = SaverPreferences.music(context),
        themes = SaverPreferences.themes(context),
        hidden = SaverPreferences.hidden(context),
        refresh = SaverPreferences.refresh(context),
        clockFormat = clockFormat(context),
        pixelShift = pixelShift(context),
        dimAfter = dimAfter(context),
        maxSession = maxSession(context),
        showWeather = showWeather(context),
        aerialQuality = aerialQuality(context),
        redditSubreddit = AmbientRedditStore.subreddit(context),
        redditRefresh = AmbientRedditStore.refresh(context),
        redditSelection = AmbientRedditStore.selection(context),
        redditSelected = AmbientRedditStore.selectedIds(context),
    )

    fun mode(context: Context): AmbientMode =
        AmbientMode.fromKey(
            SafePreferences.string(prefs(context), KEY_MODE, null),
        )

    fun setMode(context: Context, mode: AmbientMode) {
        prefs(context).edit().putString(KEY_MODE, mode.key).apply()
    }

    fun clockFormat(context: Context): AmbientClockFormat =
        AmbientClockFormat.fromKey(
            SafePreferences.string(prefs(context), KEY_CLOCK_FORMAT, null),
        )

    fun setClockFormat(context: Context, format: AmbientClockFormat) {
        prefs(context).edit().putString(KEY_CLOCK_FORMAT, format.key).apply()
    }

    fun pixelShift(context: Context): Boolean =
        SafePreferences.boolean(prefs(context), KEY_PIXEL_SHIFT, true)

    fun setPixelShift(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PIXEL_SHIFT, enabled).apply()
    }

    fun dimAfter(context: Context): AmbientDim =
        AmbientDim.fromKey(
            SafePreferences.string(prefs(context), KEY_DIM_AFTER, null),
        )

    fun setDimAfter(context: Context, dim: AmbientDim) {
        prefs(context).edit().putString(KEY_DIM_AFTER, dim.key).apply()
    }

    fun maxSession(context: Context): AmbientSession =
        AmbientSession.fromKey(
            SafePreferences.string(prefs(context), KEY_MAX_SESSION, null),
        )

    fun setMaxSession(context: Context, session: AmbientSession) {
        prefs(context).edit().putString(KEY_MAX_SESSION, session.key).apply()
    }

    fun showWeather(context: Context): Boolean =
        SafePreferences.boolean(prefs(context), KEY_SHOW_WEATHER, false)

    fun setShowWeather(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_WEATHER, enabled).apply()
    }

    fun aerialQuality(context: Context): AerialQuality =
        AerialQuality.fromKey(
            SafePreferences.string(prefs(context), KEY_AERIAL_QUALITY, null),
        )

    fun setAerialQuality(context: Context, quality: AerialQuality) {
        prefs(context).edit().putString(KEY_AERIAL_QUALITY, quality.key).apply()
    }

    /** Reset is the same reset the Aerial page always did, plus ambient fields. */
    fun reset(context: Context) {
        SaverPreferences.reset(context)
        setMode(context, AmbientMode.AERIAL)
        setClockFormat(context, AmbientClockFormat.SYSTEM)
        setPixelShift(context, true)
        setDimAfter(context, AmbientDim.NEVER)
        setMaxSession(context, AmbientSession.NEVER)
        setShowWeather(context, false)
        setAerialQuality(context, AerialQuality.COMPATIBLE)
    }

    internal fun prefs(context: Context) =
        context.getSharedPreferences(LauncherPrefs.NAME, Context.MODE_PRIVATE)
}

/**
 * Start rule of the ambient surface. The old [SaverSchedule] remains the pure
 * core; this is the single place that knows where its inputs come from.
 */
object AmbientScheduler {
    fun isDue(
        settings: AmbientSettings,
        idleMs: Long,
        musicPlaying: Boolean,
    ): Boolean = SaverSchedule.isDue(
        settings.delay,
        idleMs,
        musicPlaying,
        settings.duringMusic,
    )

    /** Only Aerial needs a non-empty clip plan; clock/minimal draw themselves. */
    fun needsClips(mode: AmbientMode): Boolean = mode == AmbientMode.AERIAL
}
