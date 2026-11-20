package app.aure.tv.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import app.aure.tv.data.NetworkKind
import app.aure.tv.data.Weather
import app.aure.tv.ui.components.CC_CAPSULE_SIDE
import app.aure.tv.ui.components.CC_CAPSULE_TOP
import app.aure.tv.ui.components.StatusCapsule
import app.aure.tv.ui.components.WEATHER_CAPSULE_LEFT
import app.aure.tv.ui.components.WEATHER_CAPSULE_TOP
import app.aure.tv.ui.components.WeatherCapsule
import app.aure.tv.ui.components.WeatherPanel

/**
 * Top chrome of Home: the forecast panel, the weather pill and the status pill
 * that grows into Control Center. A panel, the menu, search or the launch zoom
 * covers the screen and hides the pills.
 */
@Composable
internal fun BoxScope.HomeTopChrome(
    scale: TvScale,
    reduceMotion: Boolean,
    weatherPanelOpen: Boolean,
    weatherData: Weather.Data?,
    weatherStale: Boolean,
    weatherVisible: Boolean,
    weatherFocused: Boolean,
    weatherManual: () -> Boolean,
    weatherRequester: FocusRequester,
    capsuleFocused: Boolean,
    controlCenterOpen: Boolean,
    capsuleClock: String,
    capsuleNetwork: NetworkKind,
    capsuleRequester: FocusRequester,
    profileEmoji: String?,
    profileColor: Long?,
    profileName: String?,
    openFolder: OpenFolder?,
    launching: Launching?,
    menuOpen: Boolean,
    searchOpen: Boolean,
    onCloseWeatherPanel: () -> Unit,
    onWeatherActivate: () -> Unit,
    onStatusActivate: () -> Unit,
) {
    if (weatherPanelOpen) {
        WeatherPanel(
            scale = scale,
            data = weatherData,
            stale = weatherStale,
            reduceMotion = reduceMotion,
            onClose = onCloseWeatherPanel,
        )
    }

    weatherData?.takeIf { weatherVisible }?.let { data ->
        WeatherCapsule(
            scale = scale,
            data = data,
            stale = weatherStale,
            manual = weatherManual(),
            focused = weatherFocused,
            focusRequester = weatherRequester,
            onActivate = onWeatherActivate,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = scale.dp(WEATHER_CAPSULE_LEFT),
                    y = scale.dp(WEATHER_CAPSULE_TOP),
                ),
        )
    }

    if (openFolder == null && launching == null && !menuOpen && !searchOpen &&
        !weatherPanelOpen
    ) {
        StatusCapsule(
            scale = scale,
            reduceMotion = reduceMotion,
            focused = capsuleFocused,
            promoted = controlCenterOpen,
            clock = capsuleClock,
            network = capsuleNetwork,
            focusRequester = capsuleRequester,
            profileEmoji = profileEmoji,
            profileColor = profileColor,
            profileName = profileName,
            onActivate = onStatusActivate,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = -scale.dp(CC_CAPSULE_SIDE),
                    y = scale.dp(CC_CAPSULE_TOP),
                ),
        )
    }
}
