package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.whatuneed.ovisiontv.kotlin.data.Weather

/** Cached weather shown on the Home pill; refreshed by [HomeWeatherEffects]. */
internal class HomeWeatherState(initiallyEnabled: Boolean) {
    var enabled by mutableStateOf(initiallyEnabled)
    var data by mutableStateOf<Weather.Data?>(null)
    var stale by mutableStateOf(false)
}
