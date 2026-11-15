package com.whatuneed.ovisiontv.kotlin.ambient

/**
 * Pure timing for the optional protection of an unattended ambient surface.
 * No Android clock, no preferences, no panel guesses: the caller supplies the
 * elapsed time and the chosen limits.
 *
 * Dimming is deliberately gentle and never turns the screen black. The first
 * step is 80%, the second 60%, five minutes after the chosen delay.
 */
object AmbientPowerPolicy {
    const val DIM_STEP_MS = 5 * 60_000L
    const val SECOND_DIM_ALPHA = 0.6f
    const val FIRST_DIM_ALPHA = 0.8f

    fun dimAlpha(elapsedMs: Long, dimAfter: AmbientDim): Float {
        if (dimAfter == AmbientDim.NEVER) return 1f
        val start = dimAfter.minutes * 60_000L
        return when {
            elapsedMs >= start + DIM_STEP_MS -> SECOND_DIM_ALPHA
            elapsedMs >= start -> FIRST_DIM_ALPHA
            else -> 1f
        }
    }

    fun sessionExpired(elapsedMs: Long, maxSession: AmbientSession): Boolean {
        if (maxSession == AmbientSession.NEVER) return false
        return elapsedMs >= maxSession.minutes * 60_000L
    }
}
