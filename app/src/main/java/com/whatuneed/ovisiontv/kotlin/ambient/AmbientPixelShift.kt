package com.whatuneed.ovisiontv.kotlin.ambient

/**
 * A small, deterministic offset for a clock face. It is not a general animation
 * and it is not marketed as "OLED-safe": it only stops one bright element from
 * occupying exactly the same pixels for the whole session.
 *
 * Values are design dp, not screen pixels, so the same pattern works on 720p
 * and 4K. The offset changes once a minute and never leaves the safe area.
 */
data class AmbientShift(val xDp: Float, val yDp: Float)

object AmbientPixelShift {
    const val STEP_MS = 60_000L

    private val offsets = listOf(
        AmbientShift(0f, 0f),
        AmbientShift(9f, 6f),
        AmbientShift(0f, 0f),
        AmbientShift(-9f, -6f),
    )

    val cycle: Int get() = offsets.size

    fun offset(step: Long): AmbientShift {
        val index = ((step % offsets.size) + offsets.size) % offsets.size
        return offsets[index.toInt()]
    }
}
