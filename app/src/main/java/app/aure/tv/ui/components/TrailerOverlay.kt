package app.aure.tv.ui.components

import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.aure.tv.R
import app.aure.tv.ui.LauncherColors
import app.aure.tv.ui.TvSpace
import app.aure.tv.ui.TvType
import app.aure.tv.ui.rememberTvScale

internal const val TRAILER_OVERLAY_TAG = "trailer_overlay"

/**
 * A single official preview, played by the launcher itself. It is deliberately
 * not a second video app: one ExoPlayer, no controls, and BACK closes it. When
 * [actionLabel] is present, OK performs [onAction] instead of closing, so a
 * provider recommendation can go preview → OK → open in its own app. Without
 * an action every remote key closes the overlay. A failed preview leaves an
 * honest error state instead of a black screen that looks like a hang.
 *
 * The media source is the provider URL itself: no CacheDataSource, no
 * SimpleCache and no DownloadHelper. The only storage is ExoPlayer's normal
 * in-memory read buffer while the stream is playing; leaving the overlay
 * releases it.
 *
 * It lives in a [Dialog] window so its focus and BACK handling cannot be
 * swallowed by the Home grid underneath. A plain overlay inside Home left the
 * grid key handler owning the remote, which made BACK a no-op.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TrailerOverlay(
    title: String,
    url: String,
    onDismiss: () -> Unit,
    synopsis: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scale = rememberTvScale()
    val focusRequester = remember { FocusRequester() }
    var failed by remember(url) { mutableStateOf(false) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(url) {
        // The overlay is composed in the same frame as the card that opened
        // it. Wait for that frame so the focus target is attached; without
        // this the request silently leaves focus on the card behind and the
        // remote cannot close the preview.
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                    val hasAction = actionLabel != null && onAction != null
                    when {
                        event.key == Key.Back -> {
                            onDismiss()
                            true
                        }
                        hasAction && (
                            event.key == Key.DirectionCenter ||
                                event.key == Key.Enter ||
                                event.key == Key.NumPadEnter
                            ) -> {
                            onAction?.invoke()
                            true
                        }
                        // While an app action exists, stray keys must not throw
                        // the viewer back to Home before they can press OK.
                        hasAction -> true
                        // A preview without a destination still keeps the old
                        // contract: every remote key closes it.
                        else -> {
                            onDismiss()
                            true
                        }
                    }
                }
                .focusable()
                .clearAndSetSemantics {
                    testTag = TRAILER_OVERLAY_TAG
                    contentDescription = buildString {
                        append(context.getString(R.string.trailer_description, title))
                        actionLabel?.let { append(". ").append(it) }
                    }
                    // TalkBack cannot send BACK/DPAD. Expose the same primary
                    // action as a semantic click on the full-screen player.
                    onClick {
                        if (actionLabel != null && onAction != null) onAction?.invoke() else onDismiss()
                        true
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    (LayoutInflater.from(ctx)
                        .inflate(R.layout.saver_player, FrameLayout(ctx), false) as PlayerView)
                        .apply {
                            this.player = player
                            useController = false
                            setShutterBackgroundColor(AndroidColor.BLACK)
                            isFocusable = false
                            isFocusableInTouchMode = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        }
                },
                update = { it.player = player },
            )

            if (failed) {
                Text(
                    text = stringResource(R.string.trailer_unavailable),
                    color = Color.White,
                    fontSize = scale.sp(TvType.title),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    )
                    .padding(
                        start = scale.dp(TvSpace.xxl),
                        end = scale.dp(TvSpace.xxl),
                        bottom = scale.dp(TvSpace.xxl),
                    ),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.trailer_preview),
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(TvType.callout),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = title,
                    color = LauncherColors.textPrimary,
                    fontSize = scale.sp(TvType.hero),
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = scale.sp(TvType.hero * 1.1f),
                    maxLines = 2,
                )
                synopsis?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        color = LauncherColors.textPrimary.copy(alpha = 0.86f),
                        fontSize = scale.sp(TvType.body),
                        lineHeight = scale.sp(TvType.body * 1.3f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = scale.dp(TvSpace.sm)),
                    )
                }
                if (actionLabel != null) {
                    Box(
                        Modifier
                            .padding(top = scale.dp(TvSpace.md))
                            .clip(RoundedCornerShape(scale.dp(10f)))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = scale.dp(TvSpace.md), vertical = scale.dp(TvSpace.sm)),
                    ) {
                        Text(
                            text = actionLabel,
                            color = LauncherColors.textPrimary,
                            fontSize = scale.sp(TvType.callout),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = stringResource(R.string.trailer_action_hint),
                        color = LauncherColors.textSecondary.copy(alpha = 0.78f),
                        fontSize = scale.sp(TvType.caption),
                        modifier = Modifier.padding(top = scale.dp(TvSpace.xs)),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.trailer_exit_hint),
                        color = LauncherColors.textSecondary,
                        fontSize = scale.sp(TvType.callout),
                        modifier = Modifier.padding(top = scale.dp(TvSpace.xs)),
                    )
                }
            }
        }
    }
}
