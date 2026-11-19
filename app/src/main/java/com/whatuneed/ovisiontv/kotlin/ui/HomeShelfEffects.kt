package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.homeApps
import com.whatuneed.ovisiontv.kotlin.data.isHiddenFolder
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryEngine
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySection
import com.whatuneed.ovisiontv.kotlin.discovery.defaultDiscoverySection
import kotlinx.coroutines.delay

/**
 * Discovery refresh, shown-card bookkeeping, modal reset and the hero parade.
 * None of these effects draws anything; they only keep HomeShelfState in sync
 * with the provider snapshot, the grid and the visible overlays.
 */
@Composable
internal fun HomeShelfEffects(
    shelf: HomeShelfState,
    grid: HomeGridState,
    overlays: HomeOverlayState,
    discovery: DiscoveryEngine,
    shelfApp: TvApp?,
    resumeTick: Int,
    kidsMode: Boolean,
    libraryMode: Boolean,
) {
    val currentSection = shelf.currentSection
    val shelfCards = shelf.shelfCards

    // Refresh on resume, package change and after a hide/favorite. The dock
    // app's own rail is selected when the cursor moved to another app, but a
    // refresh never throws the viewer back to the first card (PHASE 33).
    LaunchedEffect(
        shelfApp?.packageName,
        resumeTick,
        grid.items,
        kidsMode,
        shelf.revision,
    ) {
        val hiddenPackages = grid.items
            .filterIsInstance<HomeItem.FolderItem>()
            .filter(::isHiddenFolder)
            .flatMap { it.apps }
            .mapTo(HashSet()) { it.packageName }
        val apps = homeApps(grid.items).filterNot { it.packageName in hiddenPackages }
        // A refresh must keep the card, not the index: a provider row can move
        // when another app publishes something newer.
        val selectedId = shelfCards.getOrNull(shelf.selected)?.id
        val snapshot = discovery.refresh(
            currentPackage = shelfApp?.packageName,
            apps = apps,
            visiblePackages = apps.mapTo(HashSet()) { it.packageName },
            kidsMode = kidsMode,
        )
        shelf.snapshot = snapshot
        val previous = shelf.lastShelfPackage
        shelf.lastShelfPackage = shelfApp?.packageName
        val appRail = snapshot.rails.firstOrNull { it.section == DiscoverySection.FROM_APP }
        val wanted = when {
            appRail != null && (previous != shelfApp?.packageName || shelf.sectionName.value == null) ->
                DiscoverySection.FROM_APP
            else -> snapshot.rails.firstOrNull { it.section.name == shelf.sectionName.value }?.section
                ?: defaultDiscoverySection(snapshot.rails)
        }
        if (wanted == null) {
            shelf.sectionName.value = null
            shelf.selected = 0
            shelf.focus = -1
            shelf.sectionFocused = false
            return@LaunchedEffect
        }
        shelf.sectionName.value = wanted.name
        val rail = snapshot.rails.firstOrNull { it.section == wanted }
        val last = rail?.items?.lastIndex?.coerceAtLeast(0) ?: 0
        if (previous != shelfApp?.packageName) {
            shelf.selected = 0
            if (shelf.focus >= 0) shelf.focus = 0
            shelf.sectionCardMemory.clear()
        } else {
            val restored = rail?.items?.indexOfFirst { it.id == selectedId } ?: -1
            shelf.selected = if (restored >= 0) {
                restored
            } else {
                shelf.selected.coerceIn(0, last)
            }
            if (shelf.focus >= 0) shelf.focus = shelf.selected
        }
    }

    // Remember only the hero the viewer actually saw. Marking a whole rail
    // would demote cards that never entered the viewport.
    LaunchedEffect(currentSection, shelfCards, shelf.selected, shelf.revision) {
        val section = currentSection ?: return@LaunchedEffect
        delay(SHOWN_SETTLE_MS)
        shelfCards.getOrNull(shelf.selected)?.let { discovery.markShown(section, it) }
    }

    LaunchedEffect(
        grid.editing,
        grid.openFolder,
        grid.launching,
        overlays.menuFolderId,
        overlays.folderDeleteId,
        overlays.menuApp,
        overlays.moveToFolderApp,
        overlays.newFolderApp,
        overlays.renameApp,
        overlays.searchOpen,
        overlays.controlCenterOpen,
        overlays.weatherPanelOpen,
        shelf.playingTrailer,
        shelf.onlineInfo,
    ) {
        if (grid.editing || grid.openFolder != null || grid.launching != null ||
            overlays.menuFolderId != null || overlays.folderDeleteId != null || overlays.menuApp != null ||
            overlays.moveToFolderApp != null || overlays.newFolderApp != null || overlays.renameApp != null || overlays.searchOpen ||
            overlays.controlCenterOpen || overlays.weatherPanelOpen || shelf.playingTrailer != null ||
            shelf.onlineInfo != null
        ) {
            shelf.focus = -1
            shelf.sectionFocused = false
        }
    }

    // The Java hero advanced every 8 s and restarted that clock on any click;
    // stepping the carousel changes shelf.selected, which restarts this timer.
    // The pill and the panel stop it: a page turn under the focused status row
    // would change what Down returns to.
    LaunchedEffect(
        shelfApp?.packageName,
        currentSection,
        shelfCards,
        shelf.selected,
        shelf.focus,
        shelf.sectionFocused,
        shelf.menu,
        libraryMode,
        grid.editing,
        grid.openFolder,
        grid.launching,
        overlays.menuFolderId,
        overlays.menuApp,
        overlays.moveToFolderApp,
        overlays.newFolderApp,
        overlays.renameApp,
        overlays.searchOpen,
        overlays.controlCenterOpen,
        overlays.capsuleFocused,
        overlays.weatherFocused,
        overlays.weatherPanelOpen,
        shelf.playingTrailer,
        shelf.onlineInfo,
    ) {
        if (shelfCards.size <= 1 || shelf.sectionFocused || shelf.focus >= 0 ||
            shelf.menu != null || libraryMode || grid.editing || grid.openFolder != null ||
            grid.launching != null || overlays.menuFolderId != null || overlays.folderDeleteId != null ||
            overlays.menuApp != null || overlays.moveToFolderApp != null || overlays.newFolderApp != null ||
            overlays.renameApp != null || overlays.searchOpen || overlays.controlCenterOpen || overlays.capsuleFocused || overlays.weatherFocused ||
            overlays.weatherPanelOpen || shelf.playingTrailer != null || shelf.onlineInfo != null
        ) {
            return@LaunchedEffect
        }
        delay(SHELF_ADVANCE_MS)
        shelf.selected = (shelf.selected + 1) % shelfCards.size
        if (shelf.focus >= 0) shelf.focus = shelf.selected
    }

}
