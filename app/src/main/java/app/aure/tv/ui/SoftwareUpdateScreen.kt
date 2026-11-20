package app.aure.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.aure.tv.core.designsystem.SectionGlyph
import app.aure.tv.BuildConfig
import app.aure.tv.R
import app.aure.tv.ui.components.TV_TOOL_LIST_WIDTH
import app.aure.tv.ui.components.TvToolPageScaffold
import app.aure.tv.data.KidsMode
import app.aure.tv.update.UpdateChannel
import app.aure.tv.update.UpdateCheckMode
import app.aure.tv.update.UpdateError
import app.aure.tv.update.UpdateManager
import app.aure.tv.update.UpdatePhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class UpdateRow(
    val title: String,
    val value: String? = null,
    val action: (() -> Unit)? = null,
    val destructive: Boolean = false,
    // Read-only status rows stay visible but out of the focus order. A page
    // with no actions keeps the installed-version row focusable so the remote
    // still has an owner and TalkBack can start reading.
    val focusable: Boolean = action != null,
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
    val kidsActive = remember(context) { KidsMode.isKids(context) }

    LaunchedEffect(Unit) {
        if (!kidsActive) {
            withContext(Dispatchers.IO) {
                manager.restore()
                manager.cleanup()
            }
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
        val currentRow = UpdateRow(
            title = stringResource(R.string.update_current),
            value = stringResource(
                R.string.update_version_value,
                state.installedVersionName.ifBlank { BuildConfig.VERSION_NAME },
                state.installedVersionCode.takeIf { it > 0 } ?: BuildConfig.VERSION_CODE,
            ),
            focusable = true,
        )
        if (kidsActive) {
            add(currentRow)
            add(UpdateRow(stringResource(R.string.update_kids_blocked)))
            return@buildList
        }
        add(currentRow)
        if (!state.sourceConfigured) {
            add(UpdateRow(stringResource(R.string.update_source_missing)))
            add(UpdateRow(stringResource(R.string.update_source_missing_hint)))
            return@buildList
        }
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

    LaunchedEffect(rows.size, state.phase) {
        if (focusIndex !in rows.indices || !rows[focusIndex].focusable) {
            focusIndex = rows.indexOfFirst { it.focusable }.coerceAtLeast(0)
        }
    }

    TvToolPageScaffold(
        title = stringResource(R.string.update_title),
        subtitle = stringResource(R.string.update_subtitle),
        icon = SectionGlyph.update,
        scale = scale,
    ) {
        Column(Modifier.fillMaxSize()) {
            val downloadTotal = state.totalBytes
            if (state.phase == UpdatePhase.DOWNLOADING && downloadTotal != null) {
                LinearProgressIndicator(
                    progress = {
                        (state.progressBytes.toFloat() / downloadTotal.toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .width(scale.dp(TV_TOOL_LIST_WIDTH))
                        .height(scale.dp(6f))
                        .testTag("update_progress"),
                    color = Color.White.copy(alpha = 0.75f),
                    trackColor = Color.White.copy(alpha = 0.10f),
                )
                Spacer(Modifier.height(scale.dp(14f)))
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("update_rows"),
                verticalArrangement = Arrangement.spacedBy(scale.dp(8f)),
            ) {
                itemsIndexed(rows) { index, row ->
                    val requester = remember(state.phase, index) { FocusRequester() }
                    if (row.focusable && index == focusIndex) {
                        LaunchedEffect(state.phase, index) { requester.requestUpdateFocusWhenAttached() }
                    }
                    UpdateRowView(
                        row = row,
                        focused = row.focusable && index == focusIndex,
                        scale = scale,
                        requester = requester,
                        onFocus = { focusIndex = index },
                    )
                }
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
