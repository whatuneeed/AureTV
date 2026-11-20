package app.aure.tv.backup

import android.content.Context
import app.aure.tv.data.writeFileAtomically
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Local copies live in filesDir, not cacheDir: "Clear cache" must not delete
 * the last rollback point. They still disappear on uninstall, so a portable
 * copy is the SAF export, not this directory.
 */
class BackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, DIR)
    private val tempDir = File(dir, TEMP_DIR)
    private val indexFile = File(dir, INDEX_FILE)

    fun list(): List<BackupIndexEntry> = readIndex().sortedByDescending { it.info.createdAt }

    fun find(id: String): BackupIndexEntry? = list().firstOrNull { it.info.backupId == id }

    fun fileFor(entry: BackupIndexEntry): File = File(dir, safeFileName(entry.fileName))

    fun newTempFile(): File {
        if (!tempDir.isDirectory && !tempDir.mkdirs()) {
            throw BackupException(BackupStage.IO, "backup directory is not writable")
        }
        return File(tempDir, "${UUID.randomUUID()}.part")
    }

    fun store(temp: File, info: BackupInfo, sha256: String, formatVersion: Int): BackupIndexEntry {
        if (!temp.isFile) throw BackupException(BackupStage.IO, "temporary backup is missing")
        val fileName = "${info.backupId}.${BackupContract.FILE_EXTENSION}"
        val target = File(dir, safeFileName(fileName))
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw BackupException(BackupStage.IO, "backup directory is not writable")
        }
        if (!temp.renameTo(target)) {
            if (!temp.copyTo(target, overwrite = true).isFile) {
                throw BackupException(BackupStage.IO, "backup could not be moved into place")
            }
            temp.delete()
        }
        val entry = BackupIndexEntry(
            info = info,
            fileName = fileName,
            sha256 = sha256,
            formatVersion = formatVersion,
        )
        val current = readIndex().filterNot { it.info.backupId == info.backupId } + entry
        if (!writeIndex(current)) {
            target.delete()
            throw BackupException(BackupStage.IO, "backup index could not be written")
        }
        return entry
    }

    fun delete(id: String): Boolean {
        val entries = readIndex()
        val entry = entries.firstOrNull { it.info.backupId == id } ?: return false
        if (entry.info.pinned) return false
        val file = fileFor(entry)
        val next = entries.filterNot { it.info.backupId == id }
        if (!writeIndex(next)) return false
        file.delete()
        return true
    }

    /** Delete only unpinned copies beyond [maxCount]; newest are kept. */
    fun prune(maxCount: Int = BackupContract.LOCAL_RETENTION): List<BackupIndexEntry> {
        val entries = readIndex().sortedByDescending { it.info.createdAt }
        val pinned = entries.filter { it.info.pinned }
        val unpinned = entries.filterNot { it.info.pinned }
        val keepUnpinned = (maxCount - pinned.size).coerceAtLeast(0)
        val keep = pinned + unpinned.take(keepUnpinned)
        val removed = entries.filterNot { candidate ->
            keep.any { it.info.backupId == candidate.info.backupId }
        }
        if (removed.isEmpty()) return emptyList()
        if (!writeIndex(keep)) return emptyList()
        removed.forEach { fileFor(it).delete() }
        return removed
    }

    fun totalBytes(): Long = list().sumOf { it.info.bytes }

    fun cleanupTemp() {
        if (tempDir.isDirectory) {
            tempDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun readIndex(): List<BackupIndexEntry> {
        if (!indexFile.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(indexFile.readText(Charsets.UTF_8))
            val array = root.optJSONArray("backups") ?: JSONArray()
            val result = ArrayList<BackupIndexEntry>(array.length())
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val info = JSONObject(entry.optString("info")).let { infoJson ->
                    BackupInfo(
                        backupId = infoJson.optString("backupId"),
                        createdAt = infoJson.optString("createdAt"),
                        appVersionName = infoJson.optString("appVersionName"),
                        appVersionCode = infoJson.optInt("appVersionCode", 0),
                        dataSchema = infoJson.optInt("dataSchema", 0),
                        encrypted = infoJson.optBoolean("encrypted", false),
                        componentCount = infoJson.optInt("componentCount", 0),
                        bytes = infoJson.optLong("bytes", 0L),
                        trigger = runCatching {
                            BackupTrigger.valueOf(infoJson.optString("trigger"))
                        }.getOrDefault(BackupTrigger.MANUAL),
                        pinned = infoJson.optBoolean("pinned", false),
                    )
                }
                val fileName = entry.optString("fileName")
                if (info.backupId.isBlank() || fileName.isBlank()) continue
                result += BackupIndexEntry(
                    info = info,
                    fileName = fileName,
                    sha256 = entry.optString("sha256"),
                    formatVersion = entry.optInt("formatVersion", 0),
                )
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(entries: List<BackupIndexEntry>): Boolean {
        if (!dir.isDirectory && !dir.mkdirs()) return false
        val root = JSONObject().put(
            "backups",
            JSONArray().apply {
                entries.forEach { entry ->
                    put(
                        JSONObject()
                            .put(
                                "info",
                                JSONObject()
                                    .put("backupId", entry.info.backupId)
                                    .put("createdAt", entry.info.createdAt)
                                    .put("appVersionName", entry.info.appVersionName)
                                    .put("appVersionCode", entry.info.appVersionCode)
                                    .put("dataSchema", entry.info.dataSchema)
                                    .put("encrypted", entry.info.encrypted)
                                    .put("componentCount", entry.info.componentCount)
                                    .put("bytes", entry.info.bytes)
                                    .put("trigger", entry.info.trigger.name)
                                    .put("pinned", entry.info.pinned),
                            )
                            .put("fileName", entry.fileName)
                            .put("sha256", entry.sha256)
                            .put("formatVersion", entry.formatVersion),
                    )
                }
            },
        )
        return writeFileAtomically(indexFile, root.toString().toByteArray(Charsets.UTF_8))
    }

    private fun safeFileName(value: String): String {
        if (value.isBlank() || value.contains('/') || value.contains('\\') ||
            value.contains("..") || value.contains(':')
        ) {
            throw BackupException(BackupStage.IO, "unsafe backup file name")
        }
        return value
    }

    private companion object {
        const val DIR = "backups"
        const val TEMP_DIR = "tmp"
        const val INDEX_FILE = "index.json"
    }
}
