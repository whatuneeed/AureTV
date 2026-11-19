package com.whatuneed.ovisiontv.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ui.components.TV_TOOL_LIST_WIDTH
import com.whatuneed.ovisiontv.kotlin.ui.components.TvListRowSurface
import com.whatuneed.ovisiontv.kotlin.backup.BackupCategory
import com.whatuneed.ovisiontv.kotlin.backup.BackupIndexEntry
import com.whatuneed.ovisiontv.kotlin.backup.BackupInfo
import com.whatuneed.ovisiontv.kotlin.backup.BackupSelection
import com.whatuneed.ovisiontv.kotlin.backup.BackupWarning
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun categoryLabel(category: BackupCategory): String = stringResource(
    when (category) {
        BackupCategory.PROFILES -> R.string.backup_category_profiles
        BackupCategory.HOME -> R.string.backup_category_home
        BackupCategory.DISCOVERY -> R.string.backup_category_discovery
        BackupCategory.KIDS -> R.string.backup_category_kids
        BackupCategory.WEATHER -> R.string.backup_category_weather
        BackupCategory.SAVER -> R.string.backup_category_saver
        BackupCategory.LANGUAGE -> R.string.backup_category_language
        BackupCategory.CONTENT -> R.string.backup_category_content
        BackupCategory.STARTUP -> R.string.backup_category_startup
    },
)

@Composable
internal fun warningText(warning: BackupWarning): String = stringResource(
    when (warning) {
        BackupWarning.MISSING_APPS -> R.string.backup_warning_missing_apps
        BackupWarning.KIDS_PIN_RESET -> R.string.backup_warning_kids
        BackupWarning.PERMISSIONS_NOT_RESTORED -> R.string.backup_warning_permissions
        BackupWarning.DISCOVERY_REQUIRES_ENCRYPTION -> R.string.backup_warning_discovery
        BackupWarning.WEATHER_REQUIRES_ENCRYPTION -> R.string.backup_warning_weather
        BackupWarning.UNKNOWN_COMPONENT,
        BackupWarning.REPAIRED,
        BackupWarning.SOURCE_UNREADABLE,
        -> R.string.backup_warning_repaired

        BackupWarning.EMPTY -> R.string.backup_warning_empty
        BackupWarning.PROFILE_LIMIT -> R.string.backup_warning_profile_limit
    },
)

@Composable
internal fun PasswordFields(
    ui: BackupUi,
    scale: TvScale,
    requester: FocusRequester,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.backup_password_hint),
        color = LauncherColors.textSecondary,
        fontSize = scale.sp(TvType.callout),
    )
    Spacer(Modifier.height(scale.dp(12f)))
    PasswordField(
        label = stringResource(R.string.backup_password),
        value = ui.password,
        scale = scale,
        requester = requester,
        tag = "backup_password",
        onValueChange = onPassword,
    )
    Spacer(Modifier.height(scale.dp(10f)))
    PasswordField(
        label = stringResource(R.string.backup_password_confirm),
        value = ui.passwordConfirm,
        scale = scale,
        tag = "backup_password_confirm",
        onValueChange = onConfirm,
    )
}

@Composable
internal fun PasswordField(
    label: String,
    value: String,
    scale: TvScale,
    requester: FocusRequester? = null,
    tag: String? = null,
    onValueChange: (String) -> Unit,
) {
    val focus = requester ?: remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    if (requester != null) {
        LaunchedEffect(focus) {
            // The text field is the focus target, not the surrounding card;
            // ask again after its first layout instead of only from the page.
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
    Column(
        Modifier
            .width(scale.dp(760f))
            .background(
                Color.White.copy(alpha = if (focused) 0.16f else 0.08f),
                RoundedCornerShape(scale.dp(14f)),
            )
            .border(
                scale.dp(if (focused) 2f else 1f),
                Color.White.copy(alpha = if (focused) 0.7f else 0.18f),
                RoundedCornerShape(scale.dp(14f)),
            )
            .padding(horizontal = scale.dp(18f), vertical = scale.dp(10f))
            .semantics { contentDescription = label },
    ) {
        Text(
            text = label,
            color = LauncherColors.textSecondary,
            fontSize = scale.sp(TvType.caption),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = scale.sp(TvType.body),
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = scale.dp(4f))
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

@Composable
internal fun BackupRowView(
    row: BackupRow,
    focused: Boolean,
    scale: TvScale,
    requester: FocusRequester,
    onFocus: () -> Unit,
    onClick: (() -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TvListRowSurface(
        focused = focused,
        scale = scale,
        modifier = Modifier
            .width(scale.dp(TV_TOOL_LIST_WIDTH))
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            // Every row is a focus target even when it only carries a warning;
            // an info row with no action must still be reachable and must not
            // leave the page without an initial focus owner. The default
            // indication would tint the focused white row grey.
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick?.invoke()
            },
    ) { focusAmount ->
        val titleColor = if (row.destructive) {
            LauncherColors.destructive
        } else {
            lerp(Color.White.copy(alpha = 0.95f), Color.Black, focusAmount)
        }
        val valueColor = lerp(
            LauncherColors.textSecondary,
            Color.Black.copy(alpha = 0.6f),
            focusAmount,
        )
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
            row.checked?.let {
                Text(
                    text = stringResource(if (it) R.string.saver_on else R.string.saver_off),
                    color = valueColor,
                    fontSize = scale.sp(TvType.callout),
                )
            } ?: row.value?.let {
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

internal fun Set<BackupCategory>.toSelection(): BackupSelection =
    BackupCategory.entries.fold(BackupSelection()) { selection, category ->
        selection.with(category, category in this)
    }

internal fun displayLastLabel(entry: BackupIndexEntry?): String = entry?.let(::lastLabel) ?: "—"

internal fun lastLabel(entry: BackupIndexEntry): String = lastLabel(entry.info)

internal fun lastLabel(info: BackupInfo): String = runCatching {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    formatter.format(Instant.parse(info.createdAt).atZone(ZoneId.systemDefault()))
}.getOrDefault(info.createdAt.ifBlank { "—" })

// A requester created by this composition is not attached until the next
// layout pass. Retry across a few frames instead of dropping the first focus.
internal suspend fun FocusRequester.requestBackupFocusWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}

internal fun fileName(): String {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        .format(Instant.now().atZone(ZoneId.systemDefault()))
    return "ovisiontv-backup-$stamp.ovbk"
}
