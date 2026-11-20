package app.aure.tv.ui.components

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import app.aure.tv.core.designsystem.SectionGlyph
import app.aure.tv.R
import app.aure.tv.data.DeviceFactsReader
import app.aure.tv.data.NetworkKind
import app.aure.tv.ui.TvFocus
import app.aure.tv.ui.TvInk
import app.aure.tv.ui.SurfaceEasing
import app.aure.tv.ui.TvOs
import app.aure.tv.ui.TvScale
import app.aure.tv.ui.motionDuration
import app.aure.tv.ui.TvType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val CC_CAPSULE_RU_WIDTH = 216f
internal const val CC_CAPSULE_EN_WIDTH = 252f
internal const val CC_PROMOTE_MS = 280
internal const val CC_FOCUS_SCALE = 1.04f
internal const val CC_AVATAR_SIZE = 40f
internal const val CC_AVATAR_FONT = 22f
internal const val CC_AVATAR_GAP = 12f
internal const val CC_AVATAR_SPACE = CC_AVATAR_SIZE + CC_AVATAR_GAP
internal const val CC_CAPSULE_FONT = TvType.subhead
internal const val CC_CAPSULE_GLYPH = 36f
internal const val CC_EXIT_MS = 200
internal const val CC_PANEL_DROP = 20f

@Composable
internal fun rememberClockText(): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    // English reads 9:40 PM, Russian reads 21:40, as in the Java build.
    val pattern = clockPattern(locale.language, minutes = true)
    val format = remember(pattern, locale) { SimpleDateFormat(pattern, locale) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
    reduceMotion: Boolean = false,
    profileEmoji: String? = null,
    profileColor: Long? = null,
    profileName: String? = null,
) {
    val russian = LocalConfiguration.current.locales[0]?.language == "ru"
    val avatarSpace = if (profileEmoji != null) CC_AVATAR_SPACE else 0f
    val width by animateFloatAsState(
        targetValue = if (promoted) {
            CC_PANEL_WIDTH
        } else if (russian) {
            CC_CAPSULE_RU_WIDTH + avatarSpace
        } else {
            CC_CAPSULE_EN_WIDTH + avatarSpace
        },
        animationSpec = tween(motionDuration(CC_PROMOTE_MS, reduceMotion), easing = SurfaceEasing),
        label = "capsuleWidth",
    )
    val grow by animateFloatAsState(
        targetValue = if (focused && !promoted) CC_FOCUS_SCALE else 1f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
        label = "capsuleFocus",
    )
    val ring by animateFloatAsState(
        targetValue = if (focused && !promoted) 1f else 0f,
        animationSpec = tween(motionDuration(TvOs.FOCUS_MS, reduceMotion)),
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
                            stateDescription = listOfNotNull(
                                networkText,
                                clock,
                                profileName,
                            ).joinToString(", ")
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
        if (profileEmoji != null && !promoted) {
            Spacer(Modifier.width(scale.dp(CC_AVATAR_GAP)))
            Box(
                Modifier
                    .size(scale.dp(CC_AVATAR_SIZE))
                    .clip(CircleShape)
                    .background(Color(profileColor ?: 0xFFD9DCE3)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profileEmoji,
                    fontSize = scale.sp(CC_AVATAR_FONT),
                    maxLines = 1,
                )
            }
        }
    }
}

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
