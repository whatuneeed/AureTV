package com.whatuneed.ovisiontv.kotlin.update

/** Distribution channels. A beta build must keep the same applicationId and signer. */
enum class UpdateChannel(val key: String) {
    STABLE("stable"),
    BETA("beta"),
    ;

    companion object {
        fun fromKey(value: String?): UpdateChannel =
            entries.firstOrNull { it.key == value } ?: STABLE
    }
}

/** Automatic check cadence. There is no exact-time background promise. */
enum class UpdateCheckMode(val key: String) {
    MANUAL("manual"),
    DAILY("daily"),
    WEEKLY("weekly"),
    ;

    fun intervalMs(): Long? = when (this) {
        MANUAL -> null
        DAILY -> 24L * 60L * 60L * 1000L
        WEEKLY -> 7L * 24L * 60L * 60L * 1000L
    }

    companion object {
        fun fromKey(value: String?): UpdateCheckMode =
            entries.firstOrNull { it.key == value } ?: DAILY
    }
}

/**
 * The update lifecycle. DOWNLOADING/VERIFYING/INSTALL_PENDING are persisted so
 * a process death cannot turn an unfinished install into a fake success.
 */
enum class UpdatePhase {
    NOT_CONFIGURED,
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    INCOMPATIBLE,
    DOWNLOADING,
    VERIFYING,
    READY,
    INSTALL_PENDING,
    INSTALLED,
    FAILED,
}

enum class UpdateError {
    NETWORK,
    SERVER,
    RATE_LIMITED,
    METADATA,
    STORAGE,
    INTEGRITY,
    SIGNATURE,
    INSTALL,
    PERMISSION,
    CANCELLED,
    UNKNOWN,
}

data class UpdateAsset(
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val abi: String = "universal",
)

data class UpdateRelease(
    val versionCode: Int,
    val versionName: String,
    val channel: UpdateChannel,
    val generatedAt: String,
    val expiresAt: String,
    val releaseDate: String,
    val minAndroidApi: Int,
    val minSupportedVersionCode: Int,
    val mandatory: Boolean,
    val notes: Map<String, List<String>>,
    val asset: UpdateAsset,
) {
    /**
     * Locale fallback is deliberate: an absent translation must not become an
     * empty "what's new" page or an invented English sentence.
     */
    fun notesFor(language: String?): List<String> {
        val key = language?.lowercase()?.take(2)
        return notes[key]
            ?: notes["en"]
            ?: notes["ru"]
            ?: notes.values.firstOrNull()
            ?: emptyList()
    }
}

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val mode: UpdateCheckMode = UpdateCheckMode.DAILY,
    val installedVersionCode: Int = 0,
    val installedVersionName: String = "",
    val available: UpdateRelease? = null,
    val progressBytes: Long = 0L,
    val totalBytes: Long? = null,
    val error: UpdateError? = null,
    val errorDetail: String? = null,
    val lastCheckMs: Long? = null,
    val lastSuccessMs: Long? = null,
    val pendingVersionCode: Int? = null,
    val sourceConfigured: Boolean = false,
) {
    val hasUpdate: Boolean
        get() = available != null &&
            available.versionCode > installedVersionCode &&
            installedVersionCode > 0

    val busy: Boolean
        get() = phase == UpdatePhase.CHECKING ||
            phase == UpdatePhase.DOWNLOADING ||
            phase == UpdatePhase.VERIFYING ||
            phase == UpdatePhase.INSTALL_PENDING
}
