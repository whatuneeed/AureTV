package com.whatuneed.ovisiontv.kotlin.ambient

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatting is pure so the clock mode can be checked without a device. The
 * system's 12/24-hour setting is passed in by the caller.
 */
object AmbientClock {
    fun formatTime(now: ZonedDateTime, is24Hour: Boolean, locale: Locale): String =
        now.format(
            DateTimeFormatter.ofPattern(
                if (is24Hour) "HH:mm" else "h:mm a",
                locale,
            ),
        )

    fun formatDate(now: ZonedDateTime, locale: Locale): String {
        val formatted = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        return formatted.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
    }
}
