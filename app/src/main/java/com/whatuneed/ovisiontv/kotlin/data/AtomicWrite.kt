package com.whatuneed.ovisiontv.kotlin.data

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes [bytes] to [target] through a unique temp file and a checked replace.
 * A failed write leaves the previous file untouched and returns false, so a
 * caller can report FAILED instead of a false UPDATED (AQ-17).
 */
internal fun writeFileAtomically(target: File, bytes: ByteArray): Boolean {
    val dir = target.parentFile ?: return false
    if (!dir.isDirectory && !dir.mkdirs()) return false
    val prefix = target.name.padEnd(3, '_') + "."
    val temp = runCatching { File.createTempFile(prefix, ".tmp", dir) }.getOrNull() ?: return false
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
    } catch (_: Exception) {
        false
    } finally {
        temp.delete()
    }
}
