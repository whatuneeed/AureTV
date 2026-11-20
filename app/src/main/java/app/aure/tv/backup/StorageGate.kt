package app.aure.tv.backup

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Coordinates the backup snapshot with store writes. It is intentionally a
 * small process-wide lock: SharedPreferences has no cross-file snapshot API.
 * Stores that do not take this lock are a known limitation, not a claim of
 * ACID restore.
 */
internal object StorageGate {
    private val lock = ReentrantReadWriteLock()

    fun <T> read(block: () -> T): T = lock.read(block)

    fun <T> write(block: () -> T): T = lock.write(block)
}
