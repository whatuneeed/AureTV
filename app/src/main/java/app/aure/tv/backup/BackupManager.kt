package app.aure.tv.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import app.aure.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.time.Instant
import java.util.Locale
import java.util.UUID

internal data class CreatedArchive(
    val bytes: ByteArray,
    val manifest: BackupManifest,
    val info: BackupInfo,
    val warnings: List<BackupWarning>,
)

/**
 * Orchestrates export, import and restore. All storage work is on Dispatchers.IO;
 * a single mutex prevents an automatic copy and a manual restore from racing.
 */
internal class BackupManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val repository = BackupRepository(appContext)
    private val journal = RestoreJournal(appContext)
    private val source = BackupSource(appContext)
    private val engine = BackupRestoreEngine(appContext)

    suspend fun list(): List<BackupIndexEntry> = withContext(Dispatchers.IO) { repository.list() }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        repository.delete(id)
    }

    suspend fun createLocal(
        selection: BackupSelection = BackupSelection(),
        trigger: BackupTrigger = BackupTrigger.MANUAL,
    ): BackupIndexEntry = mutex.withLock {
        createLocalLocked(selection, trigger)
    }

    private suspend fun createLocalLocked(
        selection: BackupSelection,
        trigger: BackupTrigger,
    ): BackupIndexEntry = withContext(Dispatchers.IO) {
        repository.cleanupTemp()
        val created = createArchive(selection, password = null, enforcePlainPolicy = false, trigger)
        val temp = repository.newTempFile()
        try {
            temp.writeBytes(created.bytes)
            val entry = repository.store(
                temp = temp,
                info = created.info,
                sha256 = BackupHashing.sha256(created.bytes),
                formatVersion = created.manifest.formatVersion,
            )
            repository.prune()
            entry
        } finally {
            if (temp.exists()) temp.delete()
            repository.cleanupTemp()
        }
    }

    suspend fun export(
        uri: Uri,
        selection: BackupSelection,
        password: CharArray?,
    ): BackupInfo = mutex.withLock {
        withContext(Dispatchers.IO) {
            val created = createArchive(
                selection = selection,
                password = password,
                // A plain manual export must not carry discovery history or a
                // home location; the UI asks for a password instead.
                enforcePlainPolicy = password == null,
                trigger = BackupTrigger.MANUAL,
            )
            val stream = appContext.contentResolver.openOutputStream(uri, "w")
                ?: throw BackupException(BackupStage.IO, "file is not writable")
            stream.use { it.write(created.bytes) }
            created.info
        }
    }

    suspend fun previewLocal(
        entry: BackupIndexEntry,
        password: CharArray? = null,
    ): BackupPreview = withContext(Dispatchers.IO) {
        val file = repository.fileFor(entry)
        if (!file.isFile) throw BackupException(BackupStage.IO, "backup file is missing")
        FileInputStream(file).use { readPreview(it, password) }
    }

    suspend fun previewUri(
        uri: Uri,
        password: CharArray? = null,
    ): BackupPreview = withContext(Dispatchers.IO) {
        val stream = appContext.contentResolver.openInputStream(uri)
            ?: throw BackupException(BackupStage.IO, "file could not be opened")
        stream.use { readPreview(it, password) }
    }

    suspend fun restore(
        preview: BackupPreview,
        selection: BackupSelection,
    ): RestoreOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (selection.isEmpty()) {
                throw BackupException(BackupStage.RESTORE, "nothing selected for restore")
            }
            // A rollback point is mandatory. If it cannot be written, the
            // restore has not started and live data is untouched.
            val preRestore = createLocalLocked(
                selection = BackupSelection(),
                trigger = BackupTrigger.PRE_RESTORE,
            )
            val startedAt = Instant.now().toString()
            journal.write(
                RestoreJournalState(
                    state = "prepared",
                    backupId = preview.info.backupId,
                    preRestoreId = preRestore.info.backupId,
                    startedAt = startedAt,
                ),
            )
            try {
                journal.write(
                    RestoreJournalState(
                        state = "applying",
                        backupId = preview.info.backupId,
                        preRestoreId = preRestore.info.backupId,
                        startedAt = startedAt,
                    ),
                )
                val outcome = StorageGate.write {
                    engine.apply(preview.validated, selection)
                }
                journal.write(
                    RestoreJournalState(
                        state = "applied",
                        backupId = preview.info.backupId,
                        preRestoreId = preRestore.info.backupId,
                        startedAt = startedAt,
                    ),
                )
                journal.clear()
                outcome
            } catch (error: Throwable) {
                journal.write(
                    RestoreJournalState(
                        state = "failed",
                        backupId = preview.info.backupId,
                        preRestoreId = preRestore.info.backupId,
                        startedAt = startedAt,
                    ),
                )
                val rolledBack = runCatching {
                    rollbackTo(preRestore.info.backupId)
                }
                if (rolledBack.isFailure) {
                    throw BackupException(
                        BackupStage.RESTORE,
                        "restore failed and rollback also failed; the pre-restore copy is kept",
                        error,
                    )
                }
                journal.write(
                    RestoreJournalState(
                        state = "rolled_back",
                        backupId = preview.info.backupId,
                        preRestoreId = preRestore.info.backupId,
                        startedAt = startedAt,
                    ),
                )
                journal.clear()
                throw BackupException(
                    BackupStage.RESTORE,
                    "restore failed; the previous data was restored",
                    error,
                )
            }
        }
    }

    /** Used by pre-restore rollback and by crash recovery. */
    internal suspend fun rollbackTo(backupId: String): RestoreOutcome = withContext(Dispatchers.IO) {
        val entry = repository.find(backupId)
            ?: throw BackupException(BackupStage.RESTORE, "rollback copy is missing")
        val preview = previewLocal(entry, password = null)
        StorageGate.write { engine.apply(preview.validated, BackupSelection()) }
    }

    internal suspend fun recoverIfNeeded(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
        val state = journal.read() ?: return@withContext false
        when (state.state) {
            "applied" -> {
                journal.clear()
                false
            }

            "rolled_back" -> {
                journal.clear()
                false
            }

            "prepared", "applying", "failed" -> {
                if (state.preRestoreId.isNotBlank()) {
                    runCatching { rollbackTo(state.preRestoreId) }
                        .onFailure {
                            throw BackupException(
                                BackupStage.RESTORE,
                                "interrupted restore could not be rolled back",
                                it,
                            )
                        }
                }
                journal.clear()
                true
            }

            else -> {
                journal.clear()
                false
            }
        }
        }
    }

    private fun readPreview(input: InputStream, password: CharArray?): BackupPreview {
        val read = BackupArchive.read(input, password)
        val validated = BackupValidator.validateForRead(read, enforcePlainPolicy = false)
        val info = read.manifest.toInfo(
            trigger = BackupTrigger.MANUAL,
            pinned = false,
            bytes = read.components.values.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() },
        )
        return BackupPreview(
            info = info,
            warnings = validated.warnings,
            categories = read.manifest.selection,
            encrypted = read.encrypted,
            read = read,
            validated = validated,
        )
    }

    private fun createArchive(
        selection: BackupSelection,
        password: CharArray?,
        enforcePlainPolicy: Boolean,
        trigger: BackupTrigger,
    ): CreatedArchive {
        if (selection.isEmpty()) throw BackupException(BackupStage.SCHEMA, "nothing selected")
        val read = StorageGate.read { source.read(selection) }
        val encoded = BackupJson.encode(read.data, selection)
        if (encoded.isEmpty()) {
            throw BackupException(BackupStage.SCHEMA, "no data to back up")
        }
        val components = LinkedHashMap<String, JSONObject>()
        val entries = ArrayList<ComponentEntry>(encoded.size)
        // A corrupt/unreadable store can disappear from the snapshot even when
        // the user selected its category. Record what was actually written so
        // the archive remains self-consistent; the warning still names the loss.
        val actualSelection = encoded.mapTo(LinkedHashSet()) { it.type.category }
        encoded.forEachIndexed { index, component ->
            val path = "${BackupContract.COMPONENT_DIR}%04d.json".format(Locale.ROOT, index)
            val json = component.json
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            components[path] = json
            entries += ComponentEntry(
                type = component.type,
                profileId = component.profileId,
                schema = component.schema,
                path = path,
                bytes = bytes.size.toLong(),
                sha256 = BackupHashing.sha256(bytes),
                count = component.count,
            )
        }
        val manifest = BackupManifest(
            formatVersion = BackupContract.FORMAT_VERSION,
            dataSchema = BackupContract.DATA_SCHEMA,
            backupId = UUID.randomUUID().toString(),
            createdAt = Instant.now().toString(),
            appVersionCode = BuildConfig.VERSION_CODE,
            appVersionName = BuildConfig.VERSION_NAME,
            sourceSdk = Build.VERSION.SDK_INT,
            sourceModel = Build.MODEL.orEmpty(),
            sourceLocale = Locale.getDefault().toLanguageTag(),
            selection = actualSelection,
            components = entries,
            encryption = null,
            payload = null,
        )
        val warnings = LinkedHashSet<BackupWarning>(read.warnings)
        warnings += BackupValidator.validateForWrite(manifest, read.data, enforcePlainPolicy)
        val bytes = BackupArchive.write(manifest, components, password)
        // Self-check the exact bytes that will be given to the user.
        val self = BackupArchive.read(ByteArrayInputStream(bytes), password)
        BackupValidator.validateForRead(self, enforcePlainPolicy = false)
        val info = manifest.toInfo(
            trigger = trigger,
            pinned = trigger == BackupTrigger.PRE_RESTORE ||
                trigger == BackupTrigger.PRE_MIGRATION ||
                trigger == BackupTrigger.PRE_UPDATE,
            bytes = bytes.size.toLong(),
        )
        return CreatedArchive(
            bytes = bytes,
            manifest = manifest,
            info = info,
            warnings = warnings.toList(),
        )
    }

    private companion object {
        val mutex = Mutex()
    }
}
