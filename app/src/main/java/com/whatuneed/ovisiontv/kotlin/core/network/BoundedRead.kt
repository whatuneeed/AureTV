package com.whatuneed.ovisiontv.kotlin.core.network

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val READ_CHUNK = 64 * 1024

/**
 * Reads a body into one buffer, refusing anything over [limit] bytes.
 * Cancellation is checked before every chunk, so leaving a screen stops the
 * copy instead of waiting for the socket read timeout.
 */
internal suspend fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream(READ_CHUNK)
    val buffer = ByteArray(READ_CHUNK)
    var total = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
