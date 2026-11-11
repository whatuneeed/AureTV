package com.whatuneed.ovisiontv.kotlin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.HomeItem
import com.whatuneed.ovisiontv.kotlin.data.TvApp
import com.whatuneed.ovisiontv.kotlin.data.isHiddenFolder
import com.whatuneed.ovisiontv.kotlin.ui.components.AppCard
import com.whatuneed.ovisiontv.kotlin.ui.components.RenameKeyboardOverlay
import com.whatuneed.ovisiontv.kotlin.ui.components.TvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.ui.components.TvMenu
import com.whatuneed.ovisiontv.kotlin.ui.components.TvMenuEntry
import com.whatuneed.ovisiontv.kotlin.ui.components.rememberTvKeyRepeat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

// 05_FOLDERS §2–§3: panel frame and content insets measured on tvOS 27.
private const val PANEL_X = 355f
private const val PANEL_Y = 150f
private const val PANEL_WIDTH = 1210f
private const val PANEL_HEIGHT = 770f
private const val PANEL_RADIUS = TvRadius.panelFolder

private const val CONTENT_LEFT = 80f
private const val CONTENT_WIDTH = 1050f
private const val CONTENT_AREA_TOP = 52f
private const val CONTENT_AREA_HEIGHT = 666f
private const val PAGE_TOP = 72f
// 05_FOLDERS §3: interaction margin −50 exists so the focused cell is not clipped.
private const val CONTENT_MARGIN = 50f
private const val PAGE_STRIDE = CONTENT_WIDTH + 150f
private const val COLUMN_PITCH = 400f
private const val ROW_PITCH = 238f
private const val COLUMNS = 3
private const val PAGE_SIZE = 9

// 14_MOTION_DESIGN M9–M11: 0.5 s zoom with cubic-bezier(0.3825, 0.091, 0.98, 0.785).
private const val OPEN_MS = 500

// 05_FOLDERS §10: the page-change animation was never captured; the reference
// Compose prototype used 320 ms, which is what we keep here.
private const val PAGE_MS = 320

// The same hold threshold the grid uses for its context menu
// (HomeScreen.MENU_HOLD_MS).
private const val EDIT_HOLD_MS = 350L

@Composable
fun FolderOverlay(
    folder: HomeItem.FolderItem,
    tileCenter: Offset,
    scale: TvScale,
    launching: Boolean,
    // The grid remembers where the panel was left, so a reopened folder lands
    // on the same cell instead of the first app (UX-09).
    initialFocusIndex: Int = 0,
    onFocusIndexChanged: (Int) -> Unit = {},
    onLaunch: (TvApp, Offset) -> Unit,
    onClosing: () -> Unit,
    onClose: () -> Unit,
    onAppsChanged: (List<TvApp>) -> Unit,
    onRename: (String) -> Unit,
    onMoveToHome: (TvApp) -> Unit,
    onAppInfo: (TvApp) -> Unit = {},
    onUninstall: (TvApp) -> Unit = {},
    reduceMotion: Boolean = false,
    // A kids profile opens folders read-only: no menu, no rename, no edit.
    kidsMode: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val progress = remember(folder.id) { Animatable(0f) }
    var closing by remember(folder.id) { mutableStateOf(false) }
    var focusIndex by remember(folder.id) {
        mutableIntStateOf(initialFocusIndex.coerceIn(0, (folder.apps.size - 1).coerceAtLeast(0)))
    }
    val focusRequester = remember { FocusRequester() }

    var editing by remember(folder.id) { mutableStateOf(false) }
    var pickupActive by remember(folder.id) { mutableStateOf(false) }
    var pickupOrigin by remember(folder.id) { mutableStateOf<Int?>(null) }
    var holdJob by remember(folder.id) { mutableStateOf<Job?>(null) }
    var longPressHandled by remember(folder.id) { mutableStateOf(false) }

    // 05_FOLDERS §7: the panel title is a text field plus a rename button in tvOS.
    // Here the title itself is the control: Up from the first row focuses it and OK
    // opens the keyboard. The title row geometry is a project decision.
    var titleFocused by remember(folder.id) { mutableStateOf(false) }
    var renameOpen by remember(folder.id) { mutableStateOf(false) }

    // 05_FOLDERS §9: an app can leave the panel for Home. tvOS drags it out;
    // with a remote the MENU key carries the action, as in the previous build.
    var menuApp by remember(folder.id) { mutableStateOf<TvApp?>(null) }
    // Same guard as Home: a menu that appeared under a held OK must not take
    // the auto-repeat as a selection.
    var menuOpenedByHold by remember(folder.id) { mutableStateOf(false) }
    // The hidden folder is created by the launcher, not by the user: its name
    // is fixed and renaming it would rename the bucket, not a choice. The kids
    // profile gets the same read-only title: rename and edit are parent tools.
    val titleLocked = isHiddenFolder(folder) || kidsMode
    // OK only launches when the panel saw the press, so the release of the key
    // that closed the menu cannot start the app under it.
    var okDown by remember(folder.id) { mutableStateOf(false) }
    val keyRepeat = rememberTvKeyRepeat()

    fun close() {
        if (closing) return
        closing = true
        // T4 is one 0.5 s transition: the hidden grid starts returning now,
        // together with the panel, not after the panel has finished.
        onClosing()
        scope.launch {
            progress.animateTo(
                0f,
                tween(motionDuration(OPEN_MS, reduceMotion), easing = TravelEasing),
            )
            onClose()
        }
    }

    // 05_FOLDERS §9: editing inside the panel reuses the grid rules — jiggle,
    // pickup cell and the same 350 ms hold threshold.
    fun dropPickup() {
        val origin = pickupOrigin
        if (origin != null && origin != focusIndex &&
            origin in folder.apps.indices && focusIndex in folder.apps.indices
        ) {
            val list = folder.apps.toMutableList()
            val moving = list.removeAt(origin)
            list.add(focusIndex.coerceIn(0, list.size), moving)
            onAppsChanged(list)
        }
        pickupActive = false
        pickupOrigin = null
    }

    fun togglePickup() {
        if (pickupActive) {
            dropPickup()
        } else {
            pickupActive = true
            pickupOrigin = focusIndex
        }
    }

    fun cancelPickup() {
        pickupOrigin?.let { focusIndex = it }
        pickupActive = false
        pickupOrigin = null
    }

    fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
        // The release must not launch the cell that receives focus next.
        longPressHandled = true
    }

    LaunchedEffect(folder.apps.size) {
        if (focusIndex > folder.apps.lastIndex) {
            focusIndex = folder.apps.lastIndex.coerceAtLeast(0)
        }
    }

    // Mirrored outward on every move: the grid's map is what survives the
    // close animation, so it must follow the cursor, not the close.
    LaunchedEffect(focusIndex) { onFocusIndexChanged(focusIndex) }

    LaunchedEffect(folder.id) {
        focusRequester.requestFocus()
        progress.animateTo(
            1f,
            tween(motionDuration(OPEN_MS, reduceMotion), easing = TravelEasing),
        )
    }

    // The panel paints its own cursor; TalkBack on a TV speaks the node that
    // holds Android's input focus, so the cursor is mirrored onto real targets.
    val titleRequester = remember(folder.id) { FocusRequester() }
    val tileRequesters = remember(folder.id) { mutableMapOf<Int, FocusRequester>() }
    // Same as Home: the picked tile is drawn as the drag proxy, which carries
    // its own focus target so TalkBack reads the tile being moved.
    val pickupRequester = remember(folder.id) { FocusRequester() }
    LaunchedEffect(
        focusIndex,
        titleFocused,
        pickupActive,
        renameOpen,
        menuApp,
        launching,
        folder.apps.size,
    ) {
        // The rename keyboard and the menu are modal: their own requesters own
        // the focus until they close, and this effect then runs again.
        if (renameOpen || menuApp != null || launching) return@LaunchedEffect
        delay(60)
        when {
            // The proxy is the tile under the cursor while it is being moved;
            // neighbouring tiles stay silent.
            pickupActive -> pickupRequester.requestFocus()
            titleFocused -> titleRequester.requestFocus()
            else -> tileRequesters[focusIndex]?.requestFocus() ?: focusRequester.requestFocus()
        }
    }

    BackHandler(enabled = !launching) {
        cancelHold()
        when {
            pickupActive -> cancelPickup()
            editing -> editing = false
            else -> close()
        }
    }

    val count = folder.apps.size
    val pageCount = ((count + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val page = focusIndex / PAGE_SIZE
    val pageOffset by animateFloatAsState(
        targetValue = page * PAGE_STRIDE,
        animationSpec = tween(motionDuration(PAGE_MS, reduceMotion)),
        label = "folderPage",
    )

    fun move(dx: Int, dy: Int) {
        if (dx != 0 || dy != 0) cancelHold()
        val pageStart = (focusIndex / PAGE_SIZE) * PAGE_SIZE
        val pageEnd = min(pageStart + PAGE_SIZE, count) - 1
        val position = focusIndex - pageStart
        val column = position % COLUMNS
        val row = position / COLUMNS
        focusIndex = when {
            // Left/Right stay inside the row; only the end of a page steps
            // to the neighbouring page, as the tvOS grid does not wrap.
            dx > 0 -> when {
                column < COLUMNS - 1 && focusIndex + 1 <= pageEnd -> focusIndex + 1
                focusIndex == pageEnd && focusIndex + 1 < count -> focusIndex + 1
                else -> focusIndex
            }

            dx < 0 -> when {
                column > 0 -> focusIndex - 1
                focusIndex == pageStart && focusIndex > 0 -> focusIndex - 1
                else -> focusIndex
            }

            dy < 0 -> (focusIndex - COLUMNS).takeIf { row > 0 } ?: focusIndex
            dy > 0 -> (focusIndex + COLUMNS).takeIf { focusIndex + COLUMNS <= pageEnd } ?: focusIndex
            else -> focusIndex
        }
    }

    fun moveUp() {
        if (!titleLocked && !titleFocused && !editing && !pickupActive &&
            focusIndex % PAGE_SIZE / COLUMNS == 0
        ) {
            cancelHold()
            titleFocused = true
        } else if (!titleFocused) {
            move(0, -1)
        }
    }

    fun moveDown() {
        if (titleFocused) titleFocused = false else move(0, 1)
    }

    fun moveLeft() {
        if (!titleFocused) move(-1, 0)
    }

    fun moveRight() {
        if (!titleFocused) move(1, 0)
    }

    val p = progress.value
    val panelCenterX = PANEL_X + PANEL_WIDTH / 2f
    val panelCenterY = PANEL_Y + PANEL_HEIGHT / 2f
    val contentAlpha = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)

    // T5 zooms out of the icon inside the panel; the panel stays behind the
    // zoom and is still open when the app returns (T6).
    fun cellCenter(index: Int): Offset {
        val pageIndex = index / PAGE_SIZE
        val withinPage = index % PAGE_SIZE
        val column = withinPage % COLUMNS
        val row = withinPage / COLUMNS
        return Offset(
            PANEL_X + CONTENT_LEFT + pageIndex * PAGE_STRIDE + column * COLUMN_PITCH -
                pageOffset + TvOs.ICON_WIDTH / 2f,
            PANEL_Y + PAGE_TOP + row * ROW_PITCH + TvOs.ICON_HEIGHT / 2f,
        )
    }

    // TalkBack's double-tap bypasses the key handler, so it gets the same
    // short-press path as OK on the focused cell.
    fun activate(index: Int) {
        if (closing || launching) return
        titleFocused = false
        focusIndex = index
        if (editing) {
            togglePickup()
            return
        }
        folder.apps.getOrNull(index)?.let { onLaunch(it, cellCenter(index)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            // Preview sees the release even when the menu consumes it, so the
            // next press of that key is not read as a repeat of the old one.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) keyRepeat.release(event.key)
                false
            }
            .onKeyEvent { event ->
                // Keys during the 0.5 s close or the launch zoom would otherwise
                // launch a second app or edit a panel that is already leaving.
                if (closing || launching) return@onKeyEvent true
                // The context menu owns the remote while it is up: the hold that
                // opened it may keep repeating, and a press/release pair would
                // otherwise land on the panel behind the panel.
                if (menuApp != null) return@onKeyEvent true
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        // One action per physical press: a held OK opens editing
                        // instead of restarting its timer on every repeat.
                        if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                            return@onKeyEvent true
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                moveLeft()
                                keyRepeat.startRepeat(scope, ::moveLeft)
                                true
                            }

                            Key.DirectionRight -> {
                                moveRight()
                                keyRepeat.startRepeat(scope, ::moveRight)
                                true
                            }

                            Key.DirectionUp -> {
                                moveUp()
                                keyRepeat.startRepeat(scope, ::moveUp)
                                true
                            }

                            Key.DirectionDown -> {
                                moveDown()
                                keyRepeat.startRepeat(scope, ::moveDown)
                                true
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                okDown = true
                                if (titleFocused) {
                                    renameOpen = true
                                } else if (editing) {
                                    togglePickup()
                                } else if (!kidsMode) {
                                    // A long OK opens the context menu, as on
                                    // Home and in the Java build; "Move" in that
                                    // menu enters the edit mode.
                                    longPressHandled = false
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(EDIT_HOLD_MS)
                                        longPressHandled = true
                                        okDown = false
                                        titleFocused = false
                                        menuOpenedByHold = true
                                        menuApp = folder.apps.getOrNull(focusIndex)
                                    }
                                } else {
                                    // Kids mode has no menus; the hold is only
                                    // consumed after the same delay, so a short
                                    // press still launches the app.
                                    longPressHandled = false
                                    holdJob?.cancel()
                                    holdJob = scope.launch {
                                        delay(EDIT_HOLD_MS)
                                        longPressHandled = true
                                    }
                                }
                                true
                            }

                            Key.Menu -> {
                                // A running hold timer must not take over behind it.
                                if (!kidsMode && !editing && !titleFocused && !pickupActive) {
                                    cancelHold()
                                    okDown = false
                                    menuOpenedByHold = false
                                    menuApp = folder.apps.getOrNull(focusIndex)
                                }
                                true
                            }

                            // Claimed here as well as in BackHandler: on a TV remote the
                            // focus node sees BACK before the activity dispatcher does.
                            Key.Back -> {
                                cancelHold()
                                when {
                                    titleFocused -> titleFocused = false
                                    pickupActive -> cancelPickup()
                                    editing -> editing = false
                                    else -> close()
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
                            if (!editing && !titleFocused && okDown && !longPressHandled) {
                                val app = folder.apps.getOrNull(focusIndex)
                                if (app != null) onLaunch(app, cellCenter(focusIndex))
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
        // tvOS uses a UIVisualEffectView here (13_VISUAL_DESIGN_SYSTEM §8); Compose
        // cannot blur the content behind an overlay, so the backdrop is a scrim.
        Box(Modifier.fillMaxSize().background(TvInk.scrimFolder.copy(alpha = TvInk.scrimFolder.alpha * p)))

        Box(
            Modifier
                .offset(x = scale.dp(PANEL_X), y = scale.dp(PANEL_Y))
                .size(scale.dp(PANEL_WIDTH), scale.dp(PANEL_HEIGHT))
                .graphicsLayer {
                    scaleX = TvOs.ICON_WIDTH / PANEL_WIDTH + (1f - TvOs.ICON_WIDTH / PANEL_WIDTH) * p
                    scaleY = TvOs.ICON_HEIGHT / PANEL_HEIGHT +
                        (1f - TvOs.ICON_HEIGHT / PANEL_HEIGHT) * p
                    translationX = (tileCenter.x - panelCenterX) * (1f - p) * scale.factor * density
                    translationY = (tileCenter.y - panelCenterY) * (1f - p) * scale.factor * density
                }
                .clip(RoundedCornerShape(scale.dp(PANEL_RADIUS)))
                .background(TvInk.panel.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.02f)),
                    ),
                    shape = RoundedCornerShape(scale.dp(PANEL_RADIUS)),
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // The menu is modal: the panel behind it recedes, so its title
                    // cannot peek out from under the menu's own edge.
                    .graphicsLayer { alpha = contentAlpha * if (menuApp != null) 0.35f else 1f }
                    .then(
                        // The rename keyboard is modal as well: with the panel
                        // still exposed, TalkBack could launch a cell the keyboard
                        // is covering.
                        if (menuApp != null || renameOpen) {
                            Modifier.clearAndSetSemantics {}
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val titleScale by animateFloatAsState(
                    targetValue = if (titleFocused) 1.03f else 1f,
                    animationSpec = tween(TvOs.FOCUS_MS),
                    label = "folderTitle",
                )
                val titleFocusAmount by animateFloatAsState(
                    targetValue = if (titleFocused) 1f else 0f,
                    animationSpec = tween(TvOs.FOCUS_MS),
                    label = "folderTitleFill",
                )
                val titleShape = RoundedCornerShape(scale.dp(TvRadius.field))
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = scale.dp(6f))
                        .size(scale.dp(900f), scale.dp(48f))
                        .then(
                            // The locked title is a label, not a control the
                            // engine can land on, so it needs no focus target.
                            if (titleLocked) {
                                Modifier
                            } else {
                                Modifier.focusRequester(titleRequester).focusable()
                            },
                        )
                        .clearAndSetSemantics {
                            contentDescription = folder.name
                            // The hidden folder keeps its fixed name, so its title
                            // is a label, not a button that promises a rename.
                            if (!titleLocked) {
                                role = Role.Button
                                // The key handler is blocked during the close and the
                                // launch zoom; a TalkBack double-tap must obey the same
                                // rule instead of opening the keyboard over a leaving panel.
                                onClick(label = folder.name) {
                                    if (!closing && !launching) {
                                        titleFocused = false
                                        renameOpen = true
                                    }
                                    true
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = titleScale
                            scaleY = titleScale
                            // The menu covers the row above the grid; a title half
                            // under it would read as a stray fragment.
                            alpha = if (menuApp != null) 0f else 1f
                        }
                        .background(
                            Color.White.copy(alpha = 0.18f * titleFocusAmount),
                            titleShape,
                        )
                        .border(
                            scale.dp(TvFocus.CHIP_STROKE),
                            Color.White.copy(alpha = TvFocus.CHIP_ALPHA * titleFocusAmount),
                            titleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = folder.name,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = scale.sp(TvType.subhead),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }

                Box(
                    Modifier
                        .offset(
                            x = scale.dp(CONTENT_LEFT - CONTENT_MARGIN),
                            y = scale.dp(CONTENT_AREA_TOP),
                        )
                        .size(
                            scale.dp(CONTENT_WIDTH + CONTENT_MARGIN * 2),
                            scale.dp(CONTENT_AREA_HEIGHT),
                        )
                        .clipToBounds(),
                ) {
                    folder.apps.forEachIndexed { index, app ->
                        if (pickupActive && index == pickupOrigin) return@forEachIndexed
                        val pageIndex = index / PAGE_SIZE
                        val withinPage = index % PAGE_SIZE
                        val column = withinPage % COLUMNS
                        val row = withinPage / COLUMNS
                        val tileRequester = remember(index) { FocusRequester() }
                        tileRequesters[index] = tileRequester
                        AppCard(
                            app = app,
                            focused = !pickupActive && !titleFocused && index == focusIndex,
                            scale = scale,
                            editing = editing,
                            confirming = false,
                            // Only the visible page is reachable: the other pages
                            // are composed off-panel and must stay out of TalkBack.
                            semanticsEnabled = pageIndex == page,
                            focusRequester = if (pageIndex == page) tileRequester else null,
                            onActivate = { activate(index) },
                            modifier = Modifier
                                // pageOffset changes every frame of the page
                                // animation; the lambda overload keeps that read
                                // in the layout phase instead of recomposing.
                                .offset {
                                    IntOffset(
                                        ((CONTENT_MARGIN + pageIndex * PAGE_STRIDE +
                                            column * COLUMN_PITCH - pageOffset) *
                                            scale.factor * density).roundToInt(),
                                        ((PAGE_TOP - CONTENT_AREA_TOP + row * ROW_PITCH) *
                                            scale.factor * density).roundToInt(),
                                    )
                                }
                                .size(scale.dp(TvOs.ICON_WIDTH), scale.dp(TvOs.ICON_HEIGHT)),
                        )
                    }

                    if (pickupActive) {
                        val dragged = pickupOrigin?.let { folder.apps.getOrNull(it) }
                        if (dragged != null) {
                            val pageIndex = focusIndex / PAGE_SIZE
                            val withinPage = focusIndex % PAGE_SIZE
                            val column = withinPage % COLUMNS
                            val row = withinPage / COLUMNS
                            AppCard(
                                app = dragged,
                                focused = false,
                                scale = scale,
                                editing = editing,
                                pickedUp = true,
                                focusRequester = pickupRequester,
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            ((CONTENT_MARGIN + pageIndex * PAGE_STRIDE +
                                                column * COLUMN_PITCH - pageOffset) *
                                                scale.factor * density).roundToInt(),
                                            ((PAGE_TOP - CONTENT_AREA_TOP + row * ROW_PITCH) *
                                                scale.factor * density).roundToInt(),
                                        )
                                    }
                                    .size(scale.dp(TvOs.ICON_WIDTH), scale.dp(TvOs.ICON_HEIGHT)),
                            )
                        }
                    }
                }

                if (pageCount > 1) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = scale.dp(30f)),
                    ) {
                        Box(
                            Modifier
                                .background(Color.Transparent, RoundedCornerShape(50)),
                                contentAlignment = Alignment.BottomCenter,
                        ) {
                            PageDots(pageCount = pageCount, current = page, scale = scale)
                        }
                    }
                }
            }
        }

        if (renameOpen) {
            RenameKeyboardOverlay(
                title = stringResource(R.string.folder_rename_title),
                initial = folder.name,
                scale = scale,
                onDone = { name ->
                    renameOpen = false
                    onRename(name)
                },
                onCancel = { renameOpen = false },
            )
        }

        menuApp?.let { app ->
            val index = folder.apps.indexOfFirst { it.packageName == app.packageName }
            // The panel offers the same per-app actions as Home; the launcher's
            // own tile would otherwise lose the way back into Settings.
            val own = app.packageName == LocalContext.current.packageName
            TvMenu(
                title = app.label,
                entries = buildList {
                    add(
                        TvMenuEntry(stringResource(R.string.menu_open)) {
                            if (index >= 0) onLaunch(app, cellCenter(index))
                        },
                    )
                    add(
                        TvMenuEntry(stringResource(R.string.menu_move)) {
                            if (index >= 0) {
                                focusIndex = index
                                editing = true
                                titleFocused = false
                                togglePickup()
                            }
                        },
                    )
                    add(
                        TvMenuEntry(stringResource(R.string.menu_show_on_home)) {
                            onMoveToHome(app)
                            close()
                        },
                    )
                    add(
                        TvMenuEntry(stringResource(R.string.menu_app_info)) {
                            onAppInfo(app)
                        },
                    )
                    if (!own && !app.system) {
                        add(
                            TvMenuEntry(
                                label = stringResource(R.string.menu_uninstall),
                                destructive = true,
                            ) {
                                onUninstall(app)
                            },
                        )
                    }
                },
                anchor = if (index >= 0) {
                    cellCenter(index)
                } else {
                    Offset(PANEL_X + PANEL_WIDTH / 2f, PANEL_Y + PANEL_HEIGHT / 2f)
                },
                scale = scale,
                suppressOkUntilRelease = menuOpenedByHold,
                onDismiss = {
                    menuApp = null
                    menuOpenedByHold = false
                },
            )
        }
    }
}

@Composable
private fun PageDots(
    pageCount: Int,
    current: Int,
    scale: TvScale,
) {
    Box(
        Modifier
            .size(
                width = scale.dp(pageCount * 20f),
                height = scale.dp(10f),
            ),
    ) {
        for (index in 0 until pageCount) {
            Box(
                Modifier
                    .offset(x = scale.dp(index * 20f))
                    .size(scale.dp(8f))
                    .clip(CircleShape)
                    .background(
                        Color.White.copy(alpha = if (index == current) 0.85f else 0.30f),
                    ),
            )
        }
    }
}
