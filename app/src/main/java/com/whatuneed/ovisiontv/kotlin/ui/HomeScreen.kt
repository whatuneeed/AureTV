package com.whatuneed.ovisiontv.kotlin.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.whatuneed.ovisiontv.kotlin.data.Weather
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.data.homeApps
import com.whatuneed.ovisiontv.kotlin.data.homeFocusKey
import com.whatuneed.ovisiontv.kotlin.data.indexOfHomeFocus
import com.whatuneed.ovisiontv.kotlin.data.isHiddenFolder
import com.whatuneed.ovisiontv.kotlin.data.isSettingsTile
import com.whatuneed.ovisiontv.kotlin.ui.components.LAUNCH_START_DELAY_MS
import com.whatuneed.ovisiontv.kotlin.ui.components.LaunchSpec
import com.whatuneed.ovisiontv.kotlin.ui.components.RenameKeyboardOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.TopShelf
import com.whatuneed.ovisiontv.kotlin.ui.components.shelfCardAnchor
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberClockText
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberNetworkKind
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberTvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryEngine
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import com.whatuneed.ovisiontv.kotlin.discovery.providerTargetForOnline
import com.whatuneed.ovisiontv.kotlin.online.onlinePreviewUri
import kotlinx.coroutines.coroutineScope
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
    var weatherEnabled by remember { mutableStateOf(WeatherPreferences.enabled(context)) }
    var weatherData by remember { mutableStateOf<Weather.Data?>(null) }
    var weatherStale by remember { mutableStateOf(false) }
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

    // The cache is shown at once and the engine fetches only when the schedule
    // or the settings ask; the wait is computed, so idle time costs no wakes.
    LaunchedEffect(resumeTick, foreground) {
        weatherEnabled = WeatherPreferences.enabled(context)
        if (!foreground) return@LaunchedEffect
        if (!weatherEnabled) {
            weatherData = null
            weatherStale = false
            return@LaunchedEffect
        }
        while (true) {
            Weather.refresh(context, false)
            weatherData = Weather.cached(context)
            weatherStale = Weather.stale(context)
            delay(Weather.refreshDelay(context))
        }
    }

    LaunchedEffect(overlays.weatherPanelOpen) {
        if (!overlays.weatherPanelOpen) return@LaunchedEffect
        Weather.refresh(context, false)
        weatherData = Weather.cached(context)
        weatherStale = Weather.stale(context)
    }

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
    val weatherVisible = weatherEnabled && weatherData != null && !libraryMode &&
        grid.openFolder == null && grid.launching == null && !menuOpen && overlays.renameApp == null &&
        !overlays.searchOpen && !overlays.controlCenterOpen && !overlays.weatherPanelOpen && shelf.focus < 0 &&
        !shelf.sectionFocused
    val gridModal = grid.openFolder != null || grid.launching != null || menuOpen ||
        overlays.newFolderApp != null || overlays.renameApp != null || overlays.searchOpen || overlays.controlCenterOpen ||
        overlays.weatherPanelOpen || shelf.playingTrailer != null || shelf.onlineInfo != null
    // Layout gate instead of a blind delay: once the grid is placed every tile's
    // FocusRequester is attached, so the cursor can move on the first frame.
    var gridPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(
        Unit,
        focusTick,
        gridModal,
        overlays.capsuleFocused,
        overlays.weatherFocused,
        grid.focusIndex,
        grid.items,
        grid.editing,
        grid.pickupActive,
        gridPlaced,
        shelf.focus,
        shelf.sectionFocused,
    ) {
        if (gridModal || !gridPlaced) return@LaunchedEffect
        // TopShelf owns the real target while the carousel or its section bar
        // has the cursor; the grid must not pull focus back underneath it.
        if (shelf.focus >= 0 || shelf.sectionFocused) return@LaunchedEffect
        // The pill sits outside the grid; while it owns the cursor the grid must
        // not take the focus back (the panel requests its own tile).
        if (overlays.capsuleFocused) {
            capsuleRequester.requestHomeFocusWhenAttached()
            return@LaunchedEffect
        }
        if (overlays.weatherFocused) {
            weatherRequester.requestHomeFocusWhenAttached()
            return@LaunchedEffect
        }
        // The proxy is the cell under the cursor while it is being moved, so it
        // carries the focus; the tiles the cursor passes over stay silent.
        if (grid.pickupActive) {
            pickupRequester.requestHomeFocusWhenAttached()
            return@LaunchedEffect
        }
        val requester = grid.items.getOrNull(grid.focusIndex)?.let { tileRequesters[it.key] }
        val target = requester ?: focusRequester
        if (!target.requestHomeFocusWhenAttached()) return@LaunchedEffect
        // TalkBack installs its own first-node focus when the window opens, so a
        // cold start would announce tile 0 while the saved cursor sits deeper.
        // Only an accessibility service does that; without one the repeat would
        // be a blind 350 ms window where keys can land on the wrong tile.
        // Compose 1.7 has no enabled flag on its accessibility manager, so the
        // platform one answers whether a screen reader is exploring by touch.
        val screenReader = context
            .getSystemService(AccessibilityManager::class.java)
            ?.isTouchExplorationEnabled == true
        if (!screenReader) return@LaunchedEffect
        delay(350)
        target.requestHomeFocusWhenAttached()
    }

    // Weather turned off in Settings, or the pill left the screen: the cursor
    // must not sit on a node that no longer exists. While the forecast panel is
    // open the pill is hidden but keeps the return target for Back.
    LaunchedEffect(weatherVisible, overlays.weatherPanelOpen, weatherEnabled) {
        if (!weatherVisible && overlays.weatherFocused && !overlays.weatherPanelOpen) {
            overlays.weatherFocused = false
            overlays.weatherFromShelf = false
        }
        if (!weatherEnabled) {
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
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) onUserActivity()
                // Preview runs before an overlay can consume the release: a key
                // whose KeyUp lands on the panel must not stay "pressed" here
                // and swallow the next press of the same key after it closes.
                if (event.type == KeyEventType.KeyUp) keyRepeat.release(event.key)
                false
            }
            .onKeyEvent { event ->
                // The zoom owns the screen until the app or the return finishes.
                if (grid.launching != null) return@onKeyEvent true
                // Search is a separate surface: anything the panel did not use
                // must not reach the grid behind it (a MENU press, for example).
                if (overlays.searchOpen) return@onKeyEvent true
                // The Control Center owns the remote while it is open; the
                // focused tile already consumed its own keys.
                if (overlays.controlCenterOpen) return@onKeyEvent true
                // A shelf card menu is its own focus surface.
                if (shelf.menu != null) return@onKeyEvent true
                // The forecast panel has its own focus and Back handler.
                if (overlays.weatherPanelOpen) return@onKeyEvent true
                // A context menu owns the remote. A hold that opened it keeps
                // sending repeats, and some remotes deliver each repeat as its
                // own press and release; those pairs must not reach the grid.
                if (menuOpen || overlays.newFolderApp != null || overlays.renameApp != null) {
                    return@onKeyEvent true
                }
                handleHomeKey(
                    event = event,
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
                )
            }
            .focusable(),
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
                        onCardActivate = ::activateShelfCard,
                        onSectionActivate = shelf::selectSection,
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
                libraryVisible = libraryVisible,
                libraryFade = libraryFade,
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
                onPlaced = { gridPlaced = true },
                onActivate = ::activate,
            )
        }

        grid.openFolder?.let { open ->
            FolderOverlay(
                folder = open.folder,
                tileCenter = Offset(open.tileX, open.tileY),
                scale = scale,
                launching = grid.launching != null,
                initialFocusKey = folderFocus[open.folder.id],
                onFocusKeyChanged = { key ->
                    if (key == null) {
                        folderFocus.remove(open.folder.id)
                    } else {
                        folderFocus[open.folder.id] = key
                    }
                },
                onLaunch = { app, center -> startLaunch(app, center) },
                onClosing = { grid.folderClosing = true },
                onClose = {
                    grid.openFolder = null
                    grid.folderClosing = false
                },
                onAppsChanged = { apps -> updateFolderApps(open.folder.id, apps) },
                onRename = { name -> renameFolder(open.folder.id, name) },
                onMoveToHome = { app -> moveAppToHome(open.folder.id, app) },
                onAppInfo = onAppInfo,
                onUninstall = onUninstall,
                reduceMotion = reduceMotion,
                kidsMode = kidsMode,
            )
        }

        overlays.menuFolderId?.let { folderId ->
            val index = grid.items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
            val folder = grid.items.getOrNull(index) as? HomeItem.FolderItem
            if (folder != null) {
                FolderContextMenu(
                    folder = folder,
                    anchor = cellCenter(index),
                    scale = scale,
                    suppressOkUntilRelease = overlays.menuOpenedByHold,
                    onOpen = { openFolderAt(index) },
                    onMove = {
                        grid.focusIndex = index
                        grid.editing = true
                        grid.mergeVictim = null
                        grid.confirmIndex = null
                        togglePickup()
                    },
                    onRequestDelete = {
                        // The menu already animated out; ask once more
                        // before the layout loses the folder.
                        overlays.menuFolderId = null
                        overlays.folderDeleteId = folderId
                    },
                    onDismiss = {
                        overlays.menuFolderId = null
                        overlays.menuOpenedByHold = false
                    },
                )
            }
        }

        // Destructive confirmation for a folder. It is a second menu, not a
        // silent delete: the apps return home, but the user still chooses.
        overlays.folderDeleteId?.let { folderId ->
            val index = grid.items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
            val folder = grid.items.getOrNull(index) as? HomeItem.FolderItem
            if (folder != null) {
                FolderDeleteMenu(
                    folder = folder,
                    anchor = cellCenter(index),
                    scale = scale,
                    onCancel = { overlays.folderDeleteId = null },
                    onDelete = {
                        removeFolderById(folderId)
                        overlays.folderDeleteId = null
                    },
                    onDismiss = { overlays.folderDeleteId = null },
                )
            }
        }

        overlays.menuApp?.let { app ->
            val index = grid.items.indexOfFirst {
                it is HomeItem.AppItem && it.app.packageName == app.packageName
            }
            if (index >= 0) {
                // The own settings tile is the launcher's screen: hiding or
                // uninstalling it would remove the way into Settings.
                val own = app.packageName == context.packageName
                val hasCustomLabel = app.available &&
                    AppLabelStore.label(context, app.packageName, prefsName) != null
                AppContextMenu(
                    app = app,
                    own = own,
                    hasCustomLabel = hasCustomLabel,
                    anchor = cellCenter(index),
                    scale = scale,
                    suppressOkUntilRelease = overlays.menuOpenedByHold,
                    onOpen = { startLaunch(app, cellCenter(index)) },
                    onMove = {
                        grid.focusIndex = index
                        grid.editing = true
                        grid.mergeVictim = null
                        grid.confirmIndex = null
                        togglePickup()
                    },
                    onMoveToFolder = { overlays.moveToFolderApp = app },
                    onHide = { hideAppFromHome(app) },
                    onRename = { overlays.renameApp = app },
                    onResetLabel = { resetAppLabel(app) },
                    onAppInfo = { onAppInfo(app) },
                    onUninstall = { onUninstall(app) },
                    onDismiss = {
                        overlays.menuApp = null
                        overlays.menuOpenedByHold = false
                    },
                )
            }
        }

        shelf.menu?.let { item ->
            val favorite = discovery.isFavorite(item.id)
            ShelfContextMenu(
                item = item,
                favorite = favorite,
                anchor = shelfCardAnchor(),
                scale = scale,
                suppressOkUntilRelease = overlays.menuOpenedByHold,
                onOpen = {
                    closeShelfMenu()
                    activateShelfCard(item)
                },
                onToggleFavorite = {
                    if (favorite) {
                        discovery.removeFavorite(item.id)
                    } else {
                        discovery.addFavorite(
                            item.copy(reason = DiscoveryReason.FAVORITE),
                        )
                    }
                    closeShelfMenu()
                    shelf.revision++
                },
                onHide = {
                    discovery.hide(item)
                    closeShelfMenu()
                    shelf.revision++
                },
                onDismiss = ::closeShelfMenu,
            )
        }

        // 05_FOLDERS: the app menu's "Move to Folder…" lists the user folders and
        // ends with "New Folder…", as the Java launcher did — a folder made here
        // is named first and takes the app with it.
        overlays.moveToFolderApp?.let { app ->
            val folders = grid.items.filterIsInstance<HomeItem.FolderItem>()
                .filterNot { isHiddenFolder(it) }
            val tileIndex = grid.items.indexOfFirst {
                it is HomeItem.AppItem && it.app.packageName == app.packageName
            }
            MoveToFolderMenu(
                folders = folders,
                anchor = cellCenter(if (tileIndex >= 0) tileIndex else grid.focusIndex),
                scale = scale,
                onMove = { folder -> moveAppIntoFolder(app, folder.id) },
                onNewFolder = { overlays.newFolderApp = app },
                onDismiss = { overlays.moveToFolderApp = null },
            )
        }

        overlays.newFolderApp?.let { app ->
            RenameKeyboardOverlay(
                title = context.getString(R.string.new_folder_title),
                initial = "",
                scale = scale,
                onDone = { name ->
                    overlays.newFolderApp = null
                    createFolderWithApp(name, app)
                },
                onCancel = { overlays.newFolderApp = null },
                placeholder = context.getString(R.string.keyboard_placeholder),
                note = context.getString(R.string.new_folder_note),
            )
        }

        overlays.renameApp?.let { app ->
            RenameKeyboardOverlay(
                title = context.getString(R.string.rename_app_title),
                initial = AppLabelStore.label(context, app.packageName, prefsName) ?: app.label,
                scale = scale,
                onDone = { name -> renameAppInHome(app, name) },
                onCancel = { overlays.renameApp = null },
                placeholder = context.getString(R.string.keyboard_placeholder),
                note = context.getString(R.string.rename_app_note),
            )
        }

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

        HomeTopChrome(
            scale = scale,
            reduceMotion = reduceMotion,
            weatherPanelOpen = overlays.weatherPanelOpen,
            weatherData = weatherData,
            weatherStale = weatherStale,
            weatherVisible = weatherVisible,
            weatherFocused = overlays.weatherFocused,
            weatherManual = { WeatherPreferences.manual(context) },
            weatherRequester = weatherRequester,
            capsuleFocused = overlays.capsuleFocused,
            controlCenterOpen = overlays.controlCenterOpen,
            capsuleClock = capsuleClock,
            capsuleNetwork = capsuleNetwork,
            capsuleRequester = capsuleRequester,
            profileEmoji = activeProfile?.emoji?.takeIf { it.isNotBlank() },
            profileColor = profileColor,
            profileName = activeProfile?.name,
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
                openShelfContent(target)
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
                openShelfContent(provider)
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
}
