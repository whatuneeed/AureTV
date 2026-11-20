package app.aure.tv.backup

import android.content.Context
import app.aure.tv.data.writeFileAtomically
import org.json.JSONObject
import java.io.File

internal data class RestoreJournalState(
    val state: String,
    val backupId: String,
    val preRestoreId: String,
    val startedAt: String,
)

/** A single atomic file; it survives process death between two prefs commits. */
internal class RestoreJournal(context: Context) {
    private val file = File(File(context.applicationContext.filesDir, DIR), FILE)

    fun read(): RestoreJournalState? {
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            RestoreJournalState(
                state = json.optString("state"),
                backupId = json.optString("backupId"),
                preRestoreId = json.optString("preRestoreId"),
                startedAt = json.optString("startedAt"),
            )
        }.getOrNull()
    }

    fun write(state: RestoreJournalState) {
        val json = JSONObject()
            .put("state", state.state)
            .put("backupId", state.backupId)
            .put("preRestoreId", state.preRestoreId)
            .put("startedAt", state.startedAt)
        val dir = file.parentFile
        if (dir != null && !dir.isDirectory && !dir.mkdirs()) {
            throw BackupException(BackupStage.IO, "journal directory is not writable")
        }
        if (!writeFileAtomically(file, json.toString().toByteArray(Charsets.UTF_8))) {
            throw BackupException(BackupStage.IO, "restore journal could not be written")
        }
    }

    fun clear() {
        file.delete()
    }

    private companion object {
        const val DIR = "backups"
        const val FILE = "journal.json"
    }
}
