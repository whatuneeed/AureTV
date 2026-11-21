package app.aure.tv.backup

import kotlinx.coroutines.CancellationException

/**
 * Backup work runs on a screen-scoped coroutine. A cancelled screen must end
 * the operation instead of being reported as a failed restore: callers rethrow
 * this before mapping an error to user-facing backup text.
 */
internal fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
