package com.whatuneed.ovisiontv.kotlin.ui.components

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.DeviceFactsReader
import com.whatuneed.ovisiontv.kotlin.data.NetworkKind
import com.whatuneed.ovisiontv.kotlin.data.SleepControls
import com.whatuneed.ovisiontv.kotlin.ui.TvFocus
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.TvRadius
import com.whatuneed.ovisiontv.kotlin.ui.SurfaceEasing
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// The Java Control Center sizes were written for its 960×540 design canvas;
// the Kotlin launcher works in tvOS points on 1920×1080, so every layout value
// is twice the Java one. The composition follows the same reference: a status
// capsule in the corner that grows into a band over a compact panel of tiles.
internal const val CC_CAPSULE_SIDE = 48f
internal const val CC_CAPSULE_TOP = 48f
internal const val CC_CAPSULE_HEIGHT = 76f
private const val CC_CAPSULE_RU_WIDTH = 216f
private const val CC_CAPSULE_EN_WIDTH = 252f
private const val CC_PANEL_TOP = 144f
private const val CC_PANEL_WIDTH = 560f
private const val CC_TILE_WIDTH = 272f
private const val CC_TILE_HEIGHT = 96f
private const val CC_TILE_GAP = 16f
private const val CC_CIRCLE = 80f
private const val CC_CAPSULE_FONT = TvType.subhead
private const val CC_TILE_TITLE_FONT = TvType.body
private const val CC_TILE_STATUS_FONT = TvType.caption
private const val CC_CIRCLE_GLYPH = 56f
private const val CC_TILE_GLYPH = 44f
private const val CC_TALL_GLYPH = 92f
private const val CC_CAPSULE_GLYPH = 36f
private const val CC_FOCUS_SCALE = 1.04f

// 07_CONTROL_CENTER records that tvOS panel geometry, motion and materials are
// UNKNOWN; these are project values from the Java panel, kept in the shared
// focus rhythm. The page appears as one surface, never as a cascade, so its
// entrance is the shared TvOs.SURFACE_ENTER_MS; only the exit and the pill's
// promotion are local to this surface.
private const val CC_EXIT_MS = 200
private const val CC_PROMOTE_MS = 280
private const val CC_PANEL_DROP = 20f
private const val CC_SCRIM_ALPHA = 0.65f

internal const val LEAD_ID = "lead"
internal const val SOUND_ID = "left:1"
private const val CC_TALL_RADIUS = TvRadius.panelWide

// One geometry source for the layout and for focus navigation: the hand-written
// index graph routed arrows diagonally (Down from Wi-Fi landed on the saver
// circle), so moves are resolved against the real tile rectangles instead.
internal data class CcFocusRect(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

internal data class CcGeometry(
    val powerHeight: Float,
    val soundY: Float,
    val circleY: Float,
    val panelHeight: Float,
)

internal fun ccGeometry(rightCount: Int, circleCount: Int): CcGeometry {
    val rowsCount = maxOf(rightCount, 2)
    val rowsHeight = rowsCount * CC_TILE_HEIGHT + (rowsCount - 1) * CC_TILE_GAP
    val powerHeight = (rowsCount - 1) * CC_TILE_HEIGHT + (rowsCount - 2) * CC_TILE_GAP
    val soundY = powerHeight + CC_TILE_GAP
    return CcGeometry(
        powerHeight = powerHeight,
        soundY = soundY,
        circleY = rowsHeight + CC_TILE_GAP,
        panelHeight = rowsHeight + if (circleCount == 0) 0f else CC_TILE_GAP + CC_CIRCLE,
    )
}

internal fun ccCircleX(index: Int, count: Int): Float =
    if (count <= 1) 0f else index * (CC_PANEL_WIDTH - CC_CIRCLE) / (count - 1f)

internal fun ccFocusRects(rightIds: List<String>, circleIds: List<String>): List<CcFocusRect> {
    val geometry = ccGeometry(rightIds.size, circleIds.size)
    return buildList {
        add(CcFocusRect(LEAD_ID, 0f, 0f, CC_TILE_WIDTH, geometry.powerHeight))
        add(CcFocusRect(SOUND_ID, 0f, geometry.soundY, CC_TILE_WIDTH, geometry.soundY + CC_TILE_HEIGHT))
        rightIds.forEachIndexed { index, id ->
            val y = index * (CC_TILE_HEIGHT + CC_TILE_GAP)
            add(CcFocusRect(id, CC_TILE_WIDTH + CC_TILE_GAP, y, CC_PANEL_WIDTH, y + CC_TILE_HEIGHT))
        }
        circleIds.forEachIndexed { index, id ->
            val x = ccCircleX(index, circleIds.size)
            add(CcFocusRect(id, x, geometry.circleY, x + CC_CIRCLE, geometry.circleY + CC_CIRCLE))
        }
    }
}

// FocusFinder rules: the candidate must lie in the pressed direction and share
// the perpendicular beam with the source — a corner touch must not steal a
// vertical move. Nearest edge wins; ties go to the closer cross-axis center,
// then to layout order. No candidate means the focus stays put.
internal fun nextCcFocusId(currentId: String, direction: Key, rects: List<CcFocusRect>): String? {
    val current = rects.firstOrNull { it.id == currentId } ?: return null
    val horizontal = direction == Key.DirectionLeft || direction == Key.DirectionRight
    val forward = direction == Key.DirectionRight || direction == Key.DirectionDown
    var best: CcFocusRect? = null
    var bestMajor = Float.MAX_VALUE
    var bestCross = Float.MAX_VALUE
    for (candidate in rects) {
        if (candidate.id == currentId) continue
        val inDirection: Boolean
        val sharesBeam: Boolean
        val major: Float
        val cross: Float
        if (horizontal) {
            inDirection = if (forward) candidate.left >= current.right else candidate.right <= current.left
            sharesBeam = candidate.top < current.bottom && candidate.bottom > current.top
            major = if (forward) candidate.left - current.right else current.left - candidate.right
            cross = abs(candidate.centerY - current.centerY)
        } else {
            inDirection = if (forward) candidate.top >= current.bottom else candidate.bottom <= current.top
            sharesBeam = candidate.left < current.right && candidate.right > current.left
            major = if (forward) candidate.top - current.bottom else current.top - candidate.bottom
            cross = abs(candidate.centerX - current.centerX)
        }
        if (!inDirection || !sharesBeam) continue
        if (major < bestMajor || (major == bestMajor && cross < bestCross)) {
            best = candidate
            bestMajor = major
            bestCross = cross
        }
    }
    return best?.id
}

/** The clock follows the interface language, not the device's 12/24-hour setting. */
@Composable
internal fun rememberClockText(): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    // English reads 9:40 PM, Russian reads 21:40, as in the Java build.
    val pattern = clockPattern(locale.language, minutes = true)
    val format = remember(pattern, locale) { SimpleDateFormat(pattern, locale) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val millis = System.currentTimeMillis()
            now = millis
            // Wake on the minute boundary instead of polling every second.
            delay(60_000L - millis % 60_000L)
        }
    }
    return format.format(Date(now))
}

@Composable
internal fun rememberNetworkKind(): NetworkKind {
    val context = LocalContext.current
    var kind by remember { mutableStateOf(DeviceFactsReader.currentNetwork(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        // Callbacks arrive on a binder thread; the main handler keeps the state
        // write on the composition thread.
        val callback = object : ConnectivityManager.NetworkCallback() {
            private fun refresh() {
                kind = DeviceFactsReader.currentNetwork(context)
            }

            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = refresh()
        }
        val registered = runCatching {
            manager?.registerDefaultNetworkCallback(callback, Handler(Looper.getMainLooper()))
        }.isSuccess
        onDispose {
            if (registered) runCatching { manager?.unregisterNetworkCallback(callback) }
        }
    }
    return kind
}

@Composable
internal fun networkStatusLabel(kind: NetworkKind): String = stringResource(
    when (kind) {
        NetworkKind.NONE -> R.string.cc_not_connected
        NetworkKind.ETHERNET -> R.string.settings_network_ethernet
        NetworkKind.MOBILE -> R.string.settings_network_mobile
        NetworkKind.WIFI, NetworkKind.OTHER -> R.string.cc_connected
    },
)

/**
 * The top-right status pill. It is the entry point to the Control Center and
 * shows only real state: the live clock and the active connection. While the
 * panel is open the pill grows into a band as wide as the panel, the clock
 * glides left and the network glyph keeps the right end.
 *
 * Focus is the same inset white ring as the launcher's other chrome. The pill
 * has no elevation (FRAGILE TV GPU: a stadium outline turns an elevation shadow
 * into a square patch on some TV GPUs).
 */
@Composable
internal fun StatusCapsule(
    scale: TvScale,
    focused: Boolean,
    promoted: Boolean,
    clock: String,
    network: NetworkKind,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val russian = LocalConfiguration.current.locales[0]?.language == "ru"
    val width by animateFloatAsState(
        targetValue = if (promoted) {
            CC_PANEL_WIDTH
        } else if (russian) {
            CC_CAPSULE_RU_WIDTH
        } else {
            CC_CAPSULE_EN_WIDTH
        },
        animationSpec = tween(CC_PROMOTE_MS, easing = SurfaceEasing),
        label = "capsuleWidth",
    )
    val grow by animateFloatAsState(
        targetValue = if (focused && !promoted) CC_FOCUS_SCALE else 1f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "capsuleFocus",
    )
    val ring by animateFloatAsState(
        targetValue = if (focused && !promoted) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "capsuleRing",
    )
    val shape = RoundedCornerShape(scale.dp(CC_CAPSULE_HEIGHT / 2f))
    val title = stringResource(R.string.cc_title)
    val networkText = networkStatusLabel(network)
    Row(
        modifier = modifier
            .width(scale.dp(width))
            .height(scale.dp(CC_CAPSULE_HEIGHT))
            // The pill grows leftwards from its right edge, as the Java one did.
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(TvInk.capsuleTop, TvInk.capsuleBottom),
                ),
                shape = shape,
            )
            .border(
                // Java marks the focused pill with a bright inset ring; the
                // ring sits inside the clip so it keeps its full stroke.
                width = TvFocus.capsuleWidth(scale, ring),
                brush = TvFocus.capsuleBrush(ring),
                shape = shape,
            )
            .then(
                if (promoted) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                        .clearAndSetSemantics {
                            contentDescription = title
                            stateDescription = "$networkText, $clock"
                            role = Role.Button
                            onClick(label = title) {
                                onActivate()
                                true
                            }
                        }
                },
            )
            .padding(horizontal = scale.dp(24f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = clock,
            color = Color.White,
            fontSize = scale.sp(CC_CAPSULE_FONT),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .width(scale.dp(2f))
                .height(scale.dp(32f))
                .background(Color.White.copy(alpha = 0.22f)),
        )
        Spacer(Modifier.width(scale.dp(20f)))
        Image(
            imageVector = SectionGlyph.wifi,
            contentDescription = null,
            modifier = Modifier.size(scale.dp(CC_CAPSULE_GLYPH)),
            colorFilter = ColorFilter.tint(
                Color.White.copy(alpha = if (network == NetworkKind.NONE) 0.35f else 0.92f),
            ),
        )
    }
}

private class CcTile(
    val id: String,
    val title: String,
    val status: String?,
    val glyph: ImageVector,
    /** A timer/power tile keeps the panel open when it only changes its own state. */
    val closes: Boolean = true,
    val action: () -> Unit,
)

private class CcCircle(
    val id: String,
    val label: String,
    val glyph: ImageVector,
    val action: () -> Unit,
)

private enum class CcLayout { TALL, ROW, CIRCLE }

/**
 * tvOS-inspired quick settings, laid out like the Java panel: Power holds the
 * left column's first two rows with Sound under it; Wi-Fi, Display and the
 * Sleep Timer stand in one column on the right; the saver and the launcher's
 * shortcuts fill the circle row. Do Not Disturb is left out until notification
 * access is decided (31_QUICK_SETTINGS_CONTROL_CENTER).
 *
 * [onClose] receives whether focus should return to the capsule: Back does,
 * an action leaves for another surface and does not.
 */
@Composable
internal fun ControlCenter(
    scale: TvScale,
    reduceMotion: Boolean,
    libraryAvailable: Boolean,
    onClose: (returnFocus: Boolean) -> Unit,
    onStartSaver: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    // The launcher routes system Settings through the kids PIN gate; a test
    // host without a gate leaves it null and opens the screen directly.
    onOpenSystem: ((Intent) -> Unit)? = null,
) {
    val context = LocalContext.current
    fun openSystem(intent: Intent) {
        if (onOpenSystem != null) onOpenSystem(intent) else openSystemScreen(context, intent)
    }
    // Asking for the sleep permission is system navigation too: it goes
    // through the same kids gate as the system tiles, OEM fallback included.
    fun requestSleepPermission() {
        val intent = SleepControls.permissionIntent(context)
        if (intent == null) {
            Toast.makeText(context, R.string.settings_power_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (intent.action != DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN) {
            Toast.makeText(context, R.string.settings_power_accessibility_hint, Toast.LENGTH_LONG)
                .show()
        }
        openSystem(intent)
    }
    val keyRepeat = rememberTvKeyRepeat()
    val scope = rememberCoroutineScope()
    val network = rememberNetworkKind()
    val networkStatus = networkStatusLabel(network)
    var locationGranted by remember { mutableStateOf(DeviceFactsReader.hasLocation(context)) }
    val wifiName = if (network == NetworkKind.WIFI && locationGranted) {
        DeviceFactsReader.wifiSsid(context)
    } else {
        null
    }
    val volume = remember { volumePercent(context) }
    var timerLeft by remember { mutableStateOf(SleepControls.timerLeft(context)) }
    // The name needs location on Android 9+; the grant is asked for from the
    // tile, and the panel stays open so the name appears as soon as it lands.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        locationGranted = DeviceFactsReader.hasLocation(context)
    }
    val screen = remember {
        val metrics = context.resources.displayMetrics
        "${metrics.widthPixels} × ${metrics.heightPixels}"
    }
    // Android TV often has no standalone Display screen: its Settings expose
    // Display and Sound as one activity, so the tile opens that real screen.
    val display = remember {
        val direct = Intent(Settings.ACTION_DISPLAY_SETTINGS)
        when {
            resolves(context, direct) -> direct
            resolves(context, Intent(Settings.ACTION_SOUND_SETTINGS)) ->
                Intent(Settings.ACTION_SOUND_SETTINGS)

            else -> null
        }
    }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(id: String): FocusRequester = requesters.getOrPut(id) { FocusRequester() }

    var focusId by remember { mutableStateOf(LEAD_ID) }
    var closing by remember { mutableStateOf(false) }
    var closeReturnsFocus by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val surfaceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion), easing = SurfaceEasing),
        label = "ccAlpha",
    )
    val drop by animateFloatAsState(
        targetValue = if (entered) 0f else -CC_PANEL_DROP,
        animationSpec = tween(motionDuration(TvOs.SURFACE_ENTER_MS, reduceMotion), easing = SurfaceEasing),
        label = "ccDrop",
    )

    fun requestClose(returnFocus: Boolean) {
        if (closing) return
        closeReturnsFocus = returnFocus
        closing = true
    }

    // An exit fades the whole surface out before Home takes the keys again.
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        entered = false
        delay(motionDuration(CC_EXIT_MS, reduceMotion).toLong())
        onClose(closeReturnsFocus)
    }

    fun activate(action: () -> Unit) {
        if (closing) return
        // The destination is another surface; it must not come back to the panel.
        onClose(false)
        action()
    }

    // One activation path for the remote and for TalkBack: a tile that only
    // changes its own state keeps the panel open, exactly as [activateFocus]
    // already did for the remote (AQ-26).
    fun runTile(tile: CcTile) {
        if (tile.closes) activate(tile.action) else tile.action()
    }

    val power = CcTile(
        id = LEAD_ID,
        title = stringResource(R.string.cc_power),
        status = null,
        glyph = SectionGlyph.power,
        action = {
            if (!SleepControls.powerOff(context)) requestSleepPermission()
        },
    )
    val sound = CcTile(
        id = SOUND_ID,
        title = stringResource(R.string.cc_sound),
        status = volume?.let { stringResource(R.string.cc_volume, it) },
        glyph = SectionGlyph.volume,
        action = { openSystem(Intent(Settings.ACTION_SOUND_SETTINGS)) },
    )
    val rights = buildList {
        add(
            CcTile(
                id = "right:0",
                title = stringResource(R.string.settings_network_wifi),
                status = wifiName ?: networkStatus,
                glyph = SectionGlyph.wifi,
                // As in the Java build: the name needs location, so the first
                // press asks for it and the next opens Wi-Fi.
                closes = false,
                action = {
                    if (network == NetworkKind.WIFI && !locationGranted) {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    } else {
                        requestClose(false)
                        openSystem(Intent(Settings.ACTION_WIFI_SETTINGS))
                    }
                },
            ),
        )
        if (display != null) {
            add(
                CcTile(
                    id = "right:${size}",
                    title = stringResource(R.string.cc_display),
                    status = screen,
                    glyph = SectionGlyph.tv,
                    action = { openSystem(display) },
                ),
            )
        }
        add(
            CcTile(
                id = "right:$size",
                title = stringResource(R.string.cc_sleep_timer),
                status = if (timerLeft > 0) {
                    stringResource(R.string.cc_minutes, timerLeft)
                } else {
                    stringResource(R.string.saver_off)
                },
                glyph = SectionGlyph.clock,
                // Off → 15 → 30 → 60 → 90 → Off, as in the Java panel. The
                // tile stays open so the next step is one press away.
                closes = false,
                action = {
                    if (!SleepControls.hasAdmin(context)) {
                        requestClose(false)
                        requestSleepPermission()
                    } else {
                        SleepControls.setTimer(
                            context,
                            SleepControls.nextTimer(SleepControls.timerLeft(context)),
                        )
                        timerLeft = SleepControls.timerLeft(context)
                    }
                },
            ),
        )
    }
    val circles = buildList {
        add(
            CcCircle(
                id = "circle:0",
                label = stringResource(R.string.cc_screensaver),
                glyph = SectionGlyph.photo,
                action = onStartSaver,
            ),
        )
        // Without a second row there is no library to open; a tile that only
        // closed the panel would be a dead control.
        if (libraryAvailable) {
            add(
                CcCircle(
                    id = "circle:1",
                    label = stringResource(R.string.cc_apps),
                    glyph = SectionGlyph.apps,
                    action = onOpenLibrary,
                ),
            )
        }
        add(
            CcCircle(
                id = "circle:${size}",
                label = stringResource(R.string.cc_search),
                glyph = SectionGlyph.search,
                action = onOpenSearch,
            ),
        )
        add(
            CcCircle(
                id = "circle:${size}",
                label = stringResource(R.string.cc_settings),
                glyph = SectionGlyph.gear,
                action = onOpenSettings,
            ),
        )
        add(
            CcCircle(
                id = "circle:${size}",
                label = stringResource(R.string.cc_system),
                glyph = SectionGlyph.tv,
                action = { openSystem(Intent(Settings.ACTION_SETTINGS)) },
            ),
        )
        add(
            CcCircle(
                id = "circle:${size}",
                label = stringResource(R.string.cc_accessibility),
                glyph = SectionGlyph.accessibility,
                action = {
                    openSystem(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            ),
        )
        if (resolves(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) {
            add(
                CcCircle(
                    id = "circle:${size}",
                    label = stringResource(R.string.cc_controllers),
                    glyph = SectionGlyph.bluetooth,
                    action = {
                        openSystem(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                ),
            )
        }
    }

    val circleCount = circles.size
    // Power fills the left column's first rows and Sound takes the last one;
    // the grid never has fewer than two rows, so Power is always the tall tile.
    val geometry = ccGeometry(rights.size, circleCount)
    val powerHeight = geometry.powerHeight
    val soundY = geometry.soundY
    val panelHeight = geometry.panelHeight
    val circleY = geometry.circleY

    val focusRects = ccFocusRects(rights.map { it.id }, circles.map { it.id })

    fun move(direction: Key): Boolean {
        nextCcFocusId(focusId, direction, focusRects)?.let { focusId = it }
        return true
    }

    fun tileById(id: String): CcTile? = when (id) {
        LEAD_ID -> power
        SOUND_ID -> sound
        else -> rights.firstOrNull { it.id == id }
    }

    fun activateFocus() {
        if (closing) return
        val tile = tileById(focusId)
        if (tile != null) {
            runTile(tile)
            return
        }
        circles.firstOrNull { it.id == focusId }?.let { activate(it.action) }
    }

    LaunchedEffect(focusId) {
        requesters[focusId]?.requestFocus()
    }

    BackHandler(enabled = !closing) { requestClose(true) }

    val rootRequester = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    keyRepeat.release(event.key)
                    return@onKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown || closing) return@onKeyEvent true
                // A held button arrives as repeat KeyDowns or as repeated
                // press/release pairs; both must move once and then repeat at
                // the launcher's own pace.
                if (TvKeyRepeat.isOneShot(event.key) && !keyRepeat.firstPress(event)) {
                    return@onKeyEvent true
                }
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionUp, Key.DirectionDown,
                    -> {
                        move(event.key)
                        keyRepeat.startRepeat(scope) { move(event.key) }
                        true
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        keyRepeat.stopRepeat()
                        activateFocus()
                        true
                    }

                    Key.Back -> {
                        keyRepeat.stopRepeat()
                        requestClose(true)
                        true
                    }

                    else -> false
                }
            }
            .focusable(),
    ) {
        // The scrim lets the glass tiles lead without blurring the whole screen.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = surfaceAlpha * CC_SCRIM_ALPHA }
                .background(Color.Black),
        )

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = scale.dp(CC_PANEL_TOP),
                    end = scale.dp(CC_CAPSULE_SIDE),
                )
                .size(scale.dp(CC_PANEL_WIDTH), scale.dp(panelHeight))
                .graphicsLayer {
                    alpha = surfaceAlpha
                    translationY = drop * scale.factor * density
                },
        ) {
            CcTileView(
                scale = scale,
                title = power.title,
                status = power.status,
                glyph = power.glyph,
                // With no Display screen the right column is one row shorter and
                // Power fits a single row, so it keeps the row layout.
                glyphSize = if (powerHeight > CC_TILE_HEIGHT) CC_TALL_GLYPH else CC_TILE_GLYPH,
                width = CC_TILE_WIDTH,
                height = powerHeight,
                radius = if (powerHeight > CC_TILE_HEIGHT) CC_TALL_RADIUS else CC_TILE_HEIGHT / 2f,
                layout = if (powerHeight > CC_TILE_HEIGHT) CcLayout.TALL else CcLayout.ROW,
                focused = focusId == LEAD_ID,
                focusRequester = requester(LEAD_ID),
                onActivate = { runTile(power) },
                modifier = Modifier.offset(x = 0.dp),
            )

            CcTileView(
                scale = scale,
                title = sound.title,
                status = sound.status,
                glyph = sound.glyph,
                glyphSize = CC_TILE_GLYPH,
                width = CC_TILE_WIDTH,
                height = CC_TILE_HEIGHT,
                radius = CC_TILE_HEIGHT / 2f,
                layout = CcLayout.ROW,
                focused = focusId == SOUND_ID,
                focusRequester = requester(SOUND_ID),
                onActivate = { runTile(sound) },
                modifier = Modifier.offset(x = 0.dp, y = scale.dp(soundY)),
            )

            rights.forEachIndexed { index, tile ->
                val id = tile.id
                CcTileView(
                    scale = scale,
                    title = tile.title,
                    status = tile.status,
                    glyph = tile.glyph,
                    glyphSize = CC_TILE_GLYPH,
                    width = CC_TILE_WIDTH,
                    height = CC_TILE_HEIGHT,
                    radius = CC_TILE_HEIGHT / 2f,
                    layout = CcLayout.ROW,
                    focused = focusId == id,
                    focusRequester = requester(id),
                    onActivate = { runTile(tile) },
                    modifier = Modifier.offset(
                        x = scale.dp(CC_TILE_WIDTH + CC_TILE_GAP),
                        y = scale.dp(index * (CC_TILE_HEIGHT + CC_TILE_GAP)),
                    ),
                )
            }

            circles.forEachIndexed { index, circle ->
                val x = ccCircleX(index, circleCount)
                val id = circle.id
                CcTileView(
                    scale = scale,
                    title = circle.label,
                    status = null,
                    glyph = circle.glyph,
                    glyphSize = CC_CIRCLE_GLYPH,
                    width = CC_CIRCLE,
                    height = CC_CIRCLE,
                    radius = CC_CIRCLE / 2f,
                    layout = CcLayout.CIRCLE,
                    focused = focusId == id,
                    focusRequester = requester(id),
                    onActivate = { activate(circle.action) },
                    modifier = Modifier.offset(x = scale.dp(x), y = scale.dp(circleY)),
                )
            }
        }
    }
}

@Composable
private fun CcTileView(
    scale: TvScale,
    title: String,
    status: String?,
    glyph: ImageVector,
    glyphSize: Float,
    width: Float,
    height: Float,
    radius: Float,
    layout: CcLayout,
    focused: Boolean,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "ccTileFill",
    )
    val grow by animateFloatAsState(
        targetValue = if (focused) CC_FOCUS_SCALE else 1f,
        animationSpec = tween(TvOs.FOCUS_MS),
        label = "ccTileFocus",
    )
    val shape = RoundedCornerShape(scale.dp(radius))
    val textColor = lerp(Color.White, TvInk.focusInk, fill)
    Box(
        modifier = modifier
            .size(scale.dp(width), scale.dp(height))
            .graphicsLayer {
                scaleX = grow
                scaleY = grow
            }
            .clip(shape)
            .background(lerp(Color.White.copy(alpha = 0.10f), Color.White, fill), shape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f * (1f - fill)),
                shape = shape,
            )
            .focusRequester(focusRequester)
            .focusable()
            .clearAndSetSemantics {
                contentDescription = title
                status?.let { stateDescription = it }
                role = Role.Button
                onClick(label = title) {
                    onActivate()
                    true
                }
            }
            .padding(horizontal = scale.dp(24f)),
        contentAlignment = Alignment.Center,
    ) {
        when (layout) {
            CcLayout.CIRCLE -> Image(
                imageVector = glyph,
                contentDescription = null,
                modifier = Modifier.size(scale.dp(glyphSize)),
                colorFilter = ColorFilter.tint(textColor),
            )

            CcLayout.TALL -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    imageVector = glyph,
                    contentDescription = null,
                    modifier = Modifier.size(scale.dp(glyphSize)),
                    colorFilter = ColorFilter.tint(textColor),
                )
                Spacer(Modifier.height(scale.dp(TvSpace.lg)))
                TileTexts(title, status, textColor, scale)
            }

            CcLayout.ROW -> Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    imageVector = glyph,
                    contentDescription = null,
                    modifier = Modifier.size(scale.dp(glyphSize)),
                    colorFilter = ColorFilter.tint(textColor),
                )
                Spacer(Modifier.width(scale.dp(14f)))
                TileTexts(title, status, textColor, scale, centered = false)
            }
        }
    }
}

@Composable
private fun TileTexts(
    title: String,
    status: String?,
    color: Color,
    scale: TvScale,
    centered: Boolean = true,
) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = title,
            color = color,
            fontSize = scale.sp(CC_TILE_TITLE_FONT),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (status != null) {
            Text(
                text = status,
                color = color.copy(alpha = 0.8f),
                fontSize = scale.sp(CC_TILE_STATUS_FONT),
                lineHeight = scale.sp(CC_TILE_STATUS_FONT + 6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun volumePercent(context: Context): Int? {
    val manager = context.getSystemService(AudioManager::class.java) ?: return null
    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return null
    val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
    return (current * 100f / max).toInt().coerceIn(0, 100)
}

private fun resolves(context: Context, intent: Intent): Boolean =
    runCatching { intent.resolveActivity(context.packageManager) != null }.getOrDefault(false)

private fun openSystemScreen(context: Context, intent: Intent) {
    // Not every TV build exposes a given settings screen; the tiles that can be
    // missing are hidden, but a device can still refuse the launch. Saying so
    // is better than a press that silently does nothing.
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, R.string.launch_failed, Toast.LENGTH_SHORT).show()
    }
}
