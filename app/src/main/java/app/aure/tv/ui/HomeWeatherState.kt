package app.aure.tv.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.aure.tv.data.Weather

/** Cached weather shown on the Home pill; refreshed by [HomeWeatherEffects]. */
internal class HomeWeatherState(initiallyEnabled: Boolean) {
    var enabled by mutableStateOf(initiallyEnabled)
    var data by mutableStateOf<Weather.Data?>(null)
    var stale by mutableStateOf(false)
}
