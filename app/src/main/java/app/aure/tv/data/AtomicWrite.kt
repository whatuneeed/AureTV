package app.aure.tv.data

import android.util.Log
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes [bytes] to [target] through a unique temp file and a checked replace.
 * A failed write leaves the previous file untouched and returns false, so a
 * caller can report FAILED instead of a false UPDATED (AQ-17).
 */
private const val TAG = "AtomicWrite"

// Android's Log is a stub in local JVM tests. Logging is diagnostic only, so a
// missing/stubbed logger must never change the Boolean result.
private fun warn(message: String, error: Throwable? = null) {
    runCatching { Log.w(TAG, message, error) }
}

internal fun writeFileAtomically(target: File, bytes: ByteArray): Boolean {
    val dir = target.parentFile ?: return false
    if (!dir.isDirectory && !dir.mkdirs()) {
        warn("cache directory is not writable: ${dir.name}")
        return false
    }
    val prefix = target.name.padEnd(3, '_') + "."
    val temp = runCatching { File.createTempFile(prefix, ".tmp", dir) }.getOrNull()
        ?: run {
            warn("temporary file could not be created in ${dir.name}")
            return false
        }
    return try {
        temp.writeBytes(bytes)
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (error: Exception) {
        // The previous file stays intact; report the failure instead of
        // pretending the write succeeded (AQ-17).
        warn("atomic write failed for ${target.name}", error)
        false
    } finally {
        temp.delete()
    }
}
