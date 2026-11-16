package com.whatuneed.ovisiontv.kotlin.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale

internal const val TRAILER_OVERLAY_TAG = "trailer_overlay"

/**
 * A single official preview, played by the launcher itself. It is deliberately
 * not a second video app: one ExoPlayer, no controls, any remote key or BACK
 * closes it. A failed preview leaves an honest error state instead of a black
 * screen that looks like a hang.
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
                    if (event.type == KeyEventType.KeyDown) onDismiss()
                    // A preview has no sub-focus: every remote key closes it,
                    // including BACK and OK, as the old player did.
                    true
                }
                .focusable()
                .clearAndSetSemantics {
                    testTag = TRAILER_OVERLAY_TAG
                    contentDescription = context.getString(R.string.trailer_description, title)
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
