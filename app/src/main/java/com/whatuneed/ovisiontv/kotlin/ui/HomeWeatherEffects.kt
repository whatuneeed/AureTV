package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.whatuneed.ovisiontv.kotlin.data.Weather
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import kotlinx.coroutines.delay

/**
 * Cached weather is shown at once. The engine fetches only when the schedule or
 * the settings ask; the wait is computed, so idle time costs no wakes.
 */
@Composable
internal fun HomeWeatherEffects(
    weather: HomeWeatherState,
    context: Context,
    resumeTick: Int,
    foreground: Boolean,
    weatherPanelOpen: Boolean,
) {
    LaunchedEffect(resumeTick, foreground) {
        weather.enabled = WeatherPreferences.enabled(context)
        if (!foreground) return@LaunchedEffect
        if (!weather.enabled) {
            weather.data = null
            weather.stale = false
            return@LaunchedEffect
        }
        while (true) {
            Weather.refresh(context, false)
            weather.data = Weather.cached(context)
            weather.stale = Weather.stale(context)
            delay(Weather.refreshDelay(context))
        }
    }

    LaunchedEffect(weatherPanelOpen) {
        if (!weatherPanelOpen) return@LaunchedEffect
        Weather.refresh(context, false)
        weather.data = Weather.cached(context)
        weather.stale = Weather.stale(context)
    }
}
