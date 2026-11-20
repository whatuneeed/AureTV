package app.aure.tv.data

/**
 * Start rule of the saver, kept free of Android types. The caller owns the
 * idle clock and the music probe, so both can be faked in tests.
 */
object SaverSchedule {
    fun isDue(
        delay: SaverDelay,
        idleMs: Long,
        musicPlaying: Boolean,
        showDuringMusic: Boolean,
    ): Boolean {
        if (delay == SaverDelay.NEVER) return false
        // tvOS keeps the screen awake while music plays unless the user asked
        // for the saver to cover the now-playing screen as well.
        if (musicPlaying && !showDuringMusic) return false
        return idleMs >= delay.minutes * 60_000L
    }
}
