package com.whatuneed.ovisiontv.kotlin.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.whatuneed.ovisiontv.kotlin.MainActivity
import com.whatuneed.ovisiontv.kotlin.R
import com.whatuneed.ovisiontv.kotlin.ui.components.SectionGlyph
import com.whatuneed.ovisiontv.kotlin.ui.components.TV_TOOL_LIST_WIDTH
import com.whatuneed.ovisiontv.kotlin.ui.components.TvListRowSurface
import com.whatuneed.ovisiontv.kotlin.ui.components.TvToolPageScaffold
import com.whatuneed.ovisiontv.kotlin.backup.BackupCategory
import com.whatuneed.ovisiontv.kotlin.backup.BackupContract
import com.whatuneed.ovisiontv.kotlin.backup.BackupException
import com.whatuneed.ovisiontv.kotlin.backup.BackupIndexEntry
import com.whatuneed.ovisiontv.kotlin.backup.BackupInfo
import com.whatuneed.ovisiontv.kotlin.backup.BackupManager
import com.whatuneed.ovisiontv.kotlin.backup.BackupPreview
import com.whatuneed.ovisiontv.kotlin.backup.BackupScheduler
import com.whatuneed.ovisiontv.kotlin.backup.BackupSelection
import com.whatuneed.ovisiontv.kotlin.backup.BackupStage
import com.whatuneed.ovisiontv.kotlin.backup.BackupTrigger
import com.whatuneed.ovisiontv.kotlin.backup.BackupWarning
import com.whatuneed.ovisiontv.kotlin.data.WeatherPreferences
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class BackupPage { HOME, CREATE, SOURCE, PREVIEW, PROGRESS, SETTINGS, PASSWORD }
private enum class PasswordPurpose { EXPORT, IMPORT }

private data class BackupUi(
    val page: BackupPage = BackupPage.HOME,
    val selection: BackupSelection = BackupSelection(),
    val encrypt: Boolean = false,
    val entries: List<BackupIndexEntry> = emptyList(),
    val preview: BackupPreview? = null,
    val last: BackupIndexEntry? = null,
    val message: String? = null,
    val working: String? = null,
    val password: String = "",
    val passwordConfirm: String = "",
    val passwordPurpose: PasswordPurpose = PasswordPurpose.EXPORT,
    val passwordReturn: BackupPage = BackupPage.CREATE,
    val pendingUri: Uri? = null,
    val pendingSelection: BackupSelection = BackupSelection(),
    val pendingPassword: String = "",
    val passwordError: String? = null,
)

private data class BackupRow(
    val title: String,
    val value: String? = null,
    val checked: Boolean? = null,
    val destructive: Boolean = false,
    val action: (() -> Unit)? = null,
)

@Composable
fun BackupScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scale = rememberTvScale()
    val manager = remember(context) { BackupManager(context) }
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf(BackupUi()) }
    var focusIndex by rememberSaveable { mutableIntStateOf(0) }
    var autoEnabled by remember { mutableStateOf(BackupScheduler.isAutoEnabled(context)) }
    val passwordRequester = remember { FocusRequester() }

    fun refresh() {
        scope.launch {
            val entries = manager.list()
            ui = ui.copy(entries = entries, last = entries.firstOrNull())
        }
    }

    LaunchedEffect(Unit) {
        // Also picks up a copy written by the alarm while this screen was away.
        refresh()
    }

    fun fail(stage: BackupStage, error: Throwable) {
        val message = when {
            error is BackupException && error.stage == BackupStage.CRYPTO ->
                context.getString(R.string.backup_password_wrong)
            error is BackupException -> error.message.orEmpty()
            else -> error.message.orEmpty()
        }
        ui = ui.copy(
            page = BackupPage.HOME,
            working = null,
            message = context.getString(R.string.backup_error, message),
            password = "",
            passwordConfirm = "",
            pendingPassword = "",
        )
        if (error is BackupException) {
            android.util.Log.w("BackupScreen", "backup failed at ${stage.name}", error)
        }
    }

    fun performExport(uri: Uri) {
        val selection = ui.pendingSelection
        val password = ui.pendingPassword.takeIf { it.isNotEmpty() }
        ui = ui.copy(
            page = BackupPage.PROGRESS,
            working = context.getString(R.string.backup_stage_export),
        )
        scope.launch {
            try {
                manager.export(uri, selection, password?.toCharArray())
                Toast.makeText(context, R.string.backup_export_done, Toast.LENGTH_SHORT).show()
                ui = ui.copy(
                    page = BackupPage.HOME,
                    working = null,
                    message = null,
                    password = "",
                    passwordConfirm = "",
                    pendingPassword = "",
                )
                refresh()
            } catch (error: Throwable) {
                fail(BackupStage.IO, error)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            performExport(uri)
        } else {
            ui = ui.copy(
                page = ui.passwordReturn,
                working = null,
                pendingPassword = "",
                password = "",
                passwordConfirm = "",
            )
        }
    }

    fun loadImport(uri: Uri, password: String?) {
        ui = ui.copy(
            page = BackupPage.PROGRESS,
            working = context.getString(R.string.backup_stage_read),
            pendingUri = uri,
        )
        scope.launch {
            try {
                val preview = manager.previewUri(uri, password?.toCharArray())
                ui = ui.copy(
                    page = BackupPage.PREVIEW,
                    preview = preview,
                    working = null,
                    pendingPassword = password.orEmpty(),
                )
            } catch (error: Throwable) {
                if (error is BackupException && error.stage == BackupStage.CRYPTO && password == null) {
                    ui = ui.copy(
                        page = BackupPage.PASSWORD,
                        passwordPurpose = PasswordPurpose.IMPORT,
                        passwordReturn = BackupPage.HOME,
                        pendingUri = uri,
                        pendingPassword = "",
                        working = null,
                        passwordError = context.getString(R.string.backup_password_required),
                    )
                } else {
                    fail(BackupStage.CRYPTO, error)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) loadImport(uri, null)
    }

    fun loadLocal(entry: BackupIndexEntry) {
        ui = ui.copy(
            page = BackupPage.PROGRESS,
            working = context.getString(R.string.backup_stage_read),
        )
        scope.launch {
            try {
                val preview = manager.previewLocal(entry, null)
                ui = ui.copy(page = BackupPage.PREVIEW, preview = preview, working = null)
            } catch (error: Throwable) {
                fail(BackupStage.FORMAT, error)
            }
        }
    }

    fun restore() {
        val preview = ui.preview ?: return
        ui = ui.copy(
            page = BackupPage.PROGRESS,
            working = context.getString(R.string.backup_stage_apply),
        )
        scope.launch {
            try {
                manager.restore(preview, preview.categories.toSelection())
                Toast.makeText(context, R.string.backup_restore_done, Toast.LENGTH_LONG).show()
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK,
                    ),
                )
                (context as? Activity)?.finish()
            } catch (error: Throwable) {
                fail(BackupStage.RESTORE, error)
            }
        }
    }

    fun requestExport() {
        val needsPassword = ui.encrypt || ui.selection.discovery ||
            WeatherPreferences.manual(context)
        ui = ui.copy(
            pendingSelection = ui.selection,
            pendingPassword = ui.password,
            passwordPurpose = PasswordPurpose.EXPORT,
            passwordReturn = ui.page,
            passwordError = null,
        )
        if (needsPassword) {
            ui = ui.copy(page = BackupPage.PASSWORD, encrypt = true)
        } else {
            exportLauncher.launch(fileName())
        }
    }

    fun beginCreate() {
        ui = ui.copy(page = BackupPage.CREATE, message = null)
        focusIndex = 0
    }

    fun back() {
        ui = when (ui.page) {
            BackupPage.HOME -> {
                onClose()
                return
            }

            BackupPage.PASSWORD -> ui.copy(
                page = ui.passwordReturn,
                password = "",
                passwordConfirm = "",
            )

            BackupPage.PREVIEW -> ui.copy(
                page = BackupPage.SOURCE,
                preview = null,
                password = "",
                passwordConfirm = "",
                pendingPassword = "",
            )
            else -> ui.copy(page = BackupPage.HOME, password = "", passwordConfirm = "")
        }
        focusIndex = 0
    }

    BackHandler {
        // Restore has already started writing files; letting BACK finish the
        // activity would hide the only surface that can report the outcome.
        if (ui.page != BackupPage.PROGRESS) back()
    }

    val rows = when (ui.page) {
        BackupPage.HOME -> buildList {
            add(BackupRow(stringResource(R.string.backup_create)) { beginCreate() })
            add(BackupRow(stringResource(R.string.backup_export)) { requestExport() })
            add(
                BackupRow(stringResource(R.string.backup_restore_local)) {
                    ui = ui.copy(page = BackupPage.SOURCE)
                    refresh()
                },
            )
            add(
                BackupRow(stringResource(R.string.backup_import)) {
                    runCatching {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }.onFailure {
                        Toast.makeText(context, R.string.backup_no_files, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            add(
                BackupRow(
                    title = stringResource(R.string.backup_auto),
                    value = stringResource(if (autoEnabled) R.string.saver_on else R.string.saver_off),
                    checked = autoEnabled,
                    action = {
                        autoEnabled = !autoEnabled
                        BackupScheduler.setAutoEnabled(context, autoEnabled)
                    },
                ),
            )
            add(
                BackupRow(stringResource(R.string.backup_settings)) {
                    ui = ui.copy(page = BackupPage.SETTINGS)
                    focusIndex = 0
                },
            )
            add(
                BackupRow(
                    title = stringResource(R.string.backup_last, displayLastLabel(ui.last)),
                    value = ui.last?.let { "${it.info.componentCount} · ${it.info.bytes / 1024} KB" },
                ),
            )
            ui.message?.let { add(BackupRow(it)) }
        }

        BackupPage.CREATE -> buildList {
            BackupCategory.entries.forEach { category ->
                add(
                    BackupRow(
                        title = categoryLabel(category),
                        checked = ui.selection.includes(category),
                        action = {
                            ui = ui.copy(
                                selection = ui.selection.with(
                                    category,
                                    !ui.selection.includes(category),
                                ),
                            )
                        },
                    ),
                )
            }
            add(
                BackupRow(
                    title = stringResource(R.string.backup_encrypt),
                    value = stringResource(if (ui.encrypt) R.string.saver_on else R.string.saver_off),
                    checked = ui.encrypt,
                    action = { ui = ui.copy(encrypt = !ui.encrypt) },
                ),
            )
            add(
                BackupRow(stringResource(R.string.backup_save_local)) {
                    val selection = ui.selection
                    if (!selection.isEmpty()) {
                        ui = ui.copy(
                            page = BackupPage.PROGRESS,
                            working = context.getString(R.string.backup_stage_export),
                        )
                        scope.launch {
                            try {
                                manager.createLocal(selection, BackupTrigger.MANUAL)
                                Toast.makeText(context, R.string.backup_local_done, Toast.LENGTH_SHORT).show()
                                ui = ui.copy(page = BackupPage.HOME, working = null)
                                refresh()
                            } catch (error: Throwable) {
                                fail(BackupStage.IO, error)
                            }
                        }
                    }
                },
            )
            add(BackupRow(stringResource(R.string.backup_export_now)) { requestExport() })
        }

        BackupPage.SOURCE -> buildList {
            if (ui.entries.isEmpty()) {
                add(BackupRow(stringResource(R.string.backup_no_copies)))
            } else {
                ui.entries.forEach { entry ->
                    add(
                        BackupRow(
                            title = lastLabel(entry),
                            value = (if (entry.info.encrypted) {
                                stringResource(R.string.backup_encrypted)
                            } else {
                                stringResource(R.string.backup_plain)
                            }) + " · " + stringResource(R.string.backup_schema, entry.info.dataSchema),
                            action = { loadLocal(entry) },
                        ),
                    )
                }
            }
            add(
                BackupRow(stringResource(R.string.backup_import)) {
                    runCatching {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }.onFailure {
                        Toast.makeText(context, R.string.backup_no_files, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        BackupPage.PREVIEW -> buildList {
            val preview = ui.preview
            if (preview != null) {
                add(
                    BackupRow(
                        title = lastLabel(preview.info),
                        value = if (preview.encrypted) {
                            stringResource(R.string.backup_encrypted)
                        } else {
                            stringResource(R.string.backup_plain)
                        },
                    ),
                )
                preview.warnings.forEach { warning ->
                    add(BackupRow(warningText(warning)))
                }
                add(BackupRow(stringResource(R.string.backup_restore_confirm)))
                add(
                    BackupRow(
                        title = stringResource(R.string.backup_restore_selected),
                        destructive = true,
                        action = { restore() },
                    ),
                )
            }
        }

        BackupPage.SETTINGS -> buildList {
            add(
                BackupRow(
                    title = stringResource(R.string.backup_auto),
                    value = stringResource(if (autoEnabled) R.string.saver_on else R.string.saver_off),
                    checked = autoEnabled,
                    action = {
                        autoEnabled = !autoEnabled
                        BackupScheduler.setAutoEnabled(context, autoEnabled)
                    },
                ),
            )
            add(
                BackupRow(
                    title = stringResource(R.string.backup_retention, BackupContract.LOCAL_RETENTION),
                    value = stringResource(R.string.backup_auto_hint),
                ),
            )
        }

        BackupPage.PASSWORD -> buildList {
            add(
                BackupRow(stringResource(R.string.backup_password_continue)) {
                    if (ui.password.length < BackupContract.PASSWORD_MIN_CHARS) {
                        ui = ui.copy(passwordError = context.getString(R.string.backup_password_too_short))
                    } else if (ui.password != ui.passwordConfirm) {
                        ui = ui.copy(
                            passwordError = context.getString(R.string.backup_password_mismatch),
                        )
                    } else if (ui.passwordPurpose == PasswordPurpose.IMPORT) {
                        val uri = ui.pendingUri
                        if (uri != null) {
                            loadImport(uri, ui.password)
                        } else {
                            fail(BackupStage.IO, IllegalStateException("file"))
                        }
                    } else {
                        ui = ui.copy(
                            pendingSelection = ui.selection,
                            pendingPassword = ui.password,
                            page = BackupPage.PROGRESS,
                            working = context.getString(R.string.backup_stage_export),
                        )
                        exportLauncher.launch(fileName())
                    }
                },
            )
        }

        BackupPage.PROGRESS -> emptyList()
    }

    LaunchedEffect(ui.page) {
        // On the password page the text field owns the first focus; -1 keeps
        // the Continue row from stealing it on the first composition.
        focusIndex = if (ui.page == BackupPage.PASSWORD) -1 else 0
        if (ui.page == BackupPage.PASSWORD) passwordRequester.requestWhenAttached()
    }
    LaunchedEffect(ui.page, focusIndex, rows.size) {
        if (ui.page != BackupPage.PASSWORD && focusIndex !in rows.indices) {
            focusIndex = rows.lastIndex.coerceAtLeast(0)
        }
    }

    TvToolPageScaffold(
        title = stringResource(R.string.backup_title),
        subtitle = stringResource(R.string.backup_subtitle),
        icon = SectionGlyph.shield,
        scale = scale,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (ui.page == BackupPage.PASSWORD) {
                PasswordFields(
                    ui = ui,
                    scale = scale,
                    requester = passwordRequester,
                    onPassword = { ui = ui.copy(password = it, passwordError = null) },
                    onConfirm = { ui = ui.copy(passwordConfirm = it, passwordError = null) },
                )
                ui.passwordError?.let {
                    Text(
                        text = it,
                        color = LauncherColors.destructive,
                        fontSize = scale.sp(TvType.callout),
                        modifier = Modifier.padding(top = scale.dp(8f)),
                    )
                }
                Spacer(Modifier.height(scale.dp(12f)))
            }
            if (ui.page == BackupPage.PROGRESS) {
                Column(Modifier.padding(top = scale.dp(24f))) {
                    Text(
                        text = ui.working ?: stringResource(R.string.backup_working),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = scale.sp(TvType.body),
                    )
                    Spacer(Modifier.height(scale.dp(14f)))
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.8f),
                        strokeWidth = scale.dp(4f),
                        modifier = Modifier.width(scale.dp(52f)),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("backup_rows"),
                    verticalArrangement = Arrangement.spacedBy(scale.dp(8f)),
                ) {
                    itemsIndexed(rows) { index, row ->
                        val requester = remember(ui.page, index) { FocusRequester() }
                        if (index == focusIndex) {
                            LaunchedEffect(ui.page, index) { requester.requestWhenAttached() }
                        }
                        BackupRowView(
                            row = row,
                            focused = index == focusIndex,
                            scale = scale,
                            requester = requester,
                            onFocus = { focusIndex = index },
                            onClick = row.action,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: BackupCategory): String = stringResource(
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
private fun warningText(warning: BackupWarning): String = stringResource(
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
private fun PasswordFields(
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
private fun PasswordField(
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
private fun BackupRowView(
    row: BackupRow,
    focused: Boolean,
    scale: TvScale,
    requester: FocusRequester,
    onFocus: () -> Unit,
    onClick: (() -> Unit)?,
) {
    TvListRowSurface(
        focused = focused,
        scale = scale,
        modifier = Modifier
            .width(scale.dp(TV_TOOL_LIST_WIDTH))
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            // Every row is a focus target even when it only carries a warning;
            // an info row with no action must still be reachable and must not
            // leave the page without an initial focus owner.
            .clickable { onClick?.invoke() },
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

private fun Set<BackupCategory>.toSelection(): BackupSelection =
    BackupCategory.entries.fold(BackupSelection()) { selection, category ->
        selection.with(category, category in this)
    }

private fun displayLastLabel(entry: BackupIndexEntry?): String = entry?.let(::lastLabel) ?: "—"

private fun lastLabel(entry: BackupIndexEntry): String = lastLabel(entry.info)

private fun lastLabel(info: BackupInfo): String = runCatching {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    formatter.format(Instant.parse(info.createdAt).atZone(ZoneId.systemDefault()))
}.getOrDefault(info.createdAt.ifBlank { "—" })

// A requester created by this composition is not attached until the next
// layout pass. Retry across a few frames instead of dropping the first focus.
private suspend fun FocusRequester.requestWhenAttached(): Boolean {
    repeat(3) {
        if (runCatching { requestFocus() }.isSuccess) return true
        withFrameNanos { }
    }
    return false
}

private fun fileName(): String {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
        .format(Instant.now().atZone(ZoneId.systemDefault()))
    return "ovisiontv-backup-$stamp.ovbk"
}
