package com.whatuneed.ovisiontv.kotlin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.whatuneed.ovisiontv.kotlin.BuildConfig
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.update.UpdateChannel
import com.whatuneed.ovisiontv.kotlin.update.UpdateCheckMode
import com.whatuneed.ovisiontv.kotlin.update.UpdateError
import com.whatuneed.ovisiontv.kotlin.update.UpdateManager
import com.whatuneed.ovisiontv.kotlin.update.UpdatePhase
import com.whatuneed.ovisiontv.kotlin.update.UpdateRelease
import com.whatuneed.ovisiontv.kotlin.update.UpdateState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class UpdateRow(
    val title: String,
    val value: String? = null,
    val action: (() -> Unit)? = null,
    val destructive: Boolean = false,
)

@Composable
fun SoftwareUpdateScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scale = rememberTvScale()
    val manager = remember(context) { UpdateManager(context) }
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var focusIndex by rememberSaveable { mutableIntStateOf(0) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            manager.restore()
            manager.cleanup()
        }
    }
    DisposableEffect(manager) {
        onDispose { downloadJob?.cancel() }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        manager.markDownloadCancelled()
    }

    BackHandler {
        when {
            notesOpen -> notesOpen = false
            state.phase == UpdatePhase.DOWNLOADING -> {
                cancelDownload()
                onClose()
            }

            else -> onClose()
        }
    }

    val release = state.available
    val rows = buildList {
        add(
            UpdateRow(
                title = stringResource(R.string.update_current),
                value = stringResource(
                    R.string.update_version_value,
                    state.installedVersionName.ifBlank { BuildConfig.VERSION_NAME },
                    state.installedVersionCode.takeIf { it > 0 } ?: BuildConfig.VERSION_CODE,
                ),
            ),
        )
        add(
            UpdateRow(
                title = stringResource(R.string.update_channel),
                value = stringResource(
                    if (state.channel == UpdateChannel.STABLE) {
                        R.string.update_channel_stable
                    } else {
                        R.string.update_channel_beta
                    },
                ),
                action = if (state.busy) {
                    null
                } else {
                    {
                        manager.setChannel(
                            if (state.channel == UpdateChannel.STABLE) {
                                UpdateChannel.BETA
                            } else {
                                UpdateChannel.STABLE
                            },
                        )
                    }
                },
            ),
        )
        add(
            UpdateRow(
                title = stringResource(R.string.update_auto),
                value = stringResource(
                    when (state.mode) {
                        UpdateCheckMode.MANUAL -> R.string.update_auto_manual
                        UpdateCheckMode.DAILY -> R.string.update_auto_daily
                        UpdateCheckMode.WEEKLY -> R.string.update_auto_weekly
                    },
                ),
                action = if (state.busy) {
                    null
                } else {
                    {
                        manager.setMode(
                            when (state.mode) {
                                UpdateCheckMode.MANUAL -> UpdateCheckMode.DAILY
                                UpdateCheckMode.DAILY -> UpdateCheckMode.WEEKLY
                                UpdateCheckMode.WEEKLY -> UpdateCheckMode.MANUAL
                            },
                        )
                    }
                },
            ),
        )
        add(
            UpdateRow(
                title = stringResource(R.string.update_last_check),
                value = state.lastCheckMs?.let(::formatDateTime)
                    ?: stringResource(R.string.update_never),
            ),
        )

        if (!state.sourceConfigured) {
            add(UpdateRow(stringResource(R.string.update_source_missing)))
            add(UpdateRow(stringResource(R.string.update_source_missing_hint)))
        } else {
            when (state.phase) {
                UpdatePhase.NOT_CONFIGURED -> {
                    add(UpdateRow(stringResource(R.string.update_source_missing)))
                }

                UpdatePhase.IDLE -> {
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_check),
                            action = if (state.busy) null else ({ scope.launch { manager.check(force = true) } }),
                        ),
                    )
                }

                UpdatePhase.CHECKING -> add(UpdateRow(stringResource(R.string.update_checking)))

                UpdatePhase.UP_TO_DATE -> {
                    add(UpdateRow(stringResource(R.string.update_up_to_date)))
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_check),
                            action = { scope.launch { manager.check(force = true) } },
                        ),
                    )
                }

                UpdatePhase.AVAILABLE -> {
                    if (release != null) {
                        add(UpdateRow(stringResource(R.string.update_available)))
                        add(UpdateRow(stringResource(R.string.update_release_date), formatDate(release.releaseDate)))
                        add(UpdateRow(stringResource(R.string.update_size), formatSize(release.asset.sizeBytes)))
                        add(
                            UpdateRow(
                                title = stringResource(R.string.update_notes),
                                action = { notesOpen = true },
                            ),
                        )
                        if (release.minSupportedVersionCode > state.installedVersionCode) {
                            add(UpdateRow(stringResource(R.string.update_eol)))
                        }
                        add(
                            UpdateRow(
                                title = stringResource(R.string.update_download),
                                action = {
                                    downloadJob = scope.launch { manager.downloadCurrent() }
                                },
                            ),
                        )
                    }
                }

                UpdatePhase.DOWNLOADING -> {
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_downloading_bytes, formatSize(state.progressBytes)),
                            value = state.totalBytes?.let {
                                stringResource(
                                    R.string.update_downloading,
                                    percent(state.progressBytes, it),
                                )
                            },
                        ),
                    )
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_cancel),
                            action = { cancelDownload() },
                            destructive = true,
                        ),
                    )
                }

                UpdatePhase.VERIFYING -> add(UpdateRow(stringResource(R.string.update_verifying)))

                UpdatePhase.READY -> {
                    add(UpdateRow(stringResource(R.string.update_ready)))
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_install),
                            action = { scope.launch { manager.installCurrent() } },
                        ),
                    )
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_notes),
                            action = { notesOpen = true },
                        ),
                    )
                }

                UpdatePhase.INSTALL_PENDING -> add(
                    UpdateRow(stringResource(R.string.update_install_pending)),
                )

                UpdatePhase.INSTALLED -> {
                    add(UpdateRow(stringResource(R.string.update_installed)))
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_check),
                            action = { scope.launch { manager.check(force = true) } },
                        ),
                    )
                }

                UpdatePhase.FAILED -> {
                    val error = state.error
                    add(UpdateRow(errorTitle(error, state), destructive = error == UpdateError.SIGNATURE))
                    if (error == UpdateError.PERMISSION) {
                        add(UpdateRow(stringResource(R.string.update_permission_hint)))
                        add(
                            UpdateRow(
                                title = stringResource(R.string.update_permission_open),
                                action = { manager.openInstallPermission() },
                            ),
                        )
                        if (manager.apkFile()?.isFile == true && manager.canInstall()) {
                            add(
                                UpdateRow(
                                    title = stringResource(R.string.update_install),
                                    action = { scope.launch { manager.installCurrent() } },
                                ),
                            )
                        }
                    } else {
                        if (release != null) {
                            add(
                                UpdateRow(
                                    title = stringResource(R.string.update_retry),
                                    action = {
                                        if (manager.apkFile()?.isFile == true) {
                                            scope.launch { manager.installCurrent() }
                                        } else {
                                            downloadJob = scope.launch { manager.downloadCurrent() }
                                        }
                                    },
                                ),
                            )
                        }
                    }
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_check),
                            action = { scope.launch { manager.check(force = true) } },
                        ),
                    )
                }

                UpdatePhase.INCOMPATIBLE -> {
                    add(UpdateRow(stringResource(R.string.update_incompatible), destructive = true))
                    add(UpdateRow(incompatibilityText(state)))
                    add(
                        UpdateRow(
                            title = stringResource(R.string.update_check),
                            action = { scope.launch { manager.check(force = true) } },
                        ),
                    )
                }
            }
        }
    }

    LaunchedEffect(rows.size, state.phase) {
        if (focusIndex !in rows.indices) focusIndex = 0
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101014), Color(0xFF0E0E11)))),
    ) {
        Column(
            Modifier
                .offset(x = scale.dp(TvOs.SAFE_AREA), y = scale.dp(54f))
                .width(scale.dp(1200f)),
        ) {
            Text(
                text = stringResource(R.string.update_title),
                color = Color.White,
                fontSize = scale.sp(TvType.largeTitle),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.update_subtitle),
                color = LauncherColors.textSecondary,
                fontSize = scale.sp(TvType.callout),
                modifier = Modifier.padding(top = scale.dp(8f)),
            )
            Spacer(Modifier.height(scale.dp(22f)))
            val downloadTotal = state.totalBytes
            if (state.phase == UpdatePhase.DOWNLOADING && downloadTotal != null) {
                LinearProgressIndicator(
                    progress = {
                        (state.progressBytes.toFloat() / downloadTotal.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .width(scale.dp(1200f))
                        .height(scale.dp(6f))
                        .testTag("update_progress"),
                    color = Color.White.copy(alpha = 0.75f),
                    trackColor = Color.White.copy(alpha = 0.10f),
                )
                Spacer(Modifier.height(scale.dp(14f)))
            }
            LazyColumn(
                modifier = Modifier
                    .width(scale.dp(1200f))
                    .height(scale.dp(700f))
                    .testTag("update_rows"),
                verticalArrangement = Arrangement.spacedBy(scale.dp(8f)),
            ) {
                itemsIndexed(rows) { index, row ->
                    val requester = remember(state.phase, index) { FocusRequester() }
                    if (index == focusIndex) {
                        LaunchedEffect(state.phase, index) { requester.requestWhenAttached() }
                    }
                    UpdateRowView(
                        row = row,
                        focused = index == focusIndex,
                        scale = scale,
                        requester = requester,
                        onFocus = { focusIndex = index },
                    )
                }
            }
        }
        if (notesOpen) {
            UpdateNotesOverlay(
                release = release,
                onClose = { notesOpen = false },
            )
        }
    }
}

@Composable
private fun UpdateNotesOverlay(release: UpdateRelease?, onClose: () -> Unit) {
    val scale = rememberTvScale()
    val requester = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { requester.requestWhenAttached() }
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
private fun UpdateRowView(
    row: UpdateRow,
    focused: Boolean,
    scale: TvScale,
    requester: FocusRequester,
    onFocus: () -> Unit,
) {
    val shape = RoundedCornerShape(scale.dp(14f))
    Row(
        Modifier
            .width(scale.dp(1200f))
            .height(scale.dp(66f))
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable { row.action?.invoke() }
            .background(
                if (focused) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.075f),
                shape,
            )
            .border(
                scale.dp(if (focused) 2f else 1f),
                Color.White.copy(alpha = if (focused) 0.8f else 0.10f),
                shape,
            )
            .padding(horizontal = scale.dp(20f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.title,
            color = if (row.destructive) LauncherColors.destructive else Color.White,
            fontSize = scale.sp(TvType.body),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        row.value?.let {
            Text(
                text = it,
                color = LauncherColors.textSecondary,
                fontSize = scale.sp(TvType.callout),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun errorTitle(error: UpdateError?, state: UpdateState): String = stringResource(
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
private fun incompatibilityText(state: UpdateState): String = stringResource(
    when (state.errorDetail) {
        "ANDROID_TOO_OLD" -> R.string.update_incompatible_api
        "ABI_UNSUPPORTED" -> R.string.update_incompatible_abi
        else -> R.string.update_incompatible_version
    },
    state.available?.minAndroidApi ?: 28,
)

private fun formatDate(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
        .format(Instant.parse(value).atZone(ZoneId.systemDefault()))
}.getOrDefault(value)

private fun formatDateTime(value: Long): String = runCatching {
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))
}.getOrDefault("—")

private fun formatSize(bytes: Long): String = if (bytes < 0) "—" else {
    val megabytes = bytes / (1024.0 * 1024.0)
    String.format(Locale.getDefault(), "%.1f MB", megabytes)
}

private fun percent(done: Long, total: Long): Int =
    if (total <= 0L) 0 else ((done * 100L) / total).coerceIn(0L, 100L).toInt()

private suspend fun FocusRequester.requestWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}
