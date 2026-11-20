package app.aure.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import app.aure.tv.data.HomeItem
import app.aure.tv.data.TvApp
import app.aure.tv.discovery.DiscoveryItem
import app.aure.tv.discovery.DiscoveryRail
import app.aure.tv.discovery.DiscoverySection
import app.aure.tv.online.onlinePreviewUri
import app.aure.tv.ui.components.TopShelf

/**
 * Backdrop, shelf, dock band and the app grid. The whole stack shifts with the
 * dock focus and hides under the shelf; the layers inside keep their own
 * semantics gate so TalkBack cannot reach tiles under a modal.
 */
@Composable
internal fun HomeSceneLayers(
    libraryVisible: Boolean,
    libraryMode: Boolean,
    libraryFade: Animatable<Float, AnimationVector1D>,
    grid: HomeGridState,
    overlays: HomeOverlayState,
    menuOpen: Boolean,
    shelf: HomeShelfState,
    scroll: Animatable<Float, AnimationVector1D>,
    hideShift: Animatable<Float, AnimationVector1D>,
    shelfHide: Float,
    libraryShift: Animatable<Float, AnimationVector1D>,
    scale: TvScale,
    context: Context,
    columns: Int,
    shelfVisible: Boolean,
    shelfApp: TvApp?,
    rails: List<DiscoveryRail>,
    currentSection: DiscoverySection?,
    shelfCards: List<DiscoveryItem>,
    reduceMotion: Boolean,
    previewAutoplay: Boolean,
    kidsMode: Boolean,
    saverVisible: Boolean,
    weatherVisible: Boolean,
    focusTick: Int,
    tileRequesters: MutableMap<String, FocusRequester>,
    pickupRequester: FocusRequester,
    onShelfCard: (DiscoveryItem) -> Unit,
    onSection: (DiscoverySection) -> Unit,
    onActivate: (Int) -> Unit,
    onPlaced: () -> Unit,
) {
    if (libraryVisible) {
        LibraryBackdrop(alpha = { 1f - libraryFade.value })
    }
    Box(
        Modifier
            .fillMaxSize()
            // The overlay/zoom is modal on screen, so TalkBack must not keep
            // offering the tiles it covers.
            .then(
                if (grid.openFolder != null || grid.launching != null || menuOpen ||
                    overlays.newFolderApp != null || overlays.renameApp != null || overlays.searchOpen ||
                    overlays.controlCenterOpen || overlays.weatherPanelOpen || shelf.menu != null ||
                    shelf.playingTrailer != null || shelf.onlineInfo != null
                ) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                val dockShift = if (grid.focusIndex < columns) TvOs.DOCK_FOCUS_SHIFT else 0f
                translationY = (-(scroll.value + dockShift) + hideShift.value) *
                    scale.factor * density
            },
    ) {
        // 03_FOCUS_ENGINE §9: dock focus lifts the grid and the band, but
        // the shelf stays put; the counter-shift cancels only that part.
        Box(
            Modifier.graphicsLayer {
                val dockShift = if (grid.focusIndex < columns) TvOs.DOCK_FOCUS_SHIFT else 0f
                translationY = dockShift * scale.factor * density
                alpha = libraryFade.value
            },
        ) {
            if (shelfVisible) {
                TopShelf(
                    app = shelfApp,
                    rails = rails,
                    selectedSection = currentSection,
                    selectedCard = shelf.selected,
                    focusedCard = shelf.focus,
                    sectionFocused = shelf.sectionFocused,
                    onCardActivate = onShelfCard,
                    onSectionActivate = onSection,
                    scale = scale,
                    providerState = shelf.snapshot.providerState,
                    reduceMotion = reduceMotion,
                    // The online card's official preview becomes the
                    // motion hero. It is not tied to the shelf cursor:
                    // Home, the shelf and the section bar share the same
                    // selected title. Every modal and Kids Mode removes
                    // the URL so no video plays underneath a panel.
                    heroPreviewUri = onlinePreviewUri(
                        item = shelfCards.getOrNull(shelf.selected),
                        enabled = previewAutoplay && !kidsMode && !reduceMotion &&
                            !libraryMode && !grid.editing && !grid.pickupActive &&
                            !saverVisible && grid.openFolder == null && grid.launching == null &&
                            !menuOpen && overlays.newFolderApp == null && overlays.renameApp == null &&
                            !overlays.searchOpen && !overlays.controlCenterOpen && !overlays.weatherPanelOpen &&
                            shelf.menu == null &&
                            shelf.playingTrailer == null && shelf.onlineInfo == null,
                    ),
                    // The weather pill owns the top-left corner. On the
                    // browsing Home the title moves below it instead of
                    // printing through the forecast; inside the shelf the
                    // pill is hidden and the tvOS title position returns.
                    headingTop = if (weatherVisible) 150f else 57f,
                    focusTick = focusTick + shelf.focusTick,
                )
            }
        }

        // 04_APP_LIBRARY: the band rides up with the dock row instead of
        // fading out, so the first row keeps its dock look at the top.
        DockBand(
            scale = scale,
            reduceMotion = reduceMotion,
            scroll = scroll,
            shift = { libraryShift.value - shelfHide },
            focused = grid.focusIndex < columns && shelf.focus < 0 && !overlays.capsuleFocused &&
                !overlays.weatherFocused,
            fade = { libraryFade.value },
        )

        // The library shift lives on the grid, not on every tile: reorder
        // animation still works on item indices, and this layer moves the
        // dock row to the top while the shelf fades out.
        HomeGridLayer(
            items = grid.items,
            columns = columns,
            libraryMode = libraryMode,
            focusIndex = grid.focusIndex,
            shelfFocus = shelf.focus,
            capsuleFocused = overlays.capsuleFocused,
            weatherFocused = overlays.weatherFocused,
            pickupActive = grid.pickupActive,
            pickupOrigin = grid.pickupOrigin,
            editing = grid.editing,
            confirmIndex = grid.confirmIndex,
            scale = scale,
            reduceMotion = reduceMotion,
            context = context,
            tileRequesters = tileRequesters,
            pickupRequester = pickupRequester,
            shift = { -libraryShift.value + shelfHide },
            onPlaced = onPlaced,
            onActivate = onActivate,
        )
    }
}
