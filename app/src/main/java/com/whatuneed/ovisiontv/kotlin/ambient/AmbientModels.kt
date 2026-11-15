package com.whatuneed.ovisiontv.kotlin.ambient

import android.content.Context
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
    ;

    companion object {
        fun fromKey(raw: String?): AmbientMode =
            entries.firstOrNull { it.key == raw } ?: AERIAL
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
)

/**
 * The single owner of the new ambient key. The old `saver_*` keys stay the
 * source for the Aerial options, so an upgrade does not reset a user's themes
 * or delay.
 */
object AmbientSettingsStore {
    const val KEY_MODE = "ambient_mode"

    fun load(context: Context): AmbientSettings = AmbientSettings(
        mode = mode(context),
        delay = SaverPreferences.delay(context),
        duringMusic = SaverPreferences.music(context),
        themes = SaverPreferences.themes(context),
        hidden = SaverPreferences.hidden(context),
        refresh = SaverPreferences.refresh(context),
    )

    fun mode(context: Context): AmbientMode =
        AmbientMode.fromKey(
            SafePreferences.string(prefs(context), KEY_MODE, null),
        )

    fun setMode(context: Context, mode: AmbientMode) {
        prefs(context).edit().putString(KEY_MODE, mode.key).apply()
    }

    /** Reset is the same reset the Aerial page always did, plus the mode. */
    fun reset(context: Context) {
        SaverPreferences.reset(context)
        setMode(context, AmbientMode.AERIAL)
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
