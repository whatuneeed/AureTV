package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.isHiddenFolder
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryRail
import com.whatuneed.ovisiontv.kotlin.ui.components.TvKeyRepeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Home remote: one-shot filtering, top surfaces (weather, capsule, section,
 * shelf) and the grid itself. The caller keeps the modal gates; this function
 * only runs when the grid is allowed to see a key.
 */
internal fun handleHomeKey(
    event: KeyEvent,
    grid: HomeGridState,
    shelf: HomeShelfState,
    overlays: HomeOverlayState,
    rails: List<DiscoveryRail>,
    shelfCards: List<DiscoveryItem>,
    weatherVisible: Boolean,
    libraryMode: Boolean,
    columns: Int,
    kidsMode: Boolean,
    keyRepeat: TvKeyRepeat,
    scope: CoroutineScope,
    onOrderChanged: (List<HomeItem>) -> Unit,
    cellCenter: (Int) -> Offset,
    startLaunch: (TvApp, Offset) -> Unit,
    activateShelfCard: (DiscoveryItem) -> Unit,
    context: Context,
    focusCapsule: (Boolean) -> Unit,
): Boolean {
    return when (event.type) {
        KeyEventType.KeyDown -> {
            // A held key used to repeat at the system rate: OK
            // restarted the edit timer and never opened grid.editing.
            if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                return true
            }
            // The weather pill, the status pill, the section bar
            // and the shelf own the remote before the grid. The
            // handler returns false when none of them is focused.
            if (
                handleHomeTopKey(
                    event = event,
                    shelf = shelf,
                    grid = grid,
                    overlays = overlays,
                    rails = rails,
                    shelfCards = shelfCards,
                    weatherVisible = weatherVisible,
                    scope = scope,
                    focusCapsule = { focusCapsule(it) },
                )
            ) {
                return true
            }
            when (event.key) {
                Key.DirectionLeft -> {
                    grid.moveFocus(-1, 0)
                    keyRepeat.startRepeat(scope) { grid.moveFocus(-1, 0) }
                    true
                }

                Key.DirectionRight -> {
                    grid.moveFocus(1, 0)
                    keyRepeat.startRepeat(scope) { grid.moveFocus(1, 0) }
                    true
                }

                Key.DirectionUp -> {
                    if (grid.focusIndex < columns && !grid.editing && !grid.pickupActive) {
                        if (rails.isNotEmpty()) {
                            shelf.enterShelf()
                        } else {
                            // No recommendations: the row above the
                            // dock is the status pill (Java focusTop).
                            focusCapsule(false)
                        }
                    } else {
                        grid.moveFocus(0, -1)
                        keyRepeat.startRepeat(scope) { grid.moveFocus(0, -1) }
                    }
                    true
                }

                Key.DirectionDown -> {
                    grid.moveFocus(0, 1)
                    keyRepeat.startRepeat(scope) { grid.moveFocus(0, 1) }
                    true
                }

                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    overlays.okDown = true
                    // The auto-repeat that follows a hold can arrive after the
                    // edit-mode timer fires, so only the first KeyDown may toggle.
                    if (grid.editing && grid.pickupActive) {
                        // A short press drops the dragged tile where it
                        // already sits; holding OK folds it with the
                        // neighbour the drag pushed aside.
                        grid.longPressHandled = false
                        grid.holdJob?.cancel()
                        grid.holdJob = scope.launch {
                            delay(FOLDER_HOLD_MS)
                            val victim = grid.mergeVictim
                            if (grid.pickupActive && victim != null &&
                                victim in grid.items.indices
                            ) {
                                grid.longPressHandled = true
                                grid.confirmIndex = victim
                                delay(FOLDER_CONFIRM_MS)
                                if (grid.pickupActive) grid.mergePickupInto(victim, context, onOrderChanged)
                            }
                        }
                    } else if (grid.editing) {
                        // tvOS lifts the icon and leaves it on the
                        // cursor until the next click; the press is
                        // consumed so KeyUp does not drop it again.
                        grid.togglePickup(onOrderChanged)
                        overlays.okDown = false
                    } else if (!kidsMode) {
                        // A long OK opens the context menu, as in the
                        // Java build (showOptions on long click). The
                        // edit mode lives inside that menu as "Move".
                        grid.longPressHandled = false
                        grid.holdJob?.cancel()
                        grid.holdJob = scope.launch {
                            delay(MENU_HOLD_MS)
                            grid.longPressHandled = true
                            overlays.okDown = false
                            grid.confirmIndex = null
                            overlays.menuOpenedByHold = true
                            when (val focused = grid.items.getOrNull(grid.focusIndex)) {
                                is HomeItem.AppItem -> overlays.menuApp = focused.app
                                is HomeItem.FolderItem ->
                                    if (!isHiddenFolder(focused)) {
                                        overlays.menuFolderId = focused.id
                                    }

                                null -> Unit
                            }
                        }
                    } else {
                        // Kids mode has no edit mode and no menus,
                        // but the hold is only consumed after the
                        // same delay the menu timer would take: a
                        // short press must still launch the tile.
                        // НЕ ТРОГАТЬ вслепую: установка
                        // grid.longPressHandled = true здесь, до KeyUp,
                        // делала мёртвым любое OK в детском режиме.
                        grid.longPressHandled = false
                        grid.holdJob?.cancel()
                        grid.holdJob = scope.launch {
                            delay(MENU_HOLD_MS)
                            grid.longPressHandled = true
                        }
                    }
                    true
                }

                // 09_SEARCH §3: search is its own surface, reached from a
                // dedicated entry rather than from the grid's context menu.
                // Android TV remotes send ASSIST where tvOS sends Search,
                // and some keyboards only have the magnifier key.
                Key.Search, Key.Assist -> {
                    if (!grid.editing && grid.openFolder == null && !overlays.menuOpen &&
                        grid.launching == null
                    ) {
                        grid.holdJob?.cancel()
                        grid.holdJob = null
                        overlays.okDown = false
                        grid.longPressHandled = false
                        grid.confirmIndex = null
                        overlays.searchOpen = true
                    }
                    true
                }

                // tvOS opens its context menu on a long OK (T12), so
                // the hold does it here too; MENU stays as the
                // alternative for remotes and keyboards that send it.
                Key.Menu -> {
                    // A running hold timer would fight the menu, and
                    // a kids profile gets no menus at all: its tiles
                    // must not offer uninstall or hiding.
                    if (!grid.editing && !kidsMode) {
                        grid.holdJob?.cancel()
                        grid.holdJob = null
                        overlays.okDown = false
                        grid.longPressHandled = true
                        grid.confirmIndex = null
                        overlays.menuOpenedByHold = false
                        when (val focused = grid.items.getOrNull(grid.focusIndex)) {
                            is HomeItem.AppItem -> overlays.menuApp = focused.app
                            // The hidden folder is not a user folder: no
                            // rename, no delete, so it has no menu at all.
                            is HomeItem.FolderItem -> if (!isHiddenFolder(focused)) {
                                overlays.menuFolderId = focused.id
                            }

                            null -> Unit
                        }
                    }
                    true
                }

                // tvOS returns from the app grid to the dock on BACK.
                // A remote delivers BACK to the focus node before the
                // Activity dispatcher (see FolderOverlay), so the grid
                // claims it here; panels own their own BACK.
                // At the root there is nothing above Home: BACK is a
                // deliberate no-op (the launcher is the HOME surface),
                // matching MainActivity's OnBackPressedCallback.
                Key.Back -> {
                    when {
                        grid.openFolder != null || overlays.menuOpen -> Unit
                        grid.editing -> {
                            if (grid.pickupActive) {
                                val start = grid.pickupStart
                                grid.pickupSnapshot?.let { grid.items = it }
                                if (start in grid.items.indices) grid.focusIndex = start
                                grid.endPickup()
                            } else {
                                grid.editing = false
                            }
                        }

                        libraryMode -> grid.focusIndex = grid.focusIndex % columns
                    }
                    true
                }

                else -> false
            }
        }

        KeyEventType.KeyUp -> when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                grid.holdJob?.cancel()
                grid.holdJob = null
                if (!grid.editing && shelf.focus >= 0 && overlays.okDown && !grid.longPressHandled) {
                    shelfCards.getOrNull(shelf.focus)?.let { activateShelfCard(it) }
                } else if (grid.editing && grid.pickupActive) {
                    if (overlays.okDown && !grid.longPressHandled) grid.dropPickup(onOrderChanged)
                } else if (!grid.editing && overlays.okDown && !grid.longPressHandled && shelf.focus < 0) {
                    when (val item = grid.items.getOrNull(grid.focusIndex)) {
                        is HomeItem.AppItem -> startLaunch(item.app, cellCenter(grid.focusIndex))
                        is HomeItem.FolderItem -> grid.openFolderAt(grid.focusIndex, cellCenter(grid.focusIndex))
                        null -> Unit
                    }
                }
                overlays.okDown = false
                grid.longPressHandled = false
                true
            }

            else -> false
        }

        else -> false
    }
}
