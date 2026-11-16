package com.whatuneed.ovisiontv.kotlin.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.SettingsActivity
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.HomeEdit
import com.whatuneed.ovisiontv.kotlin.data.KidsMode
import com.whatuneed.ovisiontv.kotlin.data.LauncherPrefs
import com.whatuneed.ovisiontv.kotlin.data.ProfileCatalog
import com.whatuneed.ovisiontv.kotlin.data.ProfileStore
import com.whatuneed.ovisiontv.kotlin.data.SafePreferences
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.Weather
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import com.whatuneed.ovisiontv.kotlin.data.hideApp
import com.whatuneed.ovisiontv.kotlin.data.homeApps
import com.whatuneed.ovisiontv.kotlin.data.homeFocusKey
import com.whatuneed.ovisiontv.kotlin.data.indexOfHomeFocus
import com.whatuneed.ovisiontv.kotlin.data.isHiddenFolder
import com.whatuneed.ovisiontv.kotlin.data.isSettingsTile
import com.whatuneed.ovisiontv.kotlin.data.removeFolder
import com.whatuneed.ovisiontv.kotlin.data.takeAppOutOfFolder
import com.whatuneed.ovisiontv.kotlin.ui.components.AppCard
import com.whatuneed.ovisiontv.kotlin.ui.components.CC_CAPSULE_SIDE
import com.whatuneed.ovisiontv.kotlin.ui.components.CC_CAPSULE_TOP
import com.whatuneed.ovisiontv.kotlin.ui.components.ControlCenter
import com.whatuneed.ovisiontv.kotlin.ui.components.FolderCard
import com.whatuneed.ovisiontv.kotlin.ui.components.LAUNCH_START_DELAY_MS
import com.whatuneed.ovisiontv.kotlin.ui.components.LaunchOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.LaunchSpec
import com.whatuneed.ovisiontv.kotlin.ui.components.RenameKeyboardOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.SearchOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.StatusCapsule
import com.whatuneed.ovisiontv.kotlin.ui.components.TopShelf
import com.whatuneed.ovisiontv.kotlin.ui.components.TvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.ui.components.TvMenu
import com.whatuneed.ovisiontv.kotlin.ui.components.TvMenuEntry
import com.whatuneed.ovisiontv.kotlin.ui.components.TrailerOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.WEATHER_CAPSULE_LEFT
import com.whatuneed.ovisiontv.kotlin.ui.components.WEATHER_CAPSULE_TOP
import com.whatuneed.ovisiontv.kotlin.ui.components.WeatherCapsule
import com.whatuneed.ovisiontv.kotlin.ui.components.WeatherPanel
import com.whatuneed.ovisiontv.kotlin.ui.components.shelfCardAnchor
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberClockText
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberNetworkKind
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberTvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryEngine
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySection
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySnapshot
import com.whatuneed.ovisiontv.kotlin.discovery.defaultDiscoverySection
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

// tvOS does not expose the long-press delay; Android reports a hold as ~500 ms
// with releases that can land near 430 ms, so 350 ms is a safe project value.
private const val TAG = "OVisionHome"
private const val MENU_HOLD_MS = 350L

private data class PlayingTrailer(val title: String, val url: String)

// Phase 08: only a resting cursor is remembered; a 400 ms pause reads as an
// intentional stop and is shorter than the time it takes to leave the room.
private const val FOCUS_SAVE_MS = 400L

// 14_MOTION_DESIGN §2.3: folder creation needs 0.75 s of hover, then a 0.3 s
// confirmation (M8), after which the two apps become a folder.
private const val FOLDER_HOLD_MS = 750L
private const val FOLDER_CONFIRM_MS = 300L

// 05_FOLDERS §5: opening and closing both take 0.5 s on the same curve.
private const val FOLDER_ZOOM_MS = 500

// 14_MOTION_DESIGN M18: the tvOS parade advances every 10 s. A manual step
// restarts the clock, as the Java hero did.
private const val SHELF_ADVANCE_MS = 10_000L
// A hero must stay focused briefly before it counts as seen; otherwise a fast
// pass through a rail writes prefs for every card that never really showed.
private const val SHOWN_SETTLE_MS = 500L

// 02_HOME_SCREEN §3: in the app library the first row starts near the safe area,
// not at the dock band. Both values are project decisions; the RESEARCH gives
// the dock geometry and row pitch, not a separate library screen.
private const val LIBRARY_TOP = 150f
private const val LIBRARY_SHIFT = TvOs.ROW_TOP - LIBRARY_TOP
private const val LIBRARY_MOTION_MS = 300
private const val LIBRARY_FADE_MS = 220

// Reordering is not measured for tvOS; 200 ms is the project value, aligned with
// the shared focus rhythm so the neighbour does not jump after the drop.
private const val TILE_MOVE_MS = 200

private data class OpenFolder(
    val folder: HomeItem.FolderItem,
    val tileX: Float,
    val tileY: Float,
)

// T1/T5 start from the focused cell, so the cell centre is captured with the app.
private data class Launching(
    val app: TvApp,
    val cellCenter: Offset,
)

// A requester created in this composition is attached in the following layout
// pass, so a data change (app installed, folder emptied) would make an immediate
// request throw. Retrying on the next frame keeps the cursor moving without a
// blind millisecond delay.
private suspend fun FocusRequester.requestWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}

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

    // The grid keeps its own order; the parent replaces `initialItems` on install/uninstall.
    var items by remember(initialItems) { mutableStateOf(initialItems) }

    // A removed focused app leaves no package to restore; the cursor then stays
    // where it was instead of jumping to the first tile.
    var lastFocusIndex by remember { mutableIntStateOf(-1) }
    // The persisted key lags by the save debounce, so the grid also keeps the
    // live key: a list replaced right after a press must not restore an older app.
    var lastFocusKey by remember { mutableStateOf<String?>(null) }
    var focusIndex by rememberSaveable(initialItems) {
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
        mutableIntStateOf(if (focusIndex < dockSize) focusIndex else restored ?: fallback)
    }

    var editing by remember { mutableStateOf(false) }
    // The shelf belongs to the app under the dock cursor; its cards come only
    // from rows that app published to the TV provider.
    val shelfApp = (items.getOrNull(shelfIndex) as? HomeItem.AppItem)?.app
    // Discovery is profile-scoped: a new profile creates a new engine, so two
    // profiles never share usage, favorites or hidden cards.
    val discovery = remember(context, prefsName, profileId) {
        DiscoveryEngine(context.applicationContext, prefsName, profileId)
    }
    var discoverySnapshot by remember { mutableStateOf(DiscoverySnapshot.EMPTY) }
    // The dock app's own rail is the tvOS default; every other rail is one Up
    // away. The selected rail is a string so rememberSaveable survives a
    // process restore without an enum serializer.
    var discoverySection by rememberSaveable { mutableStateOf<String?>(null) }
    var sectionFocused by remember { mutableStateOf(false) }
    var discoveryRevision by remember { mutableIntStateOf(0) }
    var shelfMenu by remember { mutableStateOf<DiscoveryItem?>(null) }
    var playingTrailer by remember { mutableStateOf<PlayingTrailer?>(null) }
    val sectionCardMemory = remember { mutableStateMapOf<String, Int>() }
    var lastShelfPackage by remember { mutableStateOf<String?>(null) }
    val rails = discoverySnapshot.rails
    val currentSection = rails.firstOrNull { it.section.name == discoverySection }?.section
        ?: defaultDiscoverySection(rails)
    val currentRail = rails.firstOrNull { it.section == currentSection }
    val shelfCards = currentRail?.items.orEmpty()
    var shelfFocus by remember { mutableIntStateOf(-1) }
    // A resume, a closed menu or a closed search removes the node that held
    // Android focus. TopShelf owns the card targets, so it gets a tick.
    var shelfFocusTick by remember { mutableIntStateOf(0) }
    // Which recommendation fills the screen; focusedCard says whether the
    // rail owns the remote. They move together while the shelf has focus.
    var shelfSelected by remember { mutableIntStateOf(0) }

    fun selectDiscoverySection(section: DiscoverySection) {
        val previous = currentSection
        if (previous != null) sectionCardMemory[previous.name] = shelfSelected
        discoverySection = section.name
        val rail = rails.firstOrNull { it.section == section } ?: return
        shelfSelected = (sectionCardMemory[section.name] ?: 0)
            .coerceIn(0, rail.items.lastIndex.coerceAtLeast(0))
        if (shelfFocus >= 0) shelfFocus = shelfSelected
    }

    fun enterShelf() {
        if (rails.isEmpty()) return
        val target = currentSection ?: defaultDiscoverySection(rails) ?: return
        discoverySection = target.name
        sectionFocused = false
        val rail = rails.firstOrNull { it.section == target } ?: return
        shelfSelected = shelfSelected.coerceIn(0, rail.items.lastIndex.coerceAtLeast(0))
        shelfFocus = shelfSelected
    }
    var pickupActive by remember { mutableStateOf(false) }
    var pickupOrigin by remember { mutableStateOf<Int?>(null) }
    // Order before the drag: BACK during a pickup puts every tile back, since
    // the drag reorders the grid live (neighbours slide on every step).
    var pickupSnapshot by remember { mutableStateOf<List<HomeItem>?>(null) }
    var pickupStart by remember { mutableIntStateOf(-1) }
    // The tile the drag last pushed away: holding there for a moment folds the
    // two into a folder, which is how folders are created on tvOS.
    var mergeVictim by remember { mutableStateOf<Int?>(null) }
    var confirmIndex by remember { mutableStateOf<Int?>(null) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var longPressHandled by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<OpenFolder?>(null) }
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
    var folderClosing by remember { mutableStateOf(false) }
    var launching by remember { mutableStateOf<Launching?>(null) }
    var menuFolderId by remember { mutableStateOf<String?>(null) }
    // Deleting a folder is destructive to the layout even though its apps are
    // preserved, so the menu asks once more. Keeping the pending id in state
    // also keeps the grid from taking the remote while the confirmation is up.
    var folderDeleteId by remember { mutableStateOf<String?>(null) }
    var menuApp by remember { mutableStateOf<TvApp?>(null) }
    // A hold opens the menu while OK is still down; its auto-repeat would pick
    // the first row the moment the panel takes focus. The menu waits for the
    // release before it accepts OK when it was opened this way.
    var menuOpenedByHold by remember { mutableStateOf(false) }
    // "Move to Folder…" closes the app menu and opens a picker over the grid.
    var moveToFolderApp by remember { mutableStateOf<TvApp?>(null) }
    // "New Folder…" in that picker: the keyboard names a folder around the app.
    var newFolderApp by remember { mutableStateOf<TvApp?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    // The status pill is the only way into the Control Center. `capsuleFromShelf`
    // remembers that Up came from the carousel, so Down returns there instead of
    // dropping the grid back into dock mode.
    var controlCenterOpen by remember { mutableStateOf(false) }
    var capsuleFocused by remember { mutableStateOf(false) }
    var capsuleFromShelf by remember { mutableStateOf(false) }
    val capsuleRequester = remember { FocusRequester() }
    val capsuleClock = rememberClockText()
    val capsuleNetwork = rememberNetworkKind()
    // The weather pill sits in the other top corner. It is hidden (and cannot
    // be focused) while no forecast has loaded or the weather is off.
    var weatherFocused by remember { mutableStateOf(false) }
    var weatherFromShelf by remember { mutableStateOf(false) }
    var weatherPanelOpen by remember { mutableStateOf(false) }
    var weatherEnabled by remember { mutableStateOf(WeatherPreferences.enabled(context)) }
    var weatherData by remember { mutableStateOf<Weather.Data?>(null) }
    var weatherStale by remember { mutableStateOf(false) }
    val weatherRequester = remember { FocusRequester() }
    // OK is a two-edge press: the release only launches when this grid saw the
    // press, so a menu that closes on KeyDown cannot hand its release to the tile.
    var okDown by remember { mutableStateOf(false) }
    val keyRepeat = rememberTvKeyRepeat()
    val launchProgress = remember { Animatable(0f) }

    // 02_HOME_SCREEN §3: the dock is the first row of the same grid. Leaving it
    // for the apps below turns the grid into the full-screen library: the shelf
    // and band fade, and the grid rises to the top instead of scrolling under a
    // static header. Coming back up restores the dock.
    val libraryMode = items.size > columns && focusIndex >= columns
    val rows = ((items.size + columns - 1) / columns).coerceAtLeast(1)
    val bottomReserve = if (items.size % columns == 0) 88f + 150f else 0f
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
        Animatable(scrollTargetFor(focusIndex))
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
        shelfFocus >= 0 ||
            sectionFocused ||
            (capsuleFocused && capsuleFromShelf) ||
            (weatherFocused && weatherFromShelf)
        ) && !libraryMode
    val shelfHide by animateFloatAsState(
        targetValue = if (shelfHeld) TvOs.HIDE_GRID_SHIFT else 0f,
        animationSpec = tween(
            motionDuration(TvOs.SCROLL_DURATION_MS, reduceMotion),
            easing = FastOutSlowInEasing,
        ),
        label = "shelfHide",
    )

    LaunchedEffect(libraryMode) {
        coroutineScope {
            launch {
                libraryShift.animateTo(
                    targetValue = if (libraryMode) LIBRARY_SHIFT else 0f,
                    animationSpec = tween(
                        motionDuration(LIBRARY_MOTION_MS, reduceMotion),
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
            launch {
                libraryFade.animateTo(
                    targetValue = if (libraryMode) 0f else 1f,
                    animationSpec = tween(
                        motionDuration(LIBRARY_FADE_MS, reduceMotion),
                        easing = FastOutSlowInEasing,
                    ),
                )
            }
        }
    }

    LaunchedEffect(items.size) {
        if (focusIndex > items.lastIndex) {
            focusIndex = items.lastIndex.coerceAtLeast(0)
        }
    }

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

    LaunchedEffect(weatherPanelOpen) {
        if (!weatherPanelOpen) return@LaunchedEffect
        Weather.refresh(context, false)
        weatherData = Weather.cached(context)
        weatherStale = Weather.stale(context)
    }

    // Refresh on resume, package change and after a hide/favorite. The dock
    // app's own rail is selected when the cursor moved to another app, but a
    // refresh never throws the viewer back to the first card (PHASE 33).
    LaunchedEffect(
        shelfApp?.packageName,
        resumeTick,
        items,
        kidsMode,
        discoveryRevision,
    ) {
        val hiddenPackages = items
            .filterIsInstance<HomeItem.FolderItem>()
            .filter(::isHiddenFolder)
            .flatMap { it.apps }
            .mapTo(HashSet()) { it.packageName }
        val apps = homeApps(items).filterNot { it.packageName in hiddenPackages }
        // A refresh must keep the card, not the index: a provider row can move
        // when another app publishes something newer.
        val selectedId = shelfCards.getOrNull(shelfSelected)?.id
        val snapshot = discovery.refresh(
            currentPackage = shelfApp?.packageName,
            apps = apps,
            visiblePackages = apps.mapTo(HashSet()) { it.packageName },
            kidsMode = kidsMode,
        )
        discoverySnapshot = snapshot
        val previous = lastShelfPackage
        lastShelfPackage = shelfApp?.packageName
        val appRail = snapshot.rails.firstOrNull { it.section == DiscoverySection.FROM_APP }
        val wanted = when {
            appRail != null && (previous != shelfApp?.packageName || discoverySection == null) ->
                DiscoverySection.FROM_APP
            else -> snapshot.rails.firstOrNull { it.section.name == discoverySection }?.section
                ?: defaultDiscoverySection(snapshot.rails)
        }
        if (wanted == null) {
            discoverySection = null
            shelfSelected = 0
            shelfFocus = -1
            sectionFocused = false
            return@LaunchedEffect
        }
        discoverySection = wanted.name
        val rail = snapshot.rails.firstOrNull { it.section == wanted }
        val last = rail?.items?.lastIndex?.coerceAtLeast(0) ?: 0
        if (previous != shelfApp?.packageName) {
            shelfSelected = 0
            if (shelfFocus >= 0) shelfFocus = 0
            sectionCardMemory.clear()
        } else {
            val restored = rail?.items?.indexOfFirst { it.id == selectedId } ?: -1
            shelfSelected = if (restored >= 0) {
                restored
            } else {
                shelfSelected.coerceIn(0, last)
            }
            if (shelfFocus >= 0) shelfFocus = shelfSelected
        }
    }

    // Remember only the hero the viewer actually saw. Marking a whole rail
    // would demote cards that never entered the viewport.
    LaunchedEffect(currentSection, shelfCards, shelfSelected, discoveryRevision) {
        val section = currentSection ?: return@LaunchedEffect
        delay(SHOWN_SETTLE_MS)
        shelfCards.getOrNull(shelfSelected)?.let { discovery.markShown(section, it) }
    }

    LaunchedEffect(
        editing,
        openFolder,
        launching,
        menuFolderId,
        folderDeleteId,
        menuApp,
        moveToFolderApp,
        newFolderApp,
        searchOpen,
        controlCenterOpen,
        weatherPanelOpen,
        playingTrailer,
    ) {
        if (editing || openFolder != null || launching != null ||
            menuFolderId != null || folderDeleteId != null || menuApp != null ||
            moveToFolderApp != null || newFolderApp != null || searchOpen ||
            controlCenterOpen || weatherPanelOpen || playingTrailer != null
        ) {
            shelfFocus = -1
            sectionFocused = false
        }
    }

    // The Java hero advanced every 8 s and restarted that clock on any click;
    // stepping the carousel changes shelfSelected, which restarts this timer.
    // The pill and the panel stop it: a page turn under the focused status row
    // would change what Down returns to.
    LaunchedEffect(
        shelfApp?.packageName,
        currentSection,
        shelfCards,
        shelfSelected,
        shelfFocus,
        sectionFocused,
        shelfMenu,
        libraryMode,
        editing,
        openFolder,
        launching,
        menuFolderId,
        menuApp,
        moveToFolderApp,
        newFolderApp,
        searchOpen,
        controlCenterOpen,
        capsuleFocused,
        weatherFocused,
        weatherPanelOpen,
        playingTrailer,
    ) {
        if (shelfCards.size <= 1 || sectionFocused || shelfFocus >= 0 ||
            shelfMenu != null || libraryMode || editing || openFolder != null ||
            launching != null || menuFolderId != null || folderDeleteId != null ||
            menuApp != null || moveToFolderApp != null || newFolderApp != null ||
            searchOpen || controlCenterOpen || capsuleFocused || weatherFocused ||
            weatherPanelOpen || playingTrailer != null
        ) {
            return@LaunchedEffect
        }
        delay(SHELF_ADVANCE_MS)
        shelfSelected = (shelfSelected + 1) % shelfCards.size
        if (shelfFocus >= 0) shelfFocus = shelfSelected
    }

    LaunchedEffect(focusIndex) {
        lastFocusIndex = focusIndex
    }

    // The tile itself is part of the key: deleting a folder can put another app
    // at the same index, and a size-only key would keep the dead folder saved.
    LaunchedEffect(focusIndex, items.size, items.getOrNull(focusIndex)) {
        val focused = items.getOrNull(focusIndex) ?: return@LaunchedEffect
        if (focusIndex < columns) shelfIndex = focusIndex

        scroll.animateTo(
            targetValue = scrollTargetFor(focusIndex),
            animationSpec = tween(
                durationMillis = TvOs.SCROLL_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    // Phase 08: a cursor that is still moving is not a choice. Only the focus
    // that rests for a moment is saved, so a burst of presses neither rewrites
    // the preference on every step nor leaves a half-travelled position behind.
    LaunchedEffect(focusIndex, items.getOrNull(focusIndex)) {
        val focused = items.getOrNull(focusIndex) ?: return@LaunchedEffect
        lastFocusKey = homeFocusKey(focused)
        delay(FOCUS_SAVE_MS)
        val editor = prefs.edit()
        editor.putString(LauncherPrefs.KEY_FOCUS_PACKAGE, homeFocusKey(focused))
        if (focused is HomeItem.AppItem && focusIndex < columns) {
            editor.putString(LauncherPrefs.KEY_SHELF_PACKAGE, focused.app.packageName)
        }
        editor.apply()
    }

    // 12_SYSTEM_TRANSITIONS §3: a launch hides the grid by the same 340 pt as a folder.
    // Closing starts the return at BACK, together with the panel zoom (T4, 0.5 s),
    // instead of waiting for the panel to reach the icon and adding a second half-second.
    LaunchedEffect(openFolder != null, launching != null, folderClosing) {
        val hidden = launching != null || (openFolder != null && !folderClosing)
        hideShift.animateTo(
            targetValue = if (hidden) TvOs.HIDE_GRID_SHIFT else 0f,
            animationSpec = tween(
                motionDuration(FOLDER_ZOOM_MS, reduceMotion),
                easing = TravelEasing,
            ),
        )
    }

    // T1: the card grows for 0.5 s; the app is started at 0.38 s so its window
    // covers the end of the zoom instead of waiting for it. Reduce Motion keeps
    // the same hand-off but starts the app on the first frame.
    LaunchedEffect(launching?.app?.packageName) {
        val active = launching ?: return@LaunchedEffect
        if (reduceMotion) {
            launchProgress.snapTo(1f)
            if (!onLaunch(active.app)) {
                launchProgress.snapTo(0f)
                launching = null
            }
            return@LaunchedEffect
        }
        launchProgress.snapTo(0f)
        coroutineScope {
            val forward = launch { launchProgress.animateTo(1f, LaunchSpec) }
            delay(LAUNCH_START_DELAY_MS)
            if (!onLaunch(active.app)) {
                forward.cancel()
                launchProgress.animateTo(0f, tween(200))
                launching = null
            }
            forward.join()
        }
    }

    // T2: the app window hid the last frame of the zoom, so the reverse starts
    // from a full-screen card. The grid never moved, which is the tvOS rule of
    // pre-positioning the offset before the return frame.
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0 || launching == null) return@LaunchedEffect
        if (reduceMotion) {
            launchProgress.snapTo(0f)
            launching = null
            return@LaunchedEffect
        }
        // 12_SYSTEM_TRANSITIONS T2: the grid must already sit at its final
        // offset on the first return frame; animating the 340 pt back while the
        // card shrinks produces a visible double move. A launch from a folder
        // returns to an open panel, so the grid stays hidden there (T6).
        if (openFolder == null) hideShift.snapTo(0f)
        launchProgress.snapTo(1f)
        launchProgress.animateTo(0f, LaunchSpec)
        launching = null
    }

    fun targetFor(dx: Int, dy: Int): Int {
        val row = focusIndex / columns
        val column = focusIndex % columns
        return if (dx != 0) {
            val nextColumn = column + dx
            if (nextColumn !in 0 until columns) {
                focusIndex
            } else {
                (focusIndex + dx).takeIf { it < items.size } ?: focusIndex
            }
        } else {
            val targetRow = row + dy
            if (targetRow < 0) {
                focusIndex
            } else {
                // An incomplete last row has no cell under the same column;
                // tvOS moves to the nearest cell of that row instead of
                // refusing to leave the current one.
                (targetRow * columns + column)
                    .takeIf { it <= items.lastIndex }
                    ?: items.lastIndex.coerceAtLeast(0)
            }
        }
    }

    fun moveFocus(dx: Int, dy: Int) {
        val target = targetFor(dx, dy)
        if (target == focusIndex) return
        // A press that is still counting belongs to the cell that had focus:
        // moving while OK is held must not launch the cell that receives focus.
        if (holdJob != null) longPressHandled = true
        holdJob?.cancel()
        holdJob = null
        confirmIndex = null
        // A picked-up tile takes the new slot at once, so every neighbour
        // between the two cells slides over instead of waiting for the drop
        // (14_MOTION_DESIGN: the grid follows the drag, it does not jump).
        val origin = pickupOrigin
        if (pickupActive && origin != null && origin in items.indices && target in items.indices) {
            val list = items.toMutableList()
            val moving = list.removeAt(origin)
            list.add(target, moving)
            items = list
            // The tile that was under the cursor now sits where the drag
            // started; lingering on it is the folder gesture.
            mergeVictim = if (target > origin) target - 1 else target + 1
            pickupOrigin = target
            focusIndex = target
            return
        }
        focusIndex = target
    }

    // Up leaves the grid for the status pill. A running edit timer belongs to
    // the cell the cursor came from, so it is dropped with the move; the held
    // OK must not launch that cell when it is released over the pill either.
    fun focusCapsule(fromShelf: Boolean) {
        holdJob?.cancel()
        holdJob = null
        longPressHandled = true
        okDown = false
        confirmIndex = null
        capsuleFromShelf = fromShelf
        capsuleFocused = true
    }

    fun endPickup() {
        pickupActive = false
        pickupOrigin = null
        pickupSnapshot = null
        pickupStart = -1
        mergeVictim = null
        confirmIndex = null
    }

    // Dropping only commits the order the drag already produced; the folder
    // gesture belongs to the dwell timer, not to the release.
    fun dropPickup() {
        if (pickupSnapshot != null && pickupSnapshot !== items) onOrderChanged(items)
        endPickup()
    }

    // 05_FOLDERS: two apps fold into one tile, an app dropped on a folder joins
    // it. Both tiles are moved by the drag itself, so the merge works on the
    // current positions.
    fun mergePickupInto(victimIndex: Int) {
        if (!pickupActive || pickupOrigin != focusIndex) return
        val dragged = (items.getOrNull(focusIndex) as? HomeItem.AppItem)?.app ?: return
        when (val victim = items.getOrNull(victimIndex)) {
            is HomeItem.AppItem -> {
                val folder = HomeItem.FolderItem(
                    id = UUID.randomUUID().toString(),
                    name = context.getString(R.string.folder_default),
                    apps = listOf(dragged, victim.app),
                )
                val list = items.toMutableList()
                list.removeAt(max(focusIndex, victimIndex))
                list.removeAt(min(focusIndex, victimIndex))
                val insertAt = min(focusIndex, victimIndex).coerceIn(0, list.size)
                list.add(insertAt, folder)
                items = list
                focusIndex = insertAt
            }

            is HomeItem.FolderItem -> {
                val list = items.toMutableList()
                list.removeAt(focusIndex)
                val folderPos = list.indexOfFirst {
                    it is HomeItem.FolderItem && it.id == victim.id
                }
                if (folderPos < 0) return
                val folder = list[folderPos] as HomeItem.FolderItem
                list[folderPos] = folder.copy(apps = folder.apps + dragged)
                items = list
                focusIndex = folderPos
            }

            else -> return
        }
        endPickup()
        onOrderChanged(items)
    }

    fun togglePickup() {
        if (pickupActive) {
            dropPickup()
        } else {
            // The hidden folder is a fixed bucket: dragging it would let the
            // auto-created tile drift away from the end of the grid.
            if (isHiddenFolder(items.getOrNull(focusIndex))) return
            pickupActive = true
            pickupOrigin = focusIndex
            pickupStart = focusIndex
            pickupSnapshot = items
        }
    }

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

    fun openFolderAt(index: Int) {
        val folder = items.getOrNull(index) as? HomeItem.FolderItem ?: return
        val center = cellCenter(index)
        editing = false
        folderClosing = false
        openFolder = OpenFolder(folder, center.x, center.y)
    }

    fun startLaunch(app: TvApp, center: Offset) {
        if (launching != null) return
        if (!app.available) {
            Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        holdJob?.cancel()
        holdJob = null
        longPressHandled = false
        editing = false
        pickupActive = false
        pickupOrigin = null
        pickupSnapshot = null
        pickupStart = -1
        mergeVictim = null
        confirmIndex = null
        // Kids mode: the launcher's own Settings tile opens through the PIN
        // gate the way the Control Center does. The launch zoom would only
        // have to reverse when the gate replaces the launch.
        if (kidsMode && app.isSettingsTile(context.packageName) && onOpenSettings != null) {
            onOpenSettings?.invoke()
            return
        }
        launching = Launching(app, center)
    }

    // A provider deep link is opened only when it resolves back to the app that
    // published it; otherwise the app itself opens, never a foreign package.
    fun closeShelfMenu() {
        shelfMenu = null
        menuOpenedByHold = false
        shelfFocus = shelfSelected
        shelfFocusTick++
    }

    fun activateShelfCard(card: DiscoveryItem) {
        // An online card is a real official preview, not a package launch. It
        // is played by the launcher itself, so no foreign app is started and
        // no browser is needed on the TV.
        if (card.source == DiscoverySource.ONLINE) {
            val url = card.intentUri
            if (url != null && url.startsWith("https://")) {
                playingTrailer = PlayingTrailer(card.title, url)
            } else {
                Toast.makeText(context, R.string.trailer_unavailable, Toast.LENGTH_SHORT).show()
            }
            return
        }
        // The rail is refreshed after a profile/allowlist change, but a press
        // in that gap must not open an app the kids profile no longer allows.
        if (kidsMode && !KidsMode.allowed(context, card.packageName)) {
            Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = discovery.contentIntent(card)
        if (intent != null) {
            val opened = runCatching { context.startActivity(intent) }
                .onFailure { Log.w(TAG, "deep link failed for ${card.packageName}", it) }
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
        val app = homeApps(items).firstOrNull { it.packageName == card.packageName }
        if (app != null) {
            startLaunch(app, cellCenter(focusIndex))
        } else {
            Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // TalkBack activates through semantics, not key events; run the same path a
    // short OK press would take on that cell.
    fun activate(index: Int) {
        if (launching != null || openFolder != null) return
        if (index !in items.indices) return
        focusIndex = index
        if (editing) {
            togglePickup()
            return
        }
        when (val item = items[index]) {
            is HomeItem.AppItem -> startLaunch(item.app, cellCenter(index))
            is HomeItem.FolderItem -> openFolderAt(index)
        }
    }

    fun updateFolderApps(folderId: String, apps: List<TvApp>) {
        val index = items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
        if (index < 0) return
        val current = items[index] as HomeItem.FolderItem
        val updated = current.copy(apps = apps)
        val list = items.toMutableList().also { it[index] = updated }
        items = list
        openFolder?.let { open ->
            if (open.folder.id == folderId) openFolder = open.copy(folder = updated)
        }
        onOrderChanged(list)
    }

    fun renameFolder(folderId: String, name: String) {
        val index = items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
        if (index < 0) return
        val current = items[index] as HomeItem.FolderItem
        if (current.name == name) return
        val updated = current.copy(name = name)
        val list = items.toMutableList().also { it[index] = updated }
        items = list
        openFolder?.let { open ->
            if (open.folder.id == folderId) openFolder = open.copy(folder = updated)
        }
        onOrderChanged(list)
    }

    fun applyEdit(edit: HomeEdit) {
        items = edit.items
        focusIndex = edit.focusIndex.coerceIn(0, max(edit.items.lastIndex, 0))
        onOrderChanged(edit.items)
    }

    fun removeFolderById(folderId: String) {
        applyEdit(removeFolder(items, folderId) ?: return)
    }

    // Android TV launchers let the user hide an app from the grid without
    // uninstalling it; the tile waits in the hidden folder.
    fun hideAppFromHome(app: TvApp) {
        applyEdit(
            hideApp(items, app.packageName, context.getString(R.string.hidden_folder_name))
                ?: return,
        )
    }

    // 05_FOLDERS §9: the app returns to the grid next to its folder; an emptied
    // folder disappears, which takeAppOutOfFolder handles by keeping the app.
    fun moveAppToHome(folderId: String, app: TvApp) {
        applyEdit(takeAppOutOfFolder(items, folderId, app.packageName) ?: return)
    }

    // "Move to Folder…" from the app menu: the tile leaves the grid and joins a
    // user folder. Hidden apps keep their home in the "More Apps" bucket, so the
    // picker never offers that one.
    fun moveAppIntoFolder(app: TvApp, folderId: String) {
        val from = items.indexOfFirst {
            it is HomeItem.AppItem && it.app.packageName == app.packageName
        }
        if (from < 0) return
        if (items.none { it is HomeItem.FolderItem && it.id == folderId }) return
        val list = items.toMutableList()
        list.removeAt(from)
        val target = list.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
        if (target < 0) return
        val folder = list[target] as HomeItem.FolderItem
        list[target] = folder.copy(apps = folder.apps + app)
        items = list
        focusIndex = target.coerceIn(0, max(list.lastIndex, 0))
        onOrderChanged(list)
    }

    // "New Folder…" wraps the app in a named folder that takes its slot, so the
    // rest of the grid does not shift; the Java launcher 1.1.17 made the same move.
    fun createFolderWithApp(name: String, app: TvApp) {
        val at = items.indexOfFirst {
            it is HomeItem.AppItem && it.app.packageName == app.packageName
        }
        if (at < 0) return
        val label = name.trim().ifEmpty { context.getString(R.string.folder_default) }
        val folder = HomeItem.FolderItem(UUID.randomUUID().toString(), label, listOf(app))
        val list = items.toMutableList().also { it[at] = folder }
        items = list
        focusIndex = at
        onOrderChanged(list)
    }

    // A package change rebuilds `items`: an overlay that kept the old folder would
    // show an app that no longer exists, and a drag would point past the list end.
    LaunchedEffect(initialItems) {
        holdJob?.cancel()
        holdJob = null
        editing = false
        pickupActive = false
        pickupOrigin = null
        pickupSnapshot = null
        pickupStart = -1
        mergeVictim = null
        confirmIndex = null
        longPressHandled = false
        openFolder = openFolder?.let { open ->
            val fresh = items.firstOrNull { it is HomeItem.FolderItem && it.id == open.folder.id }
            fresh?.let { open.copy(folder = it as HomeItem.FolderItem) }
        }
        // A menu whose tile vanished has no node left to dismiss it. Kept open,
        // it would gate the grid for TalkBack and swallow SEARCH with nothing
        // on screen to close.
        menuApp = menuApp?.takeIf { app ->
            items.any { it is HomeItem.AppItem && it.app.packageName == app.packageName }
        }
        menuFolderId = menuFolderId?.takeIf { id ->
            items.any { it is HomeItem.FolderItem && it.id == id }
        }
        folderDeleteId = folderDeleteId?.takeIf { id ->
            items.any { it is HomeItem.FolderItem && it.id == id }
        }
    }

    // 14_MOTION_DESIGN M8: the tile the drag pushed aside stays the folder
    // candidate while the drag rests on the new cell. Folding is an explicit
    // hold of OK (see the key handler); a drag alone never makes a folder.
    // The focus node sees BACK before the Activity dispatcher (see
    // FolderOverlay), so the grid is handled here as well.
    BackHandler(enabled = editing) {
        if (pickupActive) {
            val start = pickupStart
            pickupSnapshot?.let { items = it }
            if (start in items.indices) focusIndex = start
            endPickup()
        } else {
            editing = false
        }
    }

    // tvOS returns from the app grid to the dock on BACK; doing it through the
    // column keeps the same app under the cursor on the way up.
    BackHandler(
        enabled = libraryMode && !editing && openFolder == null && launching == null &&
            menuFolderId == null && folderDeleteId == null && menuApp == null &&
            moveToFolderApp == null && newFolderApp == null && !searchOpen &&
            !controlCenterOpen,
    ) {
        focusIndex = focusIndex % columns
    }

    val focusRequester = remember { FocusRequester() }
    // One real focus target per cell. TalkBack on a TV speaks the input focus,
    // and the custom engine alone never moves it; the engine still decides
    // where focus goes, this only mirrors that choice into Android's focus.
    val tileRequesters = remember(items) { mutableMapOf<String, FocusRequester>() }
    // The picked-up cell is drawn as the drag proxy, not as its own card, so it
    // needs its own target; focusing the grid root would make TalkBack read the
    // first tile instead of the one being moved.
    val pickupRequester = remember { FocusRequester() }
    val menuOpen = menuFolderId != null || folderDeleteId != null ||
        menuApp != null || moveToFolderApp != null
    // The Java launcher hid the pill over a folder, the library and every panel.
    val weatherVisible = weatherEnabled && weatherData != null && !libraryMode &&
        openFolder == null && launching == null && !menuOpen && !searchOpen &&
        !controlCenterOpen && !weatherPanelOpen && shelfFocus < 0 && !sectionFocused
    val gridModal = openFolder != null || launching != null || menuOpen ||
        newFolderApp != null || searchOpen || controlCenterOpen || weatherPanelOpen
    // Layout gate instead of a blind delay: once the grid is placed every tile's
    // FocusRequester is attached, so the cursor can move on the first frame.
    var gridPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(
        Unit,
        focusTick,
        gridModal,
        capsuleFocused,
        weatherFocused,
        focusIndex,
        items,
        editing,
        pickupActive,
        gridPlaced,
        shelfFocus,
        sectionFocused,
    ) {
        if (gridModal || !gridPlaced) return@LaunchedEffect
        // TopShelf owns the real target while the carousel or its section bar
        // has the cursor; the grid must not pull focus back underneath it.
        if (shelfFocus >= 0 || sectionFocused) return@LaunchedEffect
        // The pill sits outside the grid; while it owns the cursor the grid must
        // not take the focus back (the panel requests its own tile).
        if (capsuleFocused) {
            capsuleRequester.requestWhenAttached()
            return@LaunchedEffect
        }
        if (weatherFocused) {
            weatherRequester.requestWhenAttached()
            return@LaunchedEffect
        }
        // The proxy is the cell under the cursor while it is being moved, so it
        // carries the focus; the tiles the cursor passes over stay silent.
        if (pickupActive) {
            pickupRequester.requestWhenAttached()
            return@LaunchedEffect
        }
        val requester = items.getOrNull(focusIndex)?.let { tileRequesters[it.key] }
        val target = requester ?: focusRequester
        if (!target.requestWhenAttached()) return@LaunchedEffect
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
        target.requestWhenAttached()
    }

    // Weather turned off in Settings, or the pill left the screen: the cursor
    // must not sit on a node that no longer exists. While the forecast panel is
    // open the pill is hidden but keeps the return target for Back.
    LaunchedEffect(weatherVisible, weatherPanelOpen, weatherEnabled) {
        if (!weatherVisible && weatherFocused && !weatherPanelOpen) {
            weatherFocused = false
            weatherFromShelf = false
        }
        if (!weatherEnabled) {
            weatherPanelOpen = false
            weatherFromShelf = false
        }
    }

    val searchableApps = remember(items) { homeApps(items) }

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
                if (launching != null) return@onKeyEvent true
                // Search is a separate surface: anything the panel did not use
                // must not reach the grid behind it (a MENU press, for example).
                if (searchOpen) return@onKeyEvent true
                // The Control Center owns the remote while it is open; the
                // focused tile already consumed its own keys.
                if (controlCenterOpen) return@onKeyEvent true
                // A shelf card menu is its own focus surface.
                if (shelfMenu != null) return@onKeyEvent true
                // The forecast panel has its own focus and Back handler.
                if (weatherPanelOpen) return@onKeyEvent true
                // A context menu owns the remote. A hold that opened it keeps
                // sending repeats, and some remotes deliver each repeat as its
                // own press and release; those pairs must not reach the grid.
                if (menuOpen || newFolderApp != null) {
                    return@onKeyEvent true
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        // A held key used to repeat at the system rate: OK
                        // restarted the edit timer and never opened editing.
                        if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                            return@onKeyEvent true
                        }
                        // The weather pill mirrors the Java card: Ok opens the
                        // forecast, Right crosses to the status pill, Down/Back
                        // drop into the carousel or the grid, Up/Left stay put.
                        if (weatherFocused) {
                            val handled = when (event.key) {
                                Key.DirectionRight -> {
                                    val fromShelf = weatherFromShelf
                                    weatherFocused = false
                                    weatherFromShelf = false
                                    capsuleFromShelf = fromShelf
                                    capsuleFocused = true
                                    true
                                }

                                Key.DirectionDown, Key.Back -> {
                                    val fromShelf = weatherFromShelf
                                    weatherFocused = false
                                    weatherFromShelf = false
                                    if (fromShelf && shelfCards.isNotEmpty() && !editing) {
                                        shelfFocus = shelfSelected.coerceIn(0, shelfCards.lastIndex)
                                    }
                                    true
                                }

                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    okDown = false
                                    weatherPanelOpen = true
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
                            if (handled) return@onKeyEvent true
                        }
                        // The pill is the top of the screen: Ok opens the panel,
                        // Down/Back drop back in, Left reaches the weather card
                        // when one is shown, Right/Up have nowhere to go.
                        if (capsuleFocused) {
                            val handled = when (event.key) {
                                Key.DirectionDown, Key.Back -> {
                                    val fromShelf = capsuleFromShelf
                                    capsuleFocused = false
                                    capsuleFromShelf = false
                                    if (fromShelf && shelfCards.isNotEmpty() && !editing) {
                                        shelfFocus = shelfSelected.coerceIn(0, shelfCards.lastIndex)
                                    }
                                    true
                                }

                                Key.DirectionLeft -> {
                                    if (weatherVisible) {
                                        val fromShelf = capsuleFromShelf
                                        capsuleFocused = false
                                        capsuleFromShelf = false
                                        weatherFromShelf = fromShelf
                                        weatherFocused = true
                                        true
                                    } else {
                                        false
                                    }
                                }

                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    okDown = false
                                    controlCenterOpen = true
                                    true
                                }

                                // The pill is the top-right corner: Right/Up stay
                                // put, and Menu/Search are not dock actions.
                                Key.DirectionRight, Key.DirectionUp,
                                Key.Menu, Key.Search, Key.Assist,
                                -> true

                                else -> false
                            }
                            if (handled) return@onKeyEvent true
                        }
                        // The section bar sits above the rail: Left/Right
                        // switch the rail, Down/Back return to the cards.
                        if (sectionFocused && rails.size > 1 && !editing) {
                            val index = rails.indexOfFirst { it.section == currentSection }
                            val handled = when (event.key) {
                                Key.DirectionLeft -> {
                                    if (rails.isNotEmpty()) {
                                        val next = (index - 1 + rails.size) % rails.size
                                        selectDiscoverySection(rails[next].section)
                                    }
                                    true
                                }

                                Key.DirectionRight -> {
                                    if (rails.isNotEmpty()) {
                                        val next = (index + 1) % rails.size
                                        selectDiscoverySection(rails[next].section)
                                    }
                                    true
                                }

                                Key.DirectionDown, Key.Back,
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                                -> {
                                    sectionFocused = false
                                    shelfFocus = shelfSelected.coerceIn(
                                        0,
                                        shelfCards.lastIndex.coerceAtLeast(0),
                                    )
                                    true
                                }

                                Key.DirectionUp -> {
                                    sectionFocused = false
                                    focusCapsule(fromShelf = true)
                                    true
                                }

                                // Do not leak these into the dock branch below:
                                // the bar is not the app grid. SEARCH stays a
                                // grid/capsule action for now; MENU has no
                                // section-specific items.
                                Key.Menu, Key.Search, Key.Assist -> true

                                else -> false
                            }
                            if (handled) return@onKeyEvent true
                        }
                        // The rail owns the remote while a card is focused so
                        // the grid cursor underneath cannot move.
                        if (shelfFocus >= 0 && shelfCards.isNotEmpty() && !editing) {
                            val held = shelfCards.getOrNull(shelfFocus)
                            val handled = when (event.key) {
                                Key.DirectionLeft -> {
                                    if (shelfFocus > 0) {
                                        shelfFocus--
                                        shelfSelected = shelfFocus
                                    }
                                    true
                                }

                                Key.DirectionRight -> {
                                    if (shelfFocus < shelfCards.lastIndex) {
                                        shelfFocus++
                                        shelfSelected = shelfFocus
                                    }
                                    true
                                }

                                Key.DirectionDown, Key.Back -> {
                                    shelfFocus = -1
                                    sectionFocused = false
                                    true
                                }

                                Key.DirectionUp -> {
                                    // Up reaches the section bar when there is
                                    // one; otherwise it is the status pill.
                                    shelfFocus = -1
                                    if (rails.size > 1) {
                                        sectionFocused = true
                                    } else {
                                        focusCapsule(fromShelf = true)
                                    }
                                    true
                                }

                                // A long OK opens the card menu, as on a grid
                                // tile. A short OK is handled on KeyUp so the
                                // hold cannot activate the card as well.
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    okDown = true
                                    longPressHandled = false
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(MENU_HOLD_MS)
                                        longPressHandled = true
                                        okDown = false
                                        menuOpenedByHold = true
                                        shelfMenu = held
                                    }
                                    true
                                }

                                Key.Menu -> {
                                    holdJob?.cancel()
                                    holdJob = null
                                    okDown = false
                                    longPressHandled = true
                                    menuOpenedByHold = false
                                    shelfMenu = held
                                    true
                                }

                                else -> false
                            }
                            if (handled) return@onKeyEvent true
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                moveFocus(-1, 0)
                                keyRepeat.startRepeat(scope) { moveFocus(-1, 0) }
                                true
                            }

                            Key.DirectionRight -> {
                                moveFocus(1, 0)
                                keyRepeat.startRepeat(scope) { moveFocus(1, 0) }
                                true
                            }

                            Key.DirectionUp -> {
                                if (focusIndex < columns && !editing && !pickupActive) {
                                    if (rails.isNotEmpty()) {
                                        enterShelf()
                                    } else {
                                        // No recommendations: the row above the
                                        // dock is the status pill (Java focusTop).
                                        focusCapsule(fromShelf = false)
                                    }
                                } else {
                                    moveFocus(0, -1)
                                    keyRepeat.startRepeat(scope) { moveFocus(0, -1) }
                                }
                                true
                            }

                            Key.DirectionDown -> {
                                moveFocus(0, 1)
                                keyRepeat.startRepeat(scope) { moveFocus(0, 1) }
                                true
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                okDown = true
                                // The auto-repeat that follows a hold can arrive after the
                                // edit-mode timer fires, so only the first KeyDown may toggle.
                                if (editing && pickupActive) {
                                    // A short press drops the dragged tile where it
                                    // already sits; holding OK folds it with the
                                    // neighbour the drag pushed aside.
                                    longPressHandled = false
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(FOLDER_HOLD_MS)
                                        val victim = mergeVictim
                                        if (pickupActive && victim != null &&
                                            victim in items.indices
                                        ) {
                                            longPressHandled = true
                                            confirmIndex = victim
                                            delay(FOLDER_CONFIRM_MS)
                                            if (pickupActive) mergePickupInto(victim)
                                        }
                                    }
                                } else if (editing) {
                                    // tvOS lifts the icon and leaves it on the
                                    // cursor until the next click; the press is
                                    // consumed so KeyUp does not drop it again.
                                    togglePickup()
                                    okDown = false
                                } else if (!kidsMode) {
                                    // A long OK opens the context menu, as in the
                                    // Java build (showOptions on long click). The
                                    // edit mode lives inside that menu as "Move".
                                    longPressHandled = false
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(MENU_HOLD_MS)
                                        longPressHandled = true
                                        okDown = false
                                        confirmIndex = null
                                        menuOpenedByHold = true
                                        when (val focused = items.getOrNull(focusIndex)) {
                                            is HomeItem.AppItem -> menuApp = focused.app
                                            is HomeItem.FolderItem ->
                                                if (!isHiddenFolder(focused)) {
                                                    menuFolderId = focused.id
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
                                    // longPressHandled = true здесь, до KeyUp,
                                    // делала мёртвым любое OK в детском режиме.
                                    longPressHandled = false
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(MENU_HOLD_MS)
                                        longPressHandled = true
                                    }
                                }
                                true
                            }

                            // 09_SEARCH §3: search is its own surface, reached from a
                            // dedicated entry rather than from the grid's context menu.
                            // Android TV remotes send ASSIST where tvOS sends Search,
                            // and some keyboards only have the magnifier key.
                            Key.Search, Key.Assist -> {
                                if (!editing && openFolder == null && !menuOpen &&
                                    launching == null
                                ) {
                                    holdJob?.cancel()
                                    holdJob = null
                                    okDown = false
                                    longPressHandled = false
                                    confirmIndex = null
                                    searchOpen = true
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
                                if (!editing && !kidsMode) {
                                    holdJob?.cancel()
                                    holdJob = null
                                    okDown = false
                                    longPressHandled = true
                                    confirmIndex = null
                                    menuOpenedByHold = false
                                    when (val focused = items.getOrNull(focusIndex)) {
                                        is HomeItem.AppItem -> menuApp = focused.app
                                        // The hidden folder is not a user folder: no
                                        // rename, no delete, so it has no menu at all.
                                        is HomeItem.FolderItem -> if (!isHiddenFolder(focused)) {
                                            menuFolderId = focused.id
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
                                    openFolder != null || menuOpen -> Unit
                                    editing -> {
                                        if (pickupActive) {
                                            val start = pickupStart
                                            pickupSnapshot?.let { items = it }
                                            if (start in items.indices) focusIndex = start
                                            endPickup()
                                        } else {
                                            editing = false
                                        }
                                    }

                                    libraryMode -> focusIndex = focusIndex % columns
                                }
                                true
                            }

                            else -> false
                        }
                    }

                    KeyEventType.KeyUp -> when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            holdJob?.cancel()
                            holdJob = null
                            if (!editing && shelfFocus >= 0 && okDown && !longPressHandled) {
                                shelfCards.getOrNull(shelfFocus)?.let(::activateShelfCard)
                            } else if (editing && pickupActive) {
                                if (okDown && !longPressHandled) dropPickup()
                            } else if (!editing && okDown && !longPressHandled && shelfFocus < 0) {
                                when (val item = items.getOrNull(focusIndex)) {
                                    is HomeItem.AppItem -> startLaunch(item.app, cellCenter(focusIndex))
                                    is HomeItem.FolderItem -> openFolderAt(focusIndex)
                                    null -> Unit
                                }
                            }
                            okDown = false
                            longPressHandled = false
                            true
                        }

                        else -> false
                    }

                    else -> false
                }
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
                    if (openFolder != null || launching != null || menuOpen || searchOpen ||
                        controlCenterOpen || weatherPanelOpen || shelfMenu != null ||
                        playingTrailer != null
                    ) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        Modifier
                    },
                )
                .graphicsLayer {
                    val dockShift = if (focusIndex < columns) TvOs.DOCK_FOCUS_SHIFT else 0f
                    translationY = (-(scroll.value + dockShift) + hideShift.value) *
                        scale.factor * density
                },
        ) {
            // 03_FOCUS_ENGINE §9: dock focus lifts the grid and the band, but
            // the shelf stays put; the counter-shift cancels only that part.
            Box(
                Modifier.graphicsLayer {
                    val dockShift = if (focusIndex < columns) TvOs.DOCK_FOCUS_SHIFT else 0f
                    translationY = dockShift * scale.factor * density
                    alpha = libraryFade.value
                },
            ) {
                if (shelfVisible) {
                    TopShelf(
                        app = shelfApp,
                        rails = rails,
                        selectedSection = currentSection,
                        selectedCard = shelfSelected,
                        focusedCard = shelfFocus,
                        sectionFocused = sectionFocused,
                        onCardActivate = ::activateShelfCard,
                        onSectionActivate = ::selectDiscoverySection,
                        scale = scale,
                        reduceMotion = reduceMotion,
                        // The weather pill owns the top-left corner. On the
                        // browsing Home the title moves below it instead of
                        // printing through the forecast; inside the shelf the
                        // pill is hidden and the tvOS title position returns.
                        headingTop = if (weatherVisible) 150f else 57f,
                        focusTick = focusTick + shelfFocusTick,
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
                focused = focusIndex < columns && shelfFocus < 0 && !capsuleFocused &&
                    !weatherFocused,
                fade = { libraryFade.value },
            )

            // The library shift lives on the grid, not on every tile: reorder
            // animation below still works on item indices, and this layer moves
            // the dock row to the top while the shelf fades out.
            Box(
                Modifier
                    .fillMaxSize()
                    .onPlaced { gridPlaced = true }
                    .then(
                        // TalkBack follows the screen: while the carousel is in
                        // charge the tiles are not on it, so they stop being
                        // focus targets behind the poster.
                        if (shelfFocus >= 0) Modifier.clearAndSetSemantics {} else Modifier,
                    )
                    .graphicsLayer {
                        translationY = (-libraryShift.value + shelfHide) *
                            scale.factor * density
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
                            onActivate = { activate(index) },
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
                            onActivate = { activate(index) },
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

        openFolder?.let { open ->
            FolderOverlay(
                folder = open.folder,
                tileCenter = Offset(open.tileX, open.tileY),
                scale = scale,
                launching = launching != null,
                initialFocusKey = folderFocus[open.folder.id],
                onFocusKeyChanged = { key ->
                    if (key == null) {
                        folderFocus.remove(open.folder.id)
                    } else {
                        folderFocus[open.folder.id] = key
                    }
                },
                onLaunch = { app, center -> startLaunch(app, center) },
                onClosing = { folderClosing = true },
                onClose = {
                    openFolder = null
                    folderClosing = false
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

        menuFolderId?.let { folderId ->
            val index = items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
            val folder = items.getOrNull(index) as? HomeItem.FolderItem
            if (folder != null) {
                TvMenu(
                    title = folder.name,
                    entries = listOf(
                        TvMenuEntry(context.getString(R.string.menu_open)) {
                            openFolderAt(index)
                        },
                        TvMenuEntry(context.getString(R.string.menu_move)) {
                            focusIndex = index
                            editing = true
                            mergeVictim = null
                            confirmIndex = null
                            togglePickup()
                        },
                        TvMenuEntry(
                            label = context.getString(R.string.menu_delete_folder),
                            destructive = true,
                        ) {
                            // The menu already animated out; ask once more
                            // before the layout loses the folder.
                            menuFolderId = null
                            folderDeleteId = folderId
                        },
                    ),
                    anchor = cellCenter(index),
                    scale = scale,
                    suppressOkUntilRelease = menuOpenedByHold,
                    onDismiss = {
                        menuFolderId = null
                        menuOpenedByHold = false
                    },
                )
            }
        }

        // Destructive confirmation for a folder. It is a second menu, not a
        // silent delete: the apps return home, but the user still chooses.
        folderDeleteId?.let { folderId ->
            val index = items.indexOfFirst { it is HomeItem.FolderItem && it.id == folderId }
            val folder = items.getOrNull(index) as? HomeItem.FolderItem
            if (folder != null) {
                TvMenu(
                    title = folder.name,
                    entries = listOf(
                        TvMenuEntry(
                            label = context.getString(R.string.menu_delete_folder),
                            destructive = true,
                        ) {
                            removeFolderById(folderId)
                            folderDeleteId = null
                        },
                        TvMenuEntry(context.getString(R.string.settings_cancel)) {
                            folderDeleteId = null
                        },
                    ),
                    anchor = cellCenter(index),
                    scale = scale,
                    onDismiss = { folderDeleteId = null },
                )
            }
        }

        menuApp?.let { app ->
            val index = items.indexOfFirst {
                it is HomeItem.AppItem && it.app.packageName == app.packageName
            }
            if (index >= 0) {
                // The own settings tile is the launcher's screen: hiding or
                // uninstalling it would remove the way into Settings.
                val own = app.packageName == context.packageName
                val entries = buildList {
                    // A package that was restored but is not installed here has
                    // no activity to open, inspect or uninstall. Moving and
                    // hiding it are still meaningful.
                    if (app.available) {
                        add(
                            TvMenuEntry(context.getString(R.string.menu_open)) {
                                startLaunch(app, cellCenter(index))
                            },
                        )
                    }
                    add(
                        TvMenuEntry(context.getString(R.string.menu_move)) {
                            focusIndex = index
                            editing = true
                            mergeVictim = null
                            confirmIndex = null
                            togglePickup()
                        },
                    )
                    add(
                        TvMenuEntry(context.getString(R.string.menu_move_to_folder)) {
                            moveToFolderApp = app
                        },
                    )
                    if (app.available && !own) {
                        add(
                            TvMenuEntry(context.getString(R.string.menu_hide)) {
                                hideAppFromHome(app)
                            },
                        )
                    }
                    if (app.available) {
                        add(
                            TvMenuEntry(context.getString(R.string.menu_app_info)) {
                                onAppInfo(app)
                            },
                        )
                    }
                    if (app.available && !own && !app.system) {
                        add(
                            TvMenuEntry(
                                label = context.getString(R.string.menu_uninstall),
                                destructive = true,
                            ) {
                                onUninstall(app)
                            },
                        )
                    }
                }
                TvMenu(
                    title = app.label,
                    entries = entries,
                    anchor = cellCenter(index),
                    scale = scale,
                    suppressOkUntilRelease = menuOpenedByHold,
                    onDismiss = {
                        menuApp = null
                        menuOpenedByHold = false
                    },
                )
            }
        }

        // A shelf card is a real content offer: open it, keep it, or hide it.
        // The menu is the only place favorites are added, so a recommendation
        // can never quietly become "personalized" without the viewer's action.
        shelfMenu?.let { item ->
            val favorite = discovery.isFavorite(item.id)
            TvMenu(
                title = item.title,
                entries = buildList {
                    add(
                        TvMenuEntry(context.getString(R.string.menu_open)) {
                            closeShelfMenu()
                            activateShelfCard(item)
                        },
                    )
                    add(
                        TvMenuEntry(
                            context.getString(
                                if (favorite) {
                                    R.string.menu_remove_favorite
                                } else {
                                    R.string.menu_add_favorite
                                },
                            ),
                        ) {
                            if (favorite) {
                                discovery.removeFavorite(item.id)
                            } else {
                                discovery.addFavorite(
                                    item.copy(reason = DiscoveryReason.FAVORITE),
                                )
                            }
                            closeShelfMenu()
                            discoveryRevision++
                        },
                    )
                    add(
                        TvMenuEntry(
                            label = context.getString(R.string.menu_hide),
                            destructive = true,
                        ) {
                            discovery.hide(item)
                            closeShelfMenu()
                            discoveryRevision++
                        },
                    )
                },
                anchor = shelfCardAnchor(),
                scale = scale,
                suppressOkUntilRelease = menuOpenedByHold,
                onDismiss = ::closeShelfMenu,
            )
        }

        // 05_FOLDERS: the app menu's "Move to Folder…" lists the user folders and
        // ends with "New Folder…", as the Java launcher did — a folder made here
        // is named first and takes the app with it.
        moveToFolderApp?.let { app ->
            val folders = items.filterIsInstance<HomeItem.FolderItem>()
                .filterNot { isHiddenFolder(it) }
            val tileIndex = items.indexOfFirst {
                it is HomeItem.AppItem && it.app.packageName == app.packageName
            }
            TvMenu(
                title = context.getString(R.string.move_to_folder_title),
                entries = buildList {
                    folders.forEach { folder ->
                        add(
                            TvMenuEntry(folder.name) {
                                moveAppIntoFolder(app, folder.id)
                            },
                        )
                    }
                    add(
                        TvMenuEntry(context.getString(R.string.new_folder_title)) {
                            newFolderApp = app
                        },
                    )
                },
                anchor = cellCenter(if (tileIndex >= 0) tileIndex else focusIndex),
                scale = scale,
                onDismiss = { moveToFolderApp = null },
            )
        }

        newFolderApp?.let { app ->
            RenameKeyboardOverlay(
                title = context.getString(R.string.new_folder_title),
                initial = "",
                scale = scale,
                onDone = { name ->
                    newFolderApp = null
                    createFolderWithApp(name, app)
                },
                onCancel = { newFolderApp = null },
                placeholder = context.getString(R.string.keyboard_placeholder),
                note = context.getString(R.string.new_folder_note),
            )
        }

        if (searchOpen) {
            SearchOverlay(
                apps = searchableApps,
                scale = scale,
                reduceMotion = reduceMotion,
                onClose = {
                    searchOpen = false
                    if (shelfFocus >= 0) shelfFocusTick++
                },
                // A failed start shows its own toast; the panel stays so the
                // query and the chosen app are not lost.
                onLaunch = { app -> if (onLaunch(app)) searchOpen = false },
            )
        }

        // 07_CONTROL_CENTER: the panel is a separate overlay with its own focus
        // and Back closes it. Back fades out and hands the focus back to the
        // pill; a tile that leaves for another screen does not (so returning
        // from that screen does not reopen the panel).
        if (controlCenterOpen) {
            ControlCenter(
                scale = scale,
                reduceMotion = reduceMotion,
                libraryAvailable = items.size > columns,
                onClose = { returnFocus ->
                    controlCenterOpen = false
                    if (returnFocus) {
                        capsuleFocused = true
                    } else {
                        capsuleFocused = false
                        capsuleFromShelf = false
                    }
                },
                onStartSaver = onStartSaver,
                onOpenLibrary = { focusIndex = columns },
                onOpenSearch = { searchOpen = true },
                onOpenSettings = {
                    if (onOpenSettings != null) {
                        onOpenSettings()
                    } else {
                        runCatching {
                            context.startActivity(
                                Intent(context, SettingsActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure { Log.w(TAG, "cannot open SettingsActivity", it) }
                    }
                },
                onOpenSystem = onOpenSystem,
            )
        }

        if (weatherPanelOpen) {
            WeatherPanel(
                scale = scale,
                data = weatherData,
                stale = weatherStale,
                reduceMotion = reduceMotion,
                onClose = {
                    weatherPanelOpen = false
                    // The Java popover returned to the weather card.
                    weatherFocused = true
                },
            )
        }

        // Home chrome: the clock and the live connection in one glass pill, the
        // same object that grows into the Control Center band. A panel, the
        // menu, search or the launch zoom covers the screen and hides it.
        weatherData?.takeIf { weatherVisible }?.let { data ->
            WeatherCapsule(
                scale = scale,
                data = data,
                stale = weatherStale,
                manual = WeatherPreferences.manual(context),
                focused = weatherFocused,
                focusRequester = weatherRequester,
                onActivate = {
                    weatherFromShelf = false
                    weatherFocused = true
                    weatherPanelOpen = true
                },
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
                profileEmoji = activeProfile?.emoji?.takeIf { it.isNotBlank() },
                profileColor = profileColor,
                profileName = activeProfile?.name,
                onActivate = {
                    // TalkBack has no key event: take the pill and open the
                    // panel the same way OK does.
                    capsuleFromShelf = false
                    capsuleFocused = true
                    controlCenterOpen = true
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = -scale.dp(CC_CAPSULE_SIDE),
                        y = scale.dp(CC_CAPSULE_TOP),
                    ),
            )
        }

        playingTrailer?.let { trailer ->
            TrailerOverlay(
                title = trailer.title,
                url = trailer.url,
                onDismiss = {
                    playingTrailer = null
                    // Closing the preview returns to the card that opened it,
                    // not to the dock: the modal effect lowered the shelf while
                    // the preview played, so put its cursor back before asking
                    // TopShelf for Android focus again.
                    if (shelfCards.isNotEmpty()) {
                        shelfFocus = shelfSelected.coerceIn(0, shelfCards.lastIndex)
                    }
                    shelfFocusTick++
                },
            )
        }

        // Drawn last: the zoom also covers the folder panel it starts from (T5).
        launching?.let { active ->
            LaunchOverlay(
                app = active.app,
                cellCenter = active.cellCenter,
                progress = { launchProgress.value },
                scale = scale,
            )
        }
    }
}

/**
 * App name under a tile. tvOS puts the label on the home surface, not inside
 * the artwork; keeping it a sibling avoids shrinking the 250×150 icon and
 * leaves the TalkBack node on [com.whatuneed.ovisiontv.kotlin.ui.components.CardShell].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TileLabel(text: String, x: Float, y: Float, scale: TvScale) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.86f),
        fontSize = scale.sp(TvType.callout),
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .offset(x = scale.dp(x), y = scale.dp(y))
            .width(scale.dp(TvOs.ICON_WIDTH))
            // The card already announces this label; keep the visible line out
            // of TalkBack while leaving it inspectable for layout tests.
            .semantics { invisibleToUser() }
            .testTag("tile_label"),
    )
}

/**
 * Section title inside the full-screen grid. It shares the grid's shifted layer
 * so it scrolls with the row it names, and fades with the shelf.
 */
@Composable
private fun LibraryHeading(
    text: String,
    y: Float,
    fade: Animatable<Float, AnimationVector1D>,
    scale: TvScale,
) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = text.uppercase(locale),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = scale.sp(TvType.callout),
        letterSpacing = (21f * scale.factor * 0.08f).sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .offset(x = scale.dp(TvOs.SAFE_AREA), y = scale.dp(y))
            .graphicsLayer { alpha = 1f - fade.value },
    )
}

/**
 * The library is a flat dark surface. The blurred app banner it used to show
 * cost a full-screen RenderEffect on every frame; without it the screen is one
 * static gradient, which is what tvOS 27 shows behind the app grid.
 */
@Composable
private fun LibraryBackdrop(alpha: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .background(
                Brush.verticalGradient(
                    listOf(TvInk.dockTop, TvInk.dockBottom),
                ),
            ),
    )
}

@Composable
private fun DockBand(
    scale: TvScale,
    reduceMotion: Boolean,
    scroll: Animatable<Float, AnimationVector1D>,
    shift: () -> Float,
    focused: Boolean,
    fade: () -> Float,
) {
    val shape = RoundedCornerShape(scale.dp(TvOs.DOCK_RADIUS))
    // 13_VISUAL_DESIGN_SYSTEM dock.focusGrowth: the band widens by 11 pt on
    // each side while the first row holds focus. Duration is UNKNOWN; it uses
    // the shared focus duration so the band and the tile grow together.
    val grow by animateFloatAsState(
        targetValue = if (focused) TvOs.DOCK_FOCUS_GROWTH else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "dockFocusGrowth",
    )
    Box(
        Modifier
            .offset(
                x = scale.dp(TvOs.DOCK_X - grow),
                y = scale.dp(TvOs.DOCK_Y),
            )
            .size(scale.dp(TvOs.DOCK_WIDTH + grow * 2), scale.dp(TvOs.DOCK_HEIGHT))
            // tvOS draws the dock band only while the first row is at dock
            // position (dockMode == YES), so it leaves as soon as the grid
            // scrolls; in the library the favourites row is an ordinary row.
            // The shift and fades are read in the layer, so the library
            // animation does not recompose the band on every frame.
            .graphicsLayer {
                translationY = -shift() * scale.factor * density
                alpha = (1f - scroll.value / TvOs.ROW_PITCH).coerceIn(0f, 1f) * fade()
            }
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.045f)),
                ),
                shape = shape,
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f)),
                ),
                shape = shape,
            ),
    )
}

fun launchApp(context: Context, app: TvApp): Boolean {
    if (!app.available) {
        Toast.makeText(context, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        return false
    }
    val intent = Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(app.packageName, app.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (error: Exception) {
        // The toast is for the viewer; the log keeps the reason diagnosable
        // without recording anything about the person (ST-22).
        Log.w(TAG, "cannot launch ${app.packageName}", error)
        Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
        false
    }
}
