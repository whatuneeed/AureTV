package app.aure.tv.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import app.aure.tv.data.WeatherPreferences
import app.aure.tv.discovery.DiscoveryItem
import app.aure.tv.data.NetworkKind

/**
 * Weather pill, status pill and the shelf modals. The pills only change the
 * overlay state; the modals close their own target and hand the card back to
 * HomeScreen, which decides how to open it.
 */
@Composable
internal fun BoxScope.HomeChromeHost(
    overlays: HomeOverlayState,
    weather: HomeWeatherState,
    grid: HomeGridState,
    shelf: HomeShelfState,
    shelfCards: List<DiscoveryItem>,
    scale: TvScale,
    reduceMotion: Boolean,
    weatherVisible: Boolean,
    menuOpen: Boolean,
    context: Context,
    weatherRequester: FocusRequester,
    capsuleRequester: FocusRequester,
    capsuleClock: String,
    capsuleNetwork: NetworkKind,
    profileEmoji: String?,
    profileColor: Long?,
    profileName: String?,
    launchProgress: Animatable<Float, AnimationVector1D>,
    onOpenShelfContent: (DiscoveryItem) -> Unit,
) {
    HomeTopChrome(
        scale = scale,
        reduceMotion = reduceMotion,
        weatherPanelOpen = overlays.weatherPanelOpen,
        weatherData = weather.data,
        weatherStale = weather.stale,
        weatherVisible = weatherVisible,
        weatherFocused = overlays.weatherFocused,
        weatherManual = { WeatherPreferences.manual(context) },
        weatherRequester = weatherRequester,
        capsuleFocused = overlays.capsuleFocused,
        controlCenterOpen = overlays.controlCenterOpen,
        capsuleClock = capsuleClock,
        capsuleNetwork = capsuleNetwork,
        capsuleRequester = capsuleRequester,
        profileEmoji = profileEmoji,
        profileColor = profileColor,
        profileName = profileName,
        openFolder = grid.openFolder,
        launching = grid.launching,
        menuOpen = menuOpen,
        searchOpen = overlays.searchOpen,
        onCloseWeatherPanel = {
            overlays.weatherPanelOpen = false
            // The Java popover returned to the weather card.
            overlays.weatherFocused = true
        },
        onWeatherActivate = {
            overlays.weatherFromShelf = false
            overlays.weatherFocused = true
            overlays.weatherPanelOpen = true
        },
        onStatusActivate = {
            // TalkBack has no key event: take the pill and open the
            // panel the same way OK does.
            overlays.capsuleFromShelf = false
            overlays.capsuleFocused = true
            overlays.controlCenterOpen = true
        },
    )

    HomeContentModals(
        playingTrailer = shelf.playingTrailer,
        onlineInfo = shelf.onlineInfo,
        onlineInfoTarget = shelf.onlineInfoTarget,
        launching = grid.launching,
        launchProgress = launchProgress,
        scale = scale,
        onOpenTrailerTarget = { target ->
            shelf.playingTrailer = null
            onOpenShelfContent(target)
        },
        onDismissTrailer = {
            shelf.playingTrailer = null
            // Closing the preview returns to the card that opened it,
            // not to the dock: the modal effect lowered the shelf while
            // the preview played, so put its cursor back before asking
            // TopShelf for Android focus again.
            if (shelfCards.isNotEmpty()) {
                shelf.focus = shelf.selected.coerceIn(0, shelfCards.lastIndex)
            }
            shelf.focusTick++
        },
        onOpenOnlineTarget = { provider ->
            shelf.onlineInfo = null
            shelf.onlineInfoTarget = null
            onOpenShelfContent(provider)
        },
        onDismissOnlineInfo = {
            shelf.onlineInfo = null
            shelf.onlineInfoTarget = null
            if (shelfCards.isNotEmpty()) {
                shelf.focus = shelf.selected.coerceIn(0, shelfCards.lastIndex)
            }
            shelf.focusTick++
        },
    )
}
