package com.whatuneed.ovisiontv.kotlin.ui

import com.whatuneed.ovisiontv.kotlin.core.input.rememberTvKeyRepeat
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SettingsActivity
import com.whatuneed.ovisiontv.kotlin.data.AppLabelStore
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.KidsMode
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SafePreferences
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.data.homeApps
import com.whatuneed.ovisiontv.kotlin.data.indexOfHomeFocus
import com.whatuneed.ovisiontv.kotlin.data.isHiddenFolder
import com.whatuneed.ovisiontv.kotlin.data.isSettingsTile
import com.whatuneed.ovisiontv.kotlin.ui.components.shelfCardAnchor
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberClockText
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberNetworkKind
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryEngine
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import com.whatuneed.ovisiontv.kotlin.discovery.providerTargetForOnline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    initialItems: List<HomeItem>,
    resumeTick: Int,
    onLaunch: (TvApp) -> Boolean,
    onOrderChanged: (List<HomeItem>) -> Unit,
    onAppInfo: (TvApp) -> Unit = {},
    onUninstall: (TvApp) -> Unit = {},
    focusTick: Int = 0,
    onUserActivity: () -> Unit = {},
    onStartSaver: () -> Unit = {},
    reduceMotion: Boolean = false,
    // The saver draws over Home; stop the muted hero there so only the
    // saver's own video decodes.
    saverVisible: Boolean = false,
    // The weather loop belongs to the visible Home: a paused launcher must not
    // keep refreshing an invisible capsule (AQ-21).
    foreground: Boolean = true,
    prefsName: String = LauncherPrefs.NAME,
    // The profile owns discovery signals; the name above already scopes them,
    // and this id is stored in the snapshot so two profiles stay apart.
    profileId: String = "p1",
    // A kids profile must not rearrange the parent's saved order: edit mode
    // and the per-tile menus stay closed while it is on.
    kidsMode: Boolean = false,
    // The launcher can route Settings through the kids PIN gate; null keeps
    // the direct start every test relies on.
    onOpenSettings: (() -> Unit)? = null,
    // System Settings from the Control Center follow the same rule: the
    // launcher gates them, a plain test host opens them directly.
    onOpenSystem: ((Intent) -> Unit)? = null,
) {
    val scale = rememberTvScale()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // tvOS keeps the active profile in the top-right capsule. The avatar is
    // real state from the profile store, not a decorative placeholder.
    val activeProfile = remember(context, profileId) {
        runCatching {
            ProfileStore(context).list().firstOrNull { it.id == profileId }
        }.getOrNull()
    }
    val profileColor = activeProfile?.let { ProfileCatalog.color(it.color).background }
    val prefs = remember(context, prefsName) {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }
    val columns = TvOs.COLUMNS

    // A removed focused app leaves no package to restore; the cursor then stays
    // where it was instead of jumping to the first tile.
    var lastFocusIndex by remember { mutableIntStateOf(-1) }
    // The persisted key lags by the save debounce, so the grid also keeps the
    // live key: a list replaced right after a press must not restore an older app.
    var lastFocusKey by remember { mutableStateOf<String?>(null) }
    val focusState = rememberSaveable(initialItems) {
        val saved = lastFocusKey ?: SafePreferences.string(prefs, LauncherPrefs.KEY_FOCUS_PACKAGE, null)
        val restored = indexOfHomeFocus(initialItems, saved)
        mutableIntStateOf(
            if (restored >= 0) {
                restored
            } else {
                lastFocusIndex.coerceIn(0, initialItems.lastIndex.coerceAtLeast(0))
            },
        )
    }
    val grid = remember(initialItems) { HomeGridState(initialItems, focusState) }
    var shelfIndex by rememberSaveable(initialItems) {
        val saved = SafePreferences.string(prefs, LauncherPrefs.KEY_SHELF_PACKAGE, null)
        val dockSize = minOf(columns, initialItems.size)
        val restored = initialItems.indexOfFirst {
            it is HomeItem.AppItem && it.app.packageName == saved
        }.takeIf { it in 0 until dockSize }
        val fallback = (0 until dockSize).firstOrNull {
            initialItems[it] is HomeItem.AppItem
        } ?: 0
        // A focused folder is not an app, so tvOS leaves the shelf empty (06_TOP_SHELF §1).
        mutableIntStateOf(if (grid.focusIndex < dockSize) grid.focusIndex else restored ?: fallback)
    }

    // The shelf belongs to the app under the dock cursor; its cards come only
    // from rows that app published to the TV provider.
    val shelfApp = (grid.items.getOrNull(shelfIndex) as? HomeItem.AppItem)?.app
    // Discovery is profile-scoped: a new profile creates a new engine, so two
    // profiles never share usage, favorites or hidden cards.
    val discovery = remember(context, prefsName, profileId) {
        DiscoveryEngine(context.applicationContext, prefsName, profileId)
    }
    // Read again on every resume: Settings writes this in another activity.
    val previewAutoplay = remember(prefsName, resumeTick) {
        discovery.previewAutoplay()
    }
    val discoverySection = rememberSaveable { mutableStateOf<String?>(null) }
    val shelf = remember { HomeShelfState(discoverySection) }
    val rails = shelf.rails
    val currentSection = shelf.currentSection
    val shelfCards = shelf.shelfCards
    // A folder remembers the app it was left on; reopening it must not jump
    // back to the first cell (UX-09). A package key survives an earlier app
    // being uninstalled, which an index does not, and the map survives a
    // process restore.
    val folderFocus = rememberSaveable(
        saver = mapSaver(
            save = { it.toMap() },
            restore = { saved ->
                mutableStateMapOf<String, String>().apply {
                    saved.forEach { (key, value) -> if (value is String) put(key, value) }
                }
            },
        ),
    ) { mutableStateMapOf<String, String>() }
    val capsuleRequester = remember { FocusRequester() }
    val capsuleClock = rememberClockText()
    val capsuleNetwork = rememberNetworkKind()
    val weather = remember { HomeWeatherState(WeatherPreferences.enabled(context)) }
    val weatherRequester = remember { FocusRequester() }
    val keyRepeat = rememberTvKeyRepeat()
    val launchProgress = remember { Animatable(0f) }
    val overlays = remember { HomeOverlayState() }

    // 02_HOME_SCREEN §3: the dock is the first row of the same grid. Leaving it
    // for the apps below turns the grid into the full-screen library: the shelf
    // and band fade, and the grid rises to the top instead of scrolling under a
    // static header. Coming back up restores the dock.
    val libraryMode = grid.items.size > columns && grid.focusIndex >= columns
    val rows = ((grid.items.size + columns - 1) / columns).coerceAtLeast(1)
    val bottomReserve = if (grid.items.size % columns == 0) 88f + 150f else 0f
    val lastRowCenter = TvOs.ROW_TOP + (rows - 1) * TvOs.ROW_PITCH + TvOs.ICON_HEIGHT / 2f
    val maxOffsetHome = (lastRowCenter + 270f + bottomReserve - TvOs.SCREEN_HEIGHT)
        .coerceAtLeast(0f)
    // In the library the first (dock) row sits at LIBRARY_TOP, so the scroll
    // range shrinks by the same shift; otherwise the last row would stop high.
    val maxOffsetLibrary = (lastRowCenter + 270f + bottomReserve - TvOs.SCREEN_HEIGHT -
        LIBRARY_SHIFT).coerceAtLeast(0f)
    val maxOffset = if (libraryMode) maxOffsetLibrary else maxOffsetHome
    fun scrollTargetFor(index: Int): Float {
        val row = index / columns
        val base = if (libraryMode) (row - 1).coerceAtLeast(0) else row
        return (base * TvOs.ROW_PITCH).coerceAtMost(maxOffset)
    }

    val scroll = remember(initialItems) {
        Animatable(scrollTargetFor(grid.focusIndex))
    }
    // 02_HOME_SCREEN §4: opening a folder hides the grid by shifting it +340 pt.
    val hideShift = remember { Animatable(0f) }
    // Animated separately from scroll: the library shift moves the whole grid
    // under the fading shelf, while scroll still maps the focused row.
    val libraryShift = remember { Animatable(if (libraryMode) LIBRARY_SHIFT else 0f) }
    val libraryFade = remember { Animatable(if (libraryMode) 0f else 1f) }
    // Full-screen layers exist only while they are on screen: the shelf used to
    // stay composed under the library and kept drawing a 1280 px poster.
    val shelfVisible by remember { derivedStateOf { libraryFade.value > 0.01f } }
    val libraryVisible by remember { derivedStateOf { libraryFade.value < 0.999f } }
    // 02_HOME_SCREEN §4 / 14_MOTION_DESIGN M4: tvOS hides the grid by riding it
    // down 340 pt, not by fading. The dock row and its band leave with the
    // tiles, so choosing a recommendation makes the poster the whole screen.
    // The pill keeps the carousel engaged while it is focused from above, so
    // opening the Control Center does not slide the whole grid up behind it.
    val shelfHeld = (
        shelf.focus >= 0 ||
            shelf.sectionFocused ||
            (overlays.capsuleFocused && overlays.capsuleFromShelf) ||
            (overlays.weatherFocused && overlays.weatherFromShelf)
        ) && !libraryMode
    val shelfHide by animateFloatAsState(
        targetValue = if (shelfHeld) TvOs.HIDE_GRID_SHIFT else 0f,
        animationSpec = tween(
            motionDuration(TvOs.SCROLL_DURATION_MS, reduceMotion),
            easing = FastOutSlowInEasing,
        ),
        label = "shelfHide",
    )

    HomeLayoutEffects(
        grid = grid,
        libraryMode = libraryMode,
        reduceMotion = reduceMotion,
        libraryShift = libraryShift,
        libraryFade = libraryFade,
    )

    HomeWeatherEffects(
        weather = weather,
        context = context,
        resumeTick = resumeTick,
        foreground = foreground,
        weatherPanelOpen = overlays.weatherPanelOpen,
    )

    HomeShelfEffects(
        shelf = shelf,
        grid = grid,
        overlays = overlays,
        discovery = discovery,
        shelfApp = shelfApp,
        resumeTick = resumeTick,
        kidsMode = kidsMode,
        libraryMode = libraryMode,
    )

    HomeFocusEffects(
        grid = grid,
        scroll = scroll,
        columns = columns,
        shelfIndex = shelfIndex,
        onShelfIndex = { shelfIndex = it },
        onLastFocusIndex = { lastFocusIndex = it },
        onLastFocusKey = { lastFocusKey = it },
        scrollTarget = ::scrollTargetFor,
        prefs = prefs,
    )

    HomeLaunchEffects(
        grid = grid,
        launchProgress = launchProgress,
        hideShift = hideShift,
        reduceMotion = reduceMotion,
        resumeTick = resumeTick,
        onLaunch = onLaunch,
    )

    fun moveFocus(dx: Int, dy: Int) = grid.moveFocus(dx, dy)

    // Up leaves the grid for the status pill. A running edit timer belongs to
    // the cell the cursor came from, so it is dropped with the move; the held
    // OK must not launch that cell when it is released over the pill either.
    fun focusCapsule(fromShelf: Boolean) {
        grid.holdJob?.cancel()
        grid.holdJob = null
        grid.longPressHandled = true
        overlays.okDown = false
        grid.confirmIndex = null
        overlays.capsuleFromShelf = fromShelf
        overlays.capsuleFocused = true
    }

    fun endPickup() = grid.endPickup()
    fun dropPickup() = grid.dropPickup(onOrderChanged)
    fun mergePickupInto(victimIndex: Int) =
        grid.mergePickupInto(victimIndex, context, onOrderChanged)
    fun togglePickup() = grid.togglePickup(onOrderChanged)

    fun cellCenter(index: Int): Offset {
        val column = index % columns
        val row = index / columns
        val dockShift = if (index < columns) TvOs.DOCK_FOCUS_SHIFT else 0f
        return Offset(
            TvOs.SAFE_AREA + column * TvOs.ICON_COLUMN_PITCH + TvOs.ICON_WIDTH / 2f,
            TvOs.ROW_TOP + row * TvOs.ROW_PITCH + TvOs.ICON_HEIGHT / 2f -
                scroll.value - dockShift - libraryShift.value,
        )
    }

    fun openFolderAt(index: Int) = grid.openFolderAt(index, cellCenter(index))

    fun startLaunch(app: TvApp, center: Offset) {
        if (grid.launching != null) return
        if (!app.available) {
            Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        grid.holdJob?.cancel()
        grid.holdJob = null
        grid.longPressHandled = false
        grid.editing = false
        grid.pickupActive = false
        grid.pickupOrigin = null
        grid.pickupSnapshot = null
        grid.pickupStart = -1
        grid.mergeVictim = null
        grid.confirmIndex = null
        // Kids mode: the launcher's own Settings tile opens through the PIN
        // gate the way the Control Center does. The launch zoom would only
        // have to reverse when the gate replaces the launch.
        if (kidsMode && app.isSettingsTile(context.packageName) && onOpenSettings != null) {
            onOpenSettings?.invoke()
            return
        }
        grid.launching = Launching(app, center)
    }

    // A provider deep link is opened only when it resolves back to the app that
    // published it; otherwise the app itself opens, never a foreign package.
    fun closeShelfMenu() {
        shelf.menu = null
        overlays.menuOpenedByHold = false
        shelf.focus = shelf.selected
        shelf.focusTick++
    }

    fun openShelfContent(card: DiscoveryItem) {
        // The rail is refreshed after a profile/allowlist change, but a press
        // in that gap must not open an app the kids profile no longer allows.
        if (kidsMode && !KidsMode.allowed(context, card.packageName)) {
            Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = discovery.contentIntent(card)
        if (intent != null) {
            val opened = runCatching { context.startActivity(intent) }
                .onFailure { Log.w(HOME_TAG, "deep link failed for ${card.packageName}", it) }
                .isSuccess
            if (opened) {
                discovery.recordLaunch(card.packageName)
            } else {
                Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }
        // A row whose deep link stopped resolving still opens its own app; it
        // must never open a foreign package or show invented content.
        val app = homeApps(grid.items).firstOrNull { it.packageName == card.packageName }
        if (app != null) {
            startLaunch(app, cellCenter(grid.focusIndex))
        } else {
            Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun activateShelfCard(card: DiscoveryItem) {
        // An online card has no installed app to open. It is an information
        // card: its preview may decorate the hero, but a dead-end trailer is
        // deliberately not played here. Trailers belong to provider cards,
        // where OK can finally open the film in the publishing app.
        if (card.source == DiscoverySource.ONLINE) {
            // If the same film exists in an installed app's Watch Next, the
            // online card can do more than describe it: trailer → OK → open
            // that app. Without a target it stays an honest information card.
            val target = providerTargetForOnline(card, shelf.snapshot.rails)
            val trailerUrl = card.trailerUri
            if (target != null && !trailerUrl.isNullOrBlank()) {
                shelf.playingTrailer = PlayingTrailer(
                    title = card.title,
                    url = trailerUrl,
                    target = target,
                    appLabel = target.packageLabel ?: target.packageName,
                    synopsis = card.synopsis,
                )
            } else {
                shelf.onlineInfo = card
                shelf.onlineInfoTarget = target
            }
            return
        }
        if (kidsMode && !KidsMode.allowed(context, card.packageName)) {
            Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val trailerUrl = card.trailerUri
        if (!trailerUrl.isNullOrBlank()) {
            shelf.playingTrailer = PlayingTrailer(
                title = card.title,
                url = trailerUrl,
                target = card,
                appLabel = card.packageLabel ?: card.packageName,
                synopsis = card.synopsis,
            )
            return
        }
        openShelfContent(card)
    }

    // TalkBack activates through semantics, not key events; run the same path a
    // short OK press would take on that cell.
    fun activate(index: Int) =
        grid.activate(index, ::cellCenter, ::startLaunch, onOrderChanged)
    fun updateFolderApps(folderId: String, apps: List<TvApp>) =
        grid.updateFolderApps(folderId, apps, onOrderChanged)
    fun renameFolder(folderId: String, name: String) =
        grid.renameFolder(folderId, name, onOrderChanged)
    fun removeFolderById(folderId: String) =
        grid.removeFolderById(folderId, onOrderChanged)
    fun hideAppFromHome(app: TvApp) =
        grid.hideAppFromHome(app, context, onOrderChanged)

    fun renameAppInHome(app: TvApp, label: String) {
        AppLabelStore.setLabel(context, app.packageName, label, prefsName)
        grid.applyAppLabel(app.packageName, label)
        overlays.renameApp = null
    }

    fun resetAppLabel(app: TvApp) {
        AppLabelStore.clearLabel(context, app.packageName, prefsName)
        grid.applyAppLabel(app.packageName, AppLabelStore.defaultLabel(context, app))
        overlays.renameApp = null
    }

    fun moveAppToHome(folderId: String, app: TvApp) =
        grid.moveAppToHome(folderId, app, onOrderChanged)
    fun moveAppIntoFolder(app: TvApp, folderId: String) =
        grid.moveAppIntoFolder(app, folderId, onOrderChanged)
    fun createFolderWithApp(name: String, app: TvApp) =
        grid.createFolderWithApp(name, app, context, onOrderChanged)

    // A package change rebuilds `grid.items`: an overlay that kept the old folder would
    // show an app that no longer exists, and a drag would point past the list end.
    LaunchedEffect(initialItems) {
        grid.holdJob?.cancel()
        grid.holdJob = null
        grid.editing = false
        grid.pickupActive = false
        grid.pickupOrigin = null
        grid.pickupSnapshot = null
        grid.pickupStart = -1
        grid.mergeVictim = null
        grid.confirmIndex = null
        grid.longPressHandled = false
        grid.openFolder = grid.openFolder?.let { open ->
            val fresh = grid.items.firstOrNull { it is HomeItem.FolderItem && it.id == open.folder.id }
            fresh?.let { open.copy(folder = it as HomeItem.FolderItem) }
        }
        // A menu whose tile vanished has no node left to dismiss it. Kept open,
        // it would gate the grid for TalkBack and swallow SEARCH with nothing
        // on screen to close.
        overlays.menuApp = overlays.menuApp?.takeIf { app ->
            grid.items.any { it is HomeItem.AppItem && it.app.packageName == app.packageName }
        }
        overlays.menuFolderId = overlays.menuFolderId?.takeIf { id ->
            grid.items.any { it is HomeItem.FolderItem && it.id == id }
        }
        overlays.folderDeleteId = overlays.folderDeleteId?.takeIf { id ->
            grid.items.any { it is HomeItem.FolderItem && it.id == id }
        }
    }

    // 14_MOTION_DESIGN M8: the tile the drag pushed aside stays the folder
    // candidate while the drag rests on the new cell. Folding is an explicit
    // hold of OK (see the key handler); a drag alone never makes a folder.
    // The focus node sees BACK before the Activity dispatcher (see
    // FolderOverlay), so the grid is handled here as well.
    BackHandler(enabled = grid.editing) {
        if (grid.pickupActive) {
            val start = grid.pickupStart
            grid.pickupSnapshot?.let { grid.items = it }
            if (start in grid.items.indices) grid.focusIndex = start
            endPickup()
        } else {
            grid.editing = false
        }
    }

    // tvOS returns from the app grid to the dock on BACK; doing it through the
    // column keeps the same app under the cursor on the way up.
    BackHandler(
        enabled = libraryMode && !grid.editing && grid.openFolder == null && grid.launching == null &&
            overlays.menuFolderId == null && overlays.folderDeleteId == null && overlays.menuApp == null &&
            overlays.moveToFolderApp == null && overlays.newFolderApp == null && overlays.renameApp == null &&
            !overlays.searchOpen && !overlays.controlCenterOpen,
    ) {
        grid.focusIndex = grid.focusIndex % columns
    }

    val focusRequester = remember { FocusRequester() }
    // One real focus target per cell. TalkBack on a TV speaks the input focus,
    // and the custom engine alone never moves it; the engine still decides
    // where focus goes, this only mirrors that choice into Android's focus.
    val tileRequesters = remember(grid.items) { mutableMapOf<String, FocusRequester>() }
    // The picked-up cell is drawn as the drag proxy, not as its own card, so it
    // needs its own target; focusing the grid root would make TalkBack read the
    // first tile instead of the one being moved.
    val pickupRequester = remember { FocusRequester() }
    val menuOpen = overlays.menuFolderId != null || overlays.folderDeleteId != null ||
        overlays.menuApp != null || overlays.moveToFolderApp != null
    // The Java launcher hid the pill over a folder, the library and every panel.
    val weatherVisible = weather.enabled && weather.data != null && !libraryMode &&
        grid.openFolder == null && grid.launching == null && !menuOpen && overlays.renameApp == null &&
        !overlays.searchOpen && !overlays.controlCenterOpen && !overlays.weatherPanelOpen && shelf.focus < 0 &&
        !shelf.sectionFocused
    val gridModal = grid.openFolder != null || grid.launching != null || menuOpen ||
        overlays.newFolderApp != null || overlays.renameApp != null || overlays.searchOpen || overlays.controlCenterOpen ||
        overlays.weatherPanelOpen || shelf.playingTrailer != null || shelf.onlineInfo != null
    // Layout gate instead of a blind delay: once the grid is placed every tile's
    // FocusRequester is attached, so the cursor can move on the first frame.
    var gridPlaced by remember { mutableStateOf(false) }
    HomeFocusRequester(
        grid = grid,
        shelf = shelf,
        overlays = overlays,
        focusTick = focusTick,
        gridModal = gridModal,
        gridPlaced = gridPlaced,
        capsuleRequester = capsuleRequester,
        weatherRequester = weatherRequester,
        pickupRequester = pickupRequester,
        focusRequester = focusRequester,
        tileRequesters = tileRequesters,
        context = context,
    )

    // Weather turned off in Settings, or the pill left the screen: the cursor
    // must not sit on a node that no longer exists. While the forecast panel is
    // open the pill is hidden but keeps the return target for Back.
    LaunchedEffect(weatherVisible, overlays.weatherPanelOpen, weather.enabled) {
        if (!weatherVisible && overlays.weatherFocused && !overlays.weatherPanelOpen) {
            overlays.weatherFocused = false
            overlays.weatherFromShelf = false
        }
        if (!weather.enabled) {
            overlays.weatherPanelOpen = false
            overlays.weatherFromShelf = false
        }
    }

    val searchableApps = remember(grid.items) { homeApps(grid.items) }

    // LauncherRoot already paints this exact gradient behind the whole home
    // layer, so the grid does not draw a second full-screen copy.
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            // Preview runs before menus and panels consume the key, so any
            // press anywhere on this screen restarts the saver's idle clock.
            .homeKeyInput(
                grid = grid,
                shelf = shelf,
                overlays = overlays,
                rails = rails,
                shelfCards = shelfCards,
                weatherVisible = weatherVisible,
                libraryMode = libraryMode,
                columns = columns,
                kidsMode = kidsMode,
                keyRepeat = keyRepeat,
                scope = scope,
                onOrderChanged = onOrderChanged,
                context = context,
                cellCenter = ::cellCenter,
                startLaunch = ::startLaunch,
                activateShelfCard = ::activateShelfCard,
                focusCapsule = ::focusCapsule,
                menuOpen = menuOpen,
                onUserActivity = onUserActivity,
            ),
    ) {
        HomeSceneLayers(
            libraryVisible = libraryVisible,
            libraryMode = libraryMode,
            libraryFade = libraryFade,
            grid = grid,
            overlays = overlays,
            menuOpen = menuOpen,
            shelf = shelf,
            scroll = scroll,
            hideShift = hideShift,
            shelfHide = shelfHide,
            libraryShift = libraryShift,
            scale = scale,
            context = context,
            columns = columns,
            shelfVisible = shelfVisible,
            shelfApp = shelfApp,
            rails = rails,
            currentSection = currentSection,
            shelfCards = shelfCards,
            reduceMotion = reduceMotion,
            previewAutoplay = previewAutoplay,
            kidsMode = kidsMode,
            saverVisible = saverVisible,
            weatherVisible = weatherVisible,
            focusTick = focusTick,
            tileRequesters = tileRequesters,
            pickupRequester = pickupRequester,
            onShelfCard = ::activateShelfCard,
            onSection = shelf::selectSection,
            onActivate = ::activate,
            onPlaced = { gridPlaced = true },
        )

        HomeFolderHost(
            grid = grid,
            folderFocus = folderFocus,
            scale = scale,
            reduceMotion = reduceMotion,
            kidsMode = kidsMode,
            startLaunch = ::startLaunch,
            updateFolderApps = ::updateFolderApps,
            renameFolder = ::renameFolder,
            moveAppToHome = ::moveAppToHome,
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
        )

        HomeContextMenus(
            overlays = overlays,
            grid = grid,
            shelf = shelf,
            discovery = discovery,
            scale = scale,
            context = context,
            prefsName = prefsName,
            cellCenter = ::cellCenter,
            startLaunch = ::startLaunch,
            togglePickup = ::togglePickup,
            openFolderAt = { openFolderAt(it) },
            removeFolderById = ::removeFolderById,
            hideAppFromHome = ::hideAppFromHome,
            resetAppLabel = ::resetAppLabel,
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
            shelfCardAnchor = ::shelfCardAnchor,
            closeShelfMenu = ::closeShelfMenu,
            activateShelfCard = ::activateShelfCard,
            isHiddenFolder = ::isHiddenFolder,
            moveAppIntoFolder = ::moveAppIntoFolder,
        )

        HomeKeyboards(
            overlays = overlays,
            context = context,
            scale = scale,
            prefsName = prefsName,
            createFolderWithApp = ::createFolderWithApp,
            renameAppInHome = ::renameAppInHome,
        )

        HomePanels(
            overlays = overlays,
            grid = grid,
            shelf = shelf,
            searchableApps = searchableApps,
            scale = scale,
            reduceMotion = reduceMotion,
            columns = columns,
            onLaunch = onLaunch,
            onStartSaver = onStartSaver,
            onOpenSettings = {
                if (onOpenSettings != null) {
                    onOpenSettings()
                } else {
                    runCatching {
                        context.startActivity(
                            Intent(context, SettingsActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure { Log.w(HOME_TAG, "cannot open SettingsActivity", it) }
                }
            },
            onOpenSystem = onOpenSystem,
        )

        HomeChromeHost(
            overlays = overlays,
            weather = weather,
            grid = grid,
            shelf = shelf,
            shelfCards = shelfCards,
            scale = scale,
            reduceMotion = reduceMotion,
            weatherVisible = weatherVisible,
            menuOpen = menuOpen,
            context = context,
            weatherRequester = weatherRequester,
            capsuleRequester = capsuleRequester,
            capsuleClock = capsuleClock,
            capsuleNetwork = capsuleNetwork,
            profileEmoji = activeProfile?.emoji?.takeIf { it.isNotBlank() },
            profileColor = profileColor,
            profileName = activeProfile?.name,
            launchProgress = launchProgress,
            onOpenShelfContent = ::openShelfContent,
        )
    }
}
