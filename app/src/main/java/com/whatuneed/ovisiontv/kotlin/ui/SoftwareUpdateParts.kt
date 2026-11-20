package com.whatuneed.ovisiontv.kotlin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ui.components.TV_TOOL_LIST_WIDTH
import com.whatuneed.ovisiontv.kotlin.core.designsystem.TV_LIST_ROW_HEIGHT
import com.whatuneed.ovisiontv.kotlin.core.designsystem.TvListRowSurface
import com.whatuneed.ovisiontv.kotlin.update.UpdateError
import com.whatuneed.ovisiontv.kotlin.update.UpdateRelease
import com.whatuneed.ovisiontv.kotlin.update.UpdateState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun UpdateNotesOverlay(release: UpdateRelease?, onClose: () -> Unit) {
    val scale = rememberTvScale()
    val requester = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { requester.requestUpdateFocusWhenAttached() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .testTag("update_notes_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(scale.dp(900f))
                .background(Color(0xFF1B1B20), RoundedCornerShape(scale.dp(18f)))
                .border(scale.dp(1f), Color.White.copy(alpha = 0.16f), RoundedCornerShape(scale.dp(18f)))
                .padding(scale.dp(24f)),
        ) {
            Text(
                text = stringResource(R.string.update_notes),
                color = Color.White,
                fontSize = scale.sp(TvType.title),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(scale.dp(12f)))
            val notes = release?.notesFor(Locale.getDefault().language).orEmpty()
            if (notes.isEmpty()) {
                Text(
                    text = stringResource(R.string.update_notes_empty),
                    color = LauncherColors.textSecondary,
                    fontSize = scale.sp(TvType.body),
                )
            } else {
                LazyColumn(
                    Modifier
                        .height(scale.dp(360f))
                        .testTag("update_notes"),
                    verticalArrangement = Arrangement.spacedBy(scale.dp(10f)),
                ) {
                    itemsIndexed(notes) { _, line ->
                        Text(
                            text = "• $line",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = scale.sp(TvType.body),
                        )
                    }
                }
            }
            Spacer(Modifier.height(scale.dp(18f)))
            UpdateRowView(
                row = UpdateRow(stringResource(R.string.update_cancel), action = onClose),
                focused = true,
                scale = scale,
                requester = requester,
                onFocus = {},
            )
        }
    }
}

@Composable
internal fun UpdateRowView(
    row: UpdateRow,
    focused: Boolean,
    scale: TvScale,
    requester: FocusRequester,
    onFocus: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TvListRowSurface(
        focused = focused,
        scale = scale,
        modifier = Modifier
            .width(scale.dp(TV_TOOL_LIST_WIDTH))
            .height(scale.dp(TV_LIST_ROW_HEIGHT))
            .then(
                if (row.focusable) {
                    Modifier
                        .focusRequester(requester)
                        .onFocusChanged { if (it.isFocused) onFocus() }
                        // A read-only row still needs one focus owner on an
                        // otherwise action-less page; its click is a no-op.
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { row.action?.invoke() }
                } else {
                    Modifier
                },
            ),
    ) { focusAmount ->
        val titleColor = when {
            !row.focusable -> Color.White.copy(alpha = 0.42f)
            row.destructive -> LauncherColors.destructive
            else -> lerp(Color.White.copy(alpha = 0.95f), Color.Black, focusAmount)
        }
        val valueColor = if (!row.focusable) {
            Color.White.copy(alpha = 0.32f)
        } else {
            lerp(
                LauncherColors.textSecondary,
                Color.Black.copy(alpha = 0.6f),
                focusAmount,
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.title,
                color = titleColor,
                fontSize = scale.sp(TvType.body),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            row.value?.let {
                Text(
                    text = it,
                    color = valueColor,
                    fontSize = scale.sp(TvType.callout),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun errorTitle(error: UpdateError?, state: UpdateState): String = stringResource(
    when (error) {
        UpdateError.NETWORK -> R.string.update_network
        UpdateError.SERVER -> R.string.update_server
        UpdateError.RATE_LIMITED -> R.string.update_rate_limited
        UpdateError.METADATA -> R.string.update_metadata
        UpdateError.STORAGE -> R.string.update_storage
        UpdateError.INTEGRITY -> R.string.update_integrity
        UpdateError.SIGNATURE -> R.string.update_signature
        UpdateError.PERMISSION -> R.string.update_permission
        UpdateError.INSTALL -> R.string.update_failed
        UpdateError.CANCELLED -> R.string.update_failed
        else -> if (state.errorDetail == null) R.string.update_failed else R.string.update_failed
    },
)

@Composable
internal fun incompatibilityText(state: UpdateState): String = stringResource(
    when (state.errorDetail) {
        "ANDROID_TOO_OLD" -> R.string.update_incompatible_api
        "ABI_UNSUPPORTED" -> R.string.update_incompatible_abi
        else -> R.string.update_incompatible_version
    },
    state.available?.minAndroidApi ?: 28,
)

internal fun formatDate(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
        .format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value)

internal fun formatDateTime(value: Long): String = runCatching {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
}.getOrDefault("—")

internal fun formatSize(bytes: Long): String = if (bytes < 0) "—" else {
    val megabytes = bytes / (1024.0 * 1024.0)
    String.format(Locale.getDefault(), "%.1f MB", megabytes)
}

internal fun percent(done: Long, total: Long): Int =
    if (total <= 0L) 0 else ((done * 100L) / total).coerceIn(0L, 100L).toInt()

internal suspend fun FocusRequester.requestUpdateFocusWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
