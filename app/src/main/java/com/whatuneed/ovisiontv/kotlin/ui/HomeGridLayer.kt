package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.ui.components.AppCard
import com.whatuneed.ovisiontv.kotlin.ui.components.FolderCard

/**
 * The app grid and the labels under it. The library shift lives on this layer,
 * not on every tile, so reorder animation still works on item indices and the
 * dock row moves to the top while the shelf fades out.
 */
@Composable
internal fun HomeGridLayer(
    items: List<HomeItem>,
    columns: Int,
    libraryMode: Boolean,
    libraryVisible: Boolean,
    libraryFade: Animatable<Float, AnimationVector1D>,
    focusIndex: Int,
    shelfFocus: Int,
    capsuleFocused: Boolean,
    weatherFocused: Boolean,
    pickupActive: Boolean,
    pickupOrigin: Int?,
    editing: Boolean,
    confirmIndex: Int?,
    scale: TvScale,
    reduceMotion: Boolean,
    context: Context,
    tileRequesters: MutableMap<String, FocusRequester>,
    pickupRequester: FocusRequester,
    shift: () -> Float,
    onPlaced: () -> Unit,
    onActivate: (Int) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { onPlaced() }
            .then(
                // TalkBack follows the screen: while the carousel is in
                // charge the tiles are not on it, so they stop being
                // focus targets behind the poster.
                if (shelfFocus >= 0) Modifier.clearAndSetSemantics {} else Modifier,
            )
            .graphicsLayer {
                translationY = shift() * scale.factor * density
            },
    ) {
        // 04_APP_LIBRARY: the screen is one full list, but tvOS keeps the
        // favourites row apart from everything else, so both get a heading.
        // Headings sit in the same shifted layer and scroll with their rows.
        if (libraryVisible) {
            LibraryHeading(
                text = stringResource(R.string.library_favorites),
                y = TvOs.ROW_TOP - 52f,
                fade = libraryFade,
                scale = scale,
            )
            LibraryHeading(
                text = stringResource(R.string.library_all_apps),
                y = TvOs.ROW_TOP + TvOs.ROW_PITCH - 52f,
                fade = libraryFade,
                scale = scale,
            )
        }
        // The resting Home is the dock row only. A seventh app used to
        // leave a 26 px white sliver at the bottom edge: a real tile, but
        // with no room for its label or focus ring. DOWN moves the cursor
        // into the second row first, which switches on libraryMode; the
        // full grid is composed then, so focus never targets a missing
        // node.
        val visibleItems = if (libraryMode) items else items.take(columns)
        for ((index, item) in visibleItems.withIndex()) {
            if (pickupActive && index == pickupOrigin) continue
            val focused = !pickupActive && index == focusIndex && shelfFocus < 0 &&
                !capsuleFocused && !weatherFocused

            key(item.key) {
                val tileRequester = remember(item.key) { FocusRequester() }
                tileRequesters[item.key] = tileRequester
                // A dropped tile and every neighbour slide to their new
                // index instead of teleporting; 200 ms is a project value.
                val x by animateFloatAsState(
                    targetValue = TvOs.SAFE_AREA + (index % columns) * TvOs.ICON_COLUMN_PITCH,
                    animationSpec = tween(motionDuration(TILE_MOVE_MS, reduceMotion), easing = FastOutSlowInEasing),
                    label = "tileX",
                )
                val y by animateFloatAsState(
                    targetValue = TvOs.ROW_TOP + (index / columns) * TvOs.ROW_PITCH,
                    animationSpec = tween(motionDuration(TILE_MOVE_MS, reduceMotion), easing = FastOutSlowInEasing),
                    label = "tileY",
                )
                when (item) {
                    is HomeItem.AppItem -> AppCard(
                        app = item.app,
                        focused = focused,
                        scale = scale,
                        editing = editing,
                        confirming = confirmIndex == index,
                        focusRequester = tileRequester,
                        onActivate = { onActivate(index) },
                        modifier = Modifier
                            .offset { IntOffset(scale.dp(x).roundToPx(), scale.dp(y).roundToPx()) }
                            .size(scale.dp(TvOs.ICON_WIDTH), scale.dp(TvOs.ICON_HEIGHT)),
                    )

                    is HomeItem.FolderItem -> FolderCard(
                        folder = item,
                        focused = focused,
                        scale = scale,
                        editing = editing,
                        confirming = confirmIndex == index,
                        stateLabel = context.resources.getQuantityString(
                            R.plurals.folder_app_count,
                            item.apps.size,
                            item.apps.size,
                        ),
                        focusRequester = tileRequester,
                        onActivate = { onActivate(index) },
                        modifier = Modifier
                            .offset { IntOffset(scale.dp(x).roundToPx(), scale.dp(y).roundToPx()) }
                            .size(scale.dp(TvOs.ICON_WIDTH), scale.dp(TvOs.ICON_HEIGHT)),
                    )
                }
            }
        }

        // tvOS keeps the app name under every tile. The card semantics
        // already announces it; this line is for the viewer, so it is
        // decorative to TalkBack and moves with the grid layer.
        for ((index, item) in visibleItems.withIndex()) {
            if (pickupActive && index == pickupOrigin) continue
            val label = when (item) {
                is HomeItem.AppItem -> item.app.label
                is HomeItem.FolderItem -> item.name
            }
            TileLabel(
                text = label,
                x = TvOs.SAFE_AREA + (index % columns) * TvOs.ICON_COLUMN_PITCH,
                y = TvOs.ROW_TOP + (index / columns) * TvOs.ROW_PITCH +
                    TvOs.ICON_HEIGHT + 22f,
                scale = scale,
            )
        }

        if (pickupActive) {
            val origin = pickupOrigin
            val dragged = origin?.let { items.getOrNull(it) }
            if (origin != null && dragged != null) {
                key("picked:${dragged.key}") {
                    val x by animateFloatAsState(
                        targetValue = TvOs.SAFE_AREA +
                            (focusIndex % columns) * TvOs.ICON_COLUMN_PITCH,
                        animationSpec = tween(motionDuration(TILE_MOVE_MS, reduceMotion), easing = FastOutSlowInEasing),
                        label = "pickedX",
                    )
                    val y by animateFloatAsState(
                        targetValue = TvOs.ROW_TOP +
                            (focusIndex / columns) * TvOs.ROW_PITCH,
                        animationSpec = tween(motionDuration(TILE_MOVE_MS, reduceMotion), easing = FastOutSlowInEasing),
                        label = "pickedY",
                    )
                    when (dragged) {
                        is HomeItem.AppItem -> AppCard(
                            app = dragged.app,
                            focused = false,
                            scale = scale,
                            editing = editing,
                            pickedUp = true,
                            focusRequester = pickupRequester,
                            modifier = Modifier
                                .offset { IntOffset(scale.dp(x).roundToPx(), scale.dp(y).roundToPx()) }
                                .size(scale.dp(TvOs.ICON_WIDTH), scale.dp(TvOs.ICON_HEIGHT)),
                        )

                        is HomeItem.FolderItem -> FolderCard(
                            folder = dragged,
                            focused = false,
                            scale = scale,
                            editing = editing,
                            pickedUp = true,
                            focusRequester = pickupRequester,
                            modifier = Modifier
                                .offset { IntOffset(scale.dp(x).roundToPx(), scale.dp(y).roundToPx()) }
                                .size(scale.dp(TvOs.ICON_WIDTH), scale.dp(TvOs.ICON_HEIGHT)),
                        )
                    }
                }
            }
        }
    }
}
