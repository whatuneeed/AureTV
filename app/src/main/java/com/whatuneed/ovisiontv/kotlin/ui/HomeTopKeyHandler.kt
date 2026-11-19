package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryRail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keys for the surfaces above the grid: weather pill, status pill, section bar
 * and shelf cards. Returns false when none of them owns the remote, so the
 * caller can continue with the grid.
 */
internal fun handleHomeTopKey(
    event: KeyEvent,
    shelf: HomeShelfState,
    grid: HomeGridState,
    overlays: HomeOverlayState,
    rails: List<DiscoveryRail>,
    shelfCards: List<DiscoveryItem>,
    weatherVisible: Boolean,
    scope: CoroutineScope,
    focusCapsule: (Boolean) -> Unit,
): Boolean {
    // The weather pill mirrors the Java card: Ok opens the
    // forecast, Right crosses to the status pill, Down/Back
    // drop into the carousel or the grid, Up/Left stay put.
    if (overlays.weatherFocused) {
        val handled = when (event.key) {
            Key.DirectionRight -> {
                val fromShelf = overlays.weatherFromShelf
                overlays.weatherFocused = false
                overlays.weatherFromShelf = false
                overlays.capsuleFromShelf = fromShelf
                overlays.capsuleFocused = true
                true
            }

            Key.DirectionDown, Key.Back -> {
                val fromShelf = overlays.weatherFromShelf
                overlays.weatherFocused = false
                overlays.weatherFromShelf = false
                if (fromShelf && shelfCards.isNotEmpty() && !grid.editing) {
                    shelf.focus = shelf.selected.coerceIn(0, shelfCards.lastIndex)
                }
                true
            }

            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                overlays.okDown = false
                overlays.weatherPanelOpen = true
                true
            }

            // Up/Left have nowhere to go from the left pill.
            // Menu/Search are not grid actions here either;
            // system media and volume keys still fall through.
            Key.DirectionUp, Key.DirectionLeft,
            Key.Menu, Key.Search, Key.Assist,
            -> true

            else -> false
        }
        if (handled) return true
    }
    // The pill is the top of the screen: Ok opens the panel,
    // Down/Back drop back in, Left reaches the weather card
    // when one is shown, Right/Up have nowhere to go.
    if (overlays.capsuleFocused) {
        val handled = when (event.key) {
            Key.DirectionDown, Key.Back -> {
                val fromShelf = overlays.capsuleFromShelf
                overlays.capsuleFocused = false
                overlays.capsuleFromShelf = false
                if (fromShelf && shelfCards.isNotEmpty() && !grid.editing) {
                    shelf.focus = shelf.selected.coerceIn(0, shelfCards.lastIndex)
                }
                true
            }

            Key.DirectionLeft -> {
                if (weatherVisible) {
                    val fromShelf = overlays.capsuleFromShelf
                    overlays.capsuleFocused = false
                    overlays.capsuleFromShelf = false
                    overlays.weatherFromShelf = fromShelf
                    overlays.weatherFocused = true
                    true
                } else {
                    false
                }
            }

            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                overlays.okDown = false
                overlays.controlCenterOpen = true
                true
            }

            // The pill is the top-right corner: Right/Up stay
            // put, and Menu/Search are not dock actions.
            Key.DirectionRight, Key.DirectionUp,
            Key.Menu, Key.Search, Key.Assist,
            -> true

            else -> false
        }
        if (handled) return true
    }
    // The section bar sits above the rail: Left/Right
    // switch the rail, Down/Back return to the cards.
    if (shelf.sectionFocused && rails.size > 1 && !grid.editing) {
        val index = rails.indexOfFirst { it.section == shelf.currentSection }
        val handled = when (event.key) {
            Key.DirectionLeft -> {
                if (rails.isNotEmpty()) {
                    val next = (index - 1 + rails.size) % rails.size
                    shelf.selectSection(rails[next].section)
                }
                true
            }

            Key.DirectionRight -> {
                if (rails.isNotEmpty()) {
                    val next = (index + 1) % rails.size
                    shelf.selectSection(rails[next].section)
                }
                true
            }

            Key.DirectionDown, Key.Back,
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
            -> {
                shelf.sectionFocused = false
                shelf.focus = shelf.selected.coerceIn(
                    0,
                    shelfCards.lastIndex.coerceAtLeast(0),
                )
                true
            }

            Key.DirectionUp -> {
                shelf.sectionFocused = false
                focusCapsule(true)
                true
            }

            // Do not leak these into the dock branch below:
            // the bar is not the app grid. SEARCH stays a
            // grid/capsule action for now; MENU has no
            // section-specific grid.items.
            Key.Menu, Key.Search, Key.Assist -> true

            else -> false
        }
        if (handled) return true
    }
    // The rail owns the remote while a card is focused so
    // the grid cursor underneath cannot move.
    if (shelf.focus >= 0 && shelfCards.isNotEmpty() && !grid.editing) {
        val held = shelfCards.getOrNull(shelf.focus)
        val handled = when (event.key) {
            Key.DirectionLeft -> {
                if (shelf.focus > 0) {
                    shelf.focus--
                    shelf.selected = shelf.focus
                }
                true
            }

            Key.DirectionRight -> {
                if (shelf.focus < shelfCards.lastIndex) {
                    shelf.focus++
                    shelf.selected = shelf.focus
                }
                true
            }

            Key.DirectionDown, Key.Back -> {
                shelf.focus = -1
                shelf.sectionFocused = false
                true
            }

            Key.DirectionUp -> {
                // Up reaches the section bar when there is
                // one; otherwise it is the status pill.
                shelf.focus = -1
                if (rails.size > 1) {
                    shelf.sectionFocused = true
                } else {
                    focusCapsule(true)
                }
                true
            }

            // A long OK opens the card menu, as on a grid
            // tile. A short OK is handled on KeyUp so the
            // hold cannot activate the card as well.
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                overlays.okDown = true
                grid.longPressHandled = false
                grid.holdJob?.cancel()
                grid.holdJob = scope.launch {
                    delay(MENU_HOLD_MS)
                    grid.longPressHandled = true
                    overlays.okDown = false
                    overlays.menuOpenedByHold = true
                    shelf.menu = held
                }
                true
            }

            Key.Menu -> {
                grid.holdJob?.cancel()
                grid.holdJob = null
                overlays.okDown = false
                grid.longPressHandled = true
                overlays.menuOpenedByHold = false
                shelf.menu = held
                true
            }

            else -> false
        }
        if (handled) return true
    }
    return false
}
