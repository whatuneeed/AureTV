package com.whatuneed.ovisiontv.kotlin.update

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.whatuneed.ovisiontv.kotlin.data.SafePreferences
import java.io.File

/**
 * Small, non-destructive state store. It never stores a password, token or
 * release URL; the latter comes from the build configuration or the cached
 * metadata. Corrupt values fall back to safe defaults via SafePreferences.
 */
@SuppressLint("ApplySharedPref")
internal class UpdateStateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun channel(): UpdateChannel =
        UpdateChannel.fromKey(SafePreferences.string(prefs, KEY_CHANNEL, UpdateChannel.STABLE.key))

    fun setChannel(channel: UpdateChannel) {
        prefs.edit().putString(KEY_CHANNEL, channel.key).commit()
    }

    fun mode(): UpdateCheckMode =
        UpdateCheckMode.fromKey(SafePreferences.string(prefs, KEY_MODE, UpdateCheckMode.DAILY.key))

    fun setMode(mode: UpdateCheckMode) {
        prefs.edit().putString(KEY_MODE, mode.key).commit()
    }

    fun phase(): UpdatePhase =
        runCatching { UpdatePhase.valueOf(SafePreferences.string(prefs, KEY_PHASE, "") ?: "") }
            .getOrDefault(UpdatePhase.IDLE)

    fun setPhase(phase: UpdatePhase) {
        prefs.edit().putString(KEY_PHASE, phase.name).commit()
    }

    fun lastCheckMs(): Long? = positiveLong(KEY_LAST_CHECK)

    fun markAttempt(nowMs: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK, nowMs).commit()
    }

    fun lastSuccessMs(): Long? = positiveLong(KEY_LAST_SUCCESS)

    fun markSuccess(nowMs: Long) {
        prefs.edit().putLong(KEY_LAST_SUCCESS, nowMs).commit()
    }

    fun etag(): String? = SafePreferences.string(prefs, KEY_ETAG, null)?.takeIf { it.isNotBlank() }

    fun setEtag(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_ETAG) else putString(KEY_ETAG, value)
        }.commit()
    }

    fun availableVersionCode(): Int =
        SafePreferences.int(prefs, KEY_AVAILABLE_CODE, 0).coerceAtLeast(0)

    fun availableVersionName(): String =
        SafePreferences.string(prefs, KEY_AVAILABLE_NAME, "")?.take(64).orEmpty()

    fun setAvailable(release: UpdateRelease?) {
        prefs.edit().apply {
            if (release == null) {
                remove(KEY_AVAILABLE_CODE)
                remove(KEY_AVAILABLE_NAME)
            } else {
                putInt(KEY_AVAILABLE_CODE, release.versionCode)
                putString(KEY_AVAILABLE_NAME, release.versionName)
            }
        }.commit()
    }

    fun pendingVersionCode(): Int =
        SafePreferences.int(prefs, KEY_PENDING_CODE, 0).coerceAtLeast(0)

    fun pendingSessionId(): Int =
        SafePreferences.int(prefs, KEY_PENDING_SESSION, 0)

    fun setPending(versionCode: Int, sessionId: Int) {
        prefs.edit()
            .putInt(KEY_PENDING_CODE, versionCode)
            .putInt(KEY_PENDING_SESSION, sessionId)
            .commit()
    }

    fun clearPending() {
        prefs.edit()
            .remove(KEY_PENDING_CODE)
            .remove(KEY_PENDING_SESSION)
            .commit()
    }

    /** True when the configured cadence has elapsed; manual checks bypass this. */
    fun isDue(nowMs: Long): Boolean {
        val interval = mode().intervalMs() ?: return false
        val last = lastCheckMs() ?: return true
        return nowMs - last >= interval
    }

    /**
     * A compact value for Settings → System. It never claims an update when no
     * metadata was successfully read.
     */
    fun summary(): String? {
        val name = availableVersionName()
        if (name.isBlank() || availableVersionCode() <= 0) return null
        return name
    }

    fun clearAvailable() = setAvailable(null)

    fun clearAll() {
        runCatching {
            prefs.edit().clear().commit()
        }.onFailure { Log.w(TAG, "could not clear update state", it) }
    }

    private fun positiveLong(key: String): Long? =
        SafePreferences.long(prefs, key, 0L).takeIf { it > 0L }

    internal companion object {
        private const val TAG = "UpdateStateStore"
        const val PREFS = "update_state"
        const val KEY_CHANNEL = "channel"
        const val KEY_MODE = "mode"
        const val KEY_PHASE = "phase"
        const val KEY_LAST_CHECK = "last_check_ms"
        const val KEY_LAST_SUCCESS = "last_success_ms"
        const val KEY_ETAG = "etag"
        const val KEY_AVAILABLE_CODE = "available_version_code"
        const val KEY_AVAILABLE_NAME = "available_version_name"
        const val KEY_PENDING_CODE = "pending_version_code"
        const val KEY_PENDING_SESSION = "pending_session_id"
    }
}

/**
 * Cached metadata is app-private and bounded. It is a convenience for offline
 * display, not a second trust store: every install still checks the APK's
 * certificate and the system installer verifies it again.
 */
internal class UpdateMetadataCache(context: Context) {
    private val dir = File(context.applicationContext.cacheDir, DIR)

    fun load(channel: UpdateChannel): String? {
        val file = file(channel)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_BYTES) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun save(channel: UpdateChannel, json: String): Boolean {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return false
        dir.mkdirs()
        return com.whatuneed.ovisiontv.kotlin.data.writeFileAtomically(
            file(channel),
            json.toByteArray(Charsets.UTF_8),
        )
    }

    fun clear(channel: UpdateChannel? = null) {
        if (channel == null) {
            dir.listFiles()?.forEach { it.delete() }
        } else {
            file(channel).delete()
        }
    }

    private fun file(channel: UpdateChannel) = File(dir, "${channel.key}.json")

    companion object {
        private const val DIR = "update"
        private const val MAX_BYTES = 256 * 1024
    }
}
