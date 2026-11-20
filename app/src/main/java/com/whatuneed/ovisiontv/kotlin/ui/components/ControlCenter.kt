package com.whatuneed.ovisiontv.kotlin.ui.components

import com.whatuneed.ovisiontv.kotlin.core.input.TvKeyRepeat
import com.whatuneed.ovisiontv.kotlin.core.input.rememberTvKeyRepeat
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.whatuneed.ovisiontv.kotlin.core.designsystem.SectionGlyph
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.data.DeviceFactsReader
import com.whatuneed.ovisiontv.kotlin.data.NetworkKind
import com.whatuneed.ovisiontv.kotlin.data.SleepControls
import com.whatuneed.ovisiontv.kotlin.ui.TvInk
import com.whatuneed.ovisiontv.kotlin.ui.SurfaceEasing
import com.whatuneed.ovisiontv.kotlin.ui.TvOs
import com.whatuneed.ovisiontv.kotlin.ui.TvScale
import com.whatuneed.ovisiontv.kotlin.ui.motionDuration
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace
import kotlinx.coroutines.delay

// The Java Control Center sizes were written for its 960×540 design canvas;
// the Kotlin launcher works in tvOS points on 1920×1080, so every layout value
// is twice the Java one. The composition follows the same reference: a status
// capsule in the corner that grows into a band over a compact panel of tiles.
internal const val CC_CAPSULE_SIDE = 48f
internal const val CC_CAPSULE_TOP = 48f
internal const val CC_CAPSULE_HEIGHT = 76f
private const val CC_PANEL_TOP = 144f
private const val CC_TILE_TITLE_FONT = TvType.body
private const val CC_TILE_STATUS_FONT = TvType.caption
private const val CC_CIRCLE_GLYPH = 56f
private const val CC_TILE_GLYPH = 44f
private const val CC_TALL_GLYPH = 92f

// 07_CONTROL_CENTER records that tvOS panel geometry, motion and materials are
// UNKNOWN; these are project values from the Java panel, kept in the shared
// focus rhythm. The page appears as one surface, never as a cascade, so its
// entrance is the shared TvOs.SURFACE_ENTER_MS; only the exit and the pill's
// promotion are local to this surface.
private const val CC_SCRIM_ALPHA = 0.65f

/** The clock follows the interface language, not the device's 12/24-hour setting. */
/**
 * The Wi-Fi tile asks for the narrow name grant once. A second press after a
 * denial opens Wi-Fi settings instead of launching a system dialog that will
 * no longer appear; otherwise the tile would be a dead control.
 */
internal fun shouldAskForWifiName(
    network: NetworkKind,
    granted: Boolean,
    asked: Boolean,
): Boolean = network == NetworkKind.WIFI && !granted && !asked

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
    // Search is a launcher-owned surface, not another Activity: closing it has
    // to put the remote back where the panel was opened, not on a grid tile.
    val returnFocus: Boolean = false,
    val action: () -> Unit,
)

private enum class CcLayout { TALL, ROW, CIRCLE }

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
    var wifiGranted by remember { mutableStateOf(DeviceFactsReader.hasWifiNamePermission(context)) }
    var wifiAsked by remember { mutableStateOf(false) }
    val wifiName = if (network == NetworkKind.WIFI && wifiGranted) {
        DeviceFactsReader.wifiSsid(context)
    } else {
        null
    }
    val volume = remember { volumePercent(context) }
    var timerLeft by remember { mutableIntStateOf(SleepControls.timerLeft(context)) }
    // The name needs the narrowest Wi-Fi grant each API offers; it is asked
    // for from the tile, and the panel stays open so the name appears as soon
    // as it lands.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        wifiGranted = DeviceFactsReader.hasWifiNamePermission(context)
        wifiAsked = true
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

    fun activateCircle(circle: CcCircle) {
        if (closing) return
        // A launcher-owned search surface hands the remote back to the capsule;
        // Settings and other external screens keep the old no-return rule.
        if (circle.returnFocus) {
            onClose(true)
            circle.action()
        } else {
            activate(circle.action)
        }
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
                // The first press asks for the narrow Wi-Fi grant; the next
                // opens Wi-Fi.
                closes = false,
                action = {
                    if (shouldAskForWifiName(network, wifiGranted, wifiAsked)) {
                        wifiAsked = true
                        locationLauncher.launch(DeviceFactsReader.wifiNamePermissions())
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
                returnFocus = true,
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
        circles.firstOrNull { it.id == focusId }?.let(::activateCircle)
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
                    val handled = when (event.key) {
                        Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionUp, Key.DirectionDown,
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                        Key.Back,
                        -> true
                        else -> false
                    }
                    if (!handled) return@onKeyEvent false
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
                reduceMotion = reduceMotion,
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
                reduceMotion = reduceMotion,
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
                    reduceMotion = reduceMotion,
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
                    reduceMotion = reduceMotion,
                    focused = focusId == id,
                    focusRequester = requester(id),
                    onActivate = { activateCircle(circle) },
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
    reduceMotion: Boolean,
    focused: Boolean,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "ccTileFill",
    )
    val grow by animateFloatAsState(
        targetValue = if (focused) CC_FOCUS_SCALE else 1f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
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
