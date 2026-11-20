package app.aure.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.aure.tv.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The interface language decides 12/24-hour, as the Home clock already does:
 * English reads 3 PM, Russian reads 15.
 */
internal fun clockPattern(language: String, minutes: Boolean): String = when {
    language == "ru" -> if (minutes) "H:mm" else "H"
    else -> if (minutes) "h:mm a" else "h a"
}

/**
 * The short condition wording Apple Weather uses, keyed by the WMO code the
 * engine normalises every source to. Unknown codes stay silent, as in the Java
 * launcher, rather than claiming a condition the provider did not report.
 */
@Composable
internal fun weatherDescription(code: Int, day: Boolean): String {
    val id = when (code) {
        0 -> if (day) R.string.weather_sunny else R.string.weather_clear
        1 -> if (day) R.string.weather_mostly_sunny else R.string.weather_mostly_clear
        2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_fog
        51, 53, 55, 56, 57 -> R.string.weather_drizzle
        61, 63, 80, 81 -> R.string.weather_rain
        65, 82 -> R.string.weather_heavy_rain
        66, 67 -> R.string.weather_sleet
        71, 73, 75, 77, 85, 86 -> R.string.weather_snow
        95, 96, 99 -> R.string.weather_thunderstorm
        else -> 0
    }
    return if (id == 0) "" else stringResource(id)
}

/** The first hourly column is the current reading, the rest are whole hours. */
@Composable
internal fun weatherHourLabel(time: Long, first: Boolean): String {
    if (first) return stringResource(R.string.weather_now)
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val format = remember(locale) {
        SimpleDateFormat(clockPattern(locale.language, minutes = false), locale)
    }
    return format.format(Date(time))
}

/** Footer stamp of the last successful fetch, in the interface language's clock. */
@Composable
internal fun weatherTimeLabel(time: Long): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val format = remember(locale) {
        SimpleDateFormat(clockPattern(locale.language, minutes = true), locale)
    }
    return format.format(Date(time))
}

/** "Today" first, then the short weekday as the Java launcher wrote it. */
@Composable
internal fun weatherDayLabel(date: String, first: Boolean, russian: Boolean): String {
    if (first) return stringResource(R.string.weather_today)
    val locale = remember(russian) { Locale.forLanguageTag(if (russian) "ru" else "en") }
    val raw = remember(date, locale) {
        runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
                ?: return@runCatching ""
            SimpleDateFormat("EE", locale).format(parsed).replace(".", "").trim()
        }.getOrDefault("")
    }
    if (raw.length < 2) return raw
    return raw.substring(0, 1).uppercase(locale) + raw.substring(1).lowercase(locale)
}
