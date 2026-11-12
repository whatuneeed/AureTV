package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** tvOS Download Frequency, as whole intervals. Never means never. */
internal object SaverRefreshSchedule {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun intervalMs(refresh: SaverRefresh): Long? = when (refresh) {
        SaverRefresh.DAILY -> DAY_MS
        SaverRefresh.WEEKLY -> 7 * DAY_MS
        SaverRefresh.MONTHLY -> 30 * DAY_MS
        SaverRefresh.NEVER -> null
    }

    fun isDue(refresh: SaverRefresh, lastAttemptMs: Long, nowMs: Long): Boolean {
        val interval = intervalMs(refresh) ?: return false
        if (lastAttemptMs <= 0L) return true
        return nowMs - lastAttemptMs >= interval
    }
}

/**
 * A refresh attempt has three honest outcomes: the catalogue changed, Apple's
 * feed is older than the bundled one, or the attempt failed. "Already current"
 * must never stand in for a network or TLS error.
 */
enum class AerialRefreshResult {
    UPDATED,
    UNCHANGED,
    FAILED,
}

/** The refreshed catalogue lives in app storage; the asset stays the fallback. */
internal object AerialStore {
    private const val FILE_NAME = "aerial_catalog.json"

    fun load(context: Context): List<AerialClip> {
        val file = file(context)
        if (!file.isFile) return emptyList()
        return runCatching { AerialCatalog.parse(file.readText()) }.getOrDefault(emptyList())
    }

    fun save(context: Context, json: String) {
        val target = file(context)
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(json)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)
}

/**
 * Keeps the aerial catalogue as fresh as the chosen Download Frequency.
 * Failure is silent and keeps the previous catalogue: a TV without working
 * TLS to Apple must still have a screen saver.
 */
internal object AerialRefresh {
    // TLS keeps catalogue metadata authentic; where 443 to Apple is blocked the
    // refresh fails and the bundled catalogue keeps the saver running. No HTTP fallback.
    private const val SOURCE_URL = "https://sylvan.apple.com/Aerials/resources.tar"
    private const val ENTRIES = "entries.json"
    private const val STRINGS_EN = "TVIdleScreenStrings.bundle/en.lproj/Localizable.nocache.strings"
    private const val STRINGS_RU = "TVIdleScreenStrings.bundle/ru.lproj/Localizable.nocache.strings"
    private const val TIMEOUT_MS = 20_000
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val COPY_CHUNK = 64 * 1024
    private const val TAG = "AerialRefresh"

    fun isDue(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean =
        SaverRefreshSchedule.isDue(
            SaverPreferences.refresh(context),
            SaverPreferences.refreshAttempt(context),
            nowMs,
        )

    suspend fun update(context: Context): AerialRefreshResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        // The attempt is recorded before the network call so an unreachable
        // CDN is retried on the next interval, not on every resume.
        SaverPreferences.setRefreshAttempt(app, System.currentTimeMillis())
        runCatching { updateNow(app) }
            .onFailure { error -> Log.w(TAG, "aerial catalogue refresh failed", error) }
            .getOrDefault(AerialRefreshResult.FAILED)
    }

    private fun updateNow(context: Context): AerialRefreshResult {
        val archive = download() ?: return fail("download", AerialRefreshResult.FAILED)
        val files = AerialTar.extract(archive, setOf(ENTRIES, STRINGS_EN, STRINGS_RU))
        val entries = files[ENTRIES]?.toString(Charsets.UTF_8)
            ?: return fail("entries", AerialRefreshResult.FAILED)
        if (!AerialCatalogBuilder.supports(entries)) {
            // Apple's public tar is still the 2019 flat catalogue. The bundled
            // tvOS 27 catalogue is newer, so a check that finds nothing new
            // must leave it untouched instead of downgrading it.
            return fail("legacy catalogue, bundled one kept", AerialRefreshResult.UNCHANGED)
        }
        val en = files[STRINGS_EN] ?: return fail("en strings", AerialRefreshResult.FAILED)
        val ru = files[STRINGS_RU] ?: return fail("ru strings", AerialRefreshResult.FAILED)
        val json = AerialCatalogBuilder.build(entries, en, ru)
            ?: return fail("build", AerialRefreshResult.FAILED)
        if (AerialCatalog.parse(json).isEmpty()) {
            return fail("empty result", AerialRefreshResult.FAILED)
        }
        AerialStore.save(context, json)
        Log.d(TAG, "aerial catalogue refreshed")
        return AerialRefreshResult.UPDATED
    }

    private fun fail(reason: String, result: AerialRefreshResult): AerialRefreshResult {
        Log.w(TAG, "aerial catalogue refresh skipped: $reason")
        return result
    }

    private fun download(): ByteArray? = runCatching {
        val connection = URL(SOURCE_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/octet-stream")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            val out = ByteArrayOutputStream(COPY_CHUNK)
            connection.inputStream.use { input ->
                val buffer = ByteArray(COPY_CHUNK)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) return null
                    out.write(buffer, 0, read)
                }
            }
            out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }.onFailure { error -> Log.w(TAG, "aerial catalogue download failed", error) }.getOrNull()
}
