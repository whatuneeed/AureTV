package com.whatuneed.ovisiontv.kotlin.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.widget.ImageView
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.online.onlineImageUrlIsTrusted
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.ui.LauncherColors
import com.whatuneed.ovisiontv.kotlin.ui.TvSpace
import com.whatuneed.ovisiontv.kotlin.ui.TvType
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal const val ONLINE_INFO_OVERLAY_TAG = "online_info_overlay"

/**
 * The honest second half of an online card: JustWatch knows the title, the
 * year, the wide art and the synopsis, but not every title has an official
 * preview that ExoPlayer can play. Instead of dropping those cards or showing
 * a dead "trailer unavailable" toast, OK opens this in-launcher information
 * screen. It never starts a browser or another app.
 */
@Composable
fun OnlineInfoOverlay(
    item: DiscoveryItem,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scale = rememberTvScale()
    val focusRequester = remember { FocusRequester() }
    val artwork by rememberArtwork(item.landscapeUri ?: item.posterUri)
    val synopsis = item.synopsis?.takeIf { it.isNotBlank() }

    LaunchedEffect(item.id) {
        // The dialog is composed in the same frame as the card that opened it.
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
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
                        hasAction -> true
                        else -> {
                            onDismiss()
                            true
                        }
                    }
                }
                .focusable()
                .clearAndSetSemantics {
                    testTag = ONLINE_INFO_OVERLAY_TAG
                    contentDescription = buildString {
                        append(item.title)
                        item.year?.let { append(". ").append(it) }
                        synopsis?.let { append(". ").append(it) }
                        actionLabel?.let { append(". ").append(it) }
                    }
                    // TalkBack has no remote key event. Make the same action
                    // available as a semantic click instead of an unclickable
                    // full-screen text node.
                    onClick {
                        if (actionLabel != null && onAction != null) onAction?.invoke() else onDismiss()
                        true
                    }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(AndroidColor.BLACK)
                        isFocusable = false
                        isFocusableInTouchMode = false
                    }
                },
                update = { it.setImageBitmap(artwork) },
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.90f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxSize()
                    .padding(
                        start = scale.dp(TvSpace.xxl),
                        end = scale.dp(TvSpace.xxl),
                        bottom = scale.dp(TvSpace.xxl),
                    ),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.online_info_about),
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(TvType.callout),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.title,
                    color = LauncherColors.textPrimary,
                    fontSize = scale.sp(TvType.hero),
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = scale.sp(TvType.hero * 1.1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(item.year, item.packageLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(TvType.callout),
                    modifier = Modifier.padding(top = scale.dp(TvSpace.xs)),
                )
                Text(
                    text = synopsis ?: stringResource(R.string.online_info_no_synopsis),
                    color = LauncherColors.textPrimary,
                    fontSize = scale.sp(TvType.body),
                    lineHeight = scale.sp(TvType.body * 1.35f),
                    maxLines = 7,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = scale.dp(TvSpace.md)),
                )
                Text(
                    text = stringResource(R.string.online_info_label),
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(TvType.callout),
                    modifier = Modifier.padding(top = scale.dp(TvSpace.sm)),
                )
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

@Composable
private fun rememberArtwork(url: String?): androidx.compose.runtime.State<Bitmap?> {
    val state = remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        state.value = if (url.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { loadArtwork(url) }
        }
    }
    return state
}

private const val ART_TIMEOUT_MS = 8_000
private const val ART_MAX_BYTES = 8 * 1024 * 1024
private const val ART_MAX_REDIRECTS = 3

private fun loadArtwork(value: String): Bitmap? {
    val bytes = downloadArtwork(value) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inScaled = false
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun downloadArtwork(value: String): ByteArray? {
    var current = value
    repeat(ART_MAX_REDIRECTS + 1) { attempt ->
        if (!onlineImageUrlIsTrusted(current)) return null
        val connection = runCatching {
            URL(current).openConnection() as HttpURLConnection
        }.getOrNull() ?: return null
        try {
            connection.connectTimeout = ART_TIMEOUT_MS
            connection.readTimeout = ART_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "image/*")
            connection.setRequestProperty("User-Agent", "OVisionTV (Android TV)")
            val code = connection.responseCode
            if (code in 300..399) {
                if (attempt >= ART_MAX_REDIRECTS) return null
                val location = connection.getHeaderField("Location") ?: return null
                val next = runCatching { URL(URL(current), location).toString() }.getOrNull()
                    ?: return null
                if (!onlineImageUrlIsTrusted(next) || !imageRedirectIsTrusted(current, next)) {
                    return null
                }
                current = next
                return@repeat
            }
            if (code != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > ART_MAX_BYTES) return null
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(64 * 1024)
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > ART_MAX_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        } finally {
            connection.disconnect()
        }
    }
    return null
}
