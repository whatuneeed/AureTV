package com.whatuneed.ovisiontv.kotlin.backup

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.Usage

/** Structural limits for one archive; validation never trusts an archive's own numbers. */
internal object BackupContract {
    const val KIND = "ovisiontv-backup"
    const val FORMAT_VERSION = 1
    const val DATA_SCHEMA = 1

    const val FILE_EXTENSION = "ovbk"
    const val MANIFEST_ENTRY = "manifest.json"
    const val PAYLOAD_ENTRY = "payload.enc"
    const val COMPONENT_DIR = "components/"

    const val MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L
    const val MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L
    const val MAX_ENTRY_BYTES = 8L * 1024L * 1024L
    const val MAX_ENTRIES = 256
    const val MAX_JSON_CHARS = 8 * 1024 * 1024
    const val MAX_COMPRESSION_RATIO = 100L

    const val MAX_PROFILES = 3
    const val MAX_PROFILE_ID_CHARS = 32
    const val MAX_PROFILE_NAME_CHARS = 256
    const val MAX_EMOJI_CHARS = 32
    const val MAX_PACKAGE_CHARS = 255
    const val MAX_LABEL_CHARS = 256
    const val MAX_ORDER_ENTRIES = 1_000
    const val MAX_FOLDER_APPS = 1_000
    const val MAX_DISCOVERY_ITEMS = 100
    const val MAX_USAGE_KEYS = 512
    const val MAX_SHOWN_KEYS = 1_000
    const val MAX_ALLOWLIST = 1_000
    const val MAX_SAVER_IDS = 2_000
    const val MAX_STARTUP_CHARS = 512

    const val LOCAL_RETENTION = 5
    const val MIN_RETENTION = 3
    const val MAX_RETENTION = 10
    const val PASSWORD_MIN_CHARS = 8
    const val KDF_ITERATIONS = 210_000
}

/** User-selectable groups. Kids credentials are deliberately not a group. */
enum class BackupCategory(val id: String) {
    PROFILES("profiles"),
    HOME("home"),
    DISCOVERY("discovery"),
    KIDS("kids"),
    WEATHER("weather"),
    SAVER("saver"),
    LANGUAGE("language"),
    CONTENT("content"),
    STARTUP("startup"),
    ;

    companion object {
        fun fromId(value: String): BackupCategory? = entries.firstOrNull { it.id == value }
    }
}

internal enum class BackupComponentType(val id: String) {
    PROFILES("profiles"),
    HOME("home"),
    DISCOVERY("discovery"),
    KIDS("kids"),
    WEATHER("weather"),
    SAVER("saver"),
    LANGUAGE("language"),
    CONTENT("content"),
    STARTUP("startup"),
    ;

    val category: BackupCategory
        get() = when (this) {
            PROFILES -> BackupCategory.PROFILES
            HOME -> BackupCategory.HOME
            DISCOVERY -> BackupCategory.DISCOVERY
            KIDS -> BackupCategory.KIDS
            WEATHER -> BackupCategory.WEATHER
            SAVER -> BackupCategory.SAVER
            LANGUAGE -> BackupCategory.LANGUAGE
            CONTENT -> BackupCategory.CONTENT
            STARTUP -> BackupCategory.STARTUP
        }

    companion object {
        fun fromId(value: String): BackupComponentType? = entries.firstOrNull { it.id == value }
    }
}

data class BackupSelection(
    val profiles: Boolean = true,
    val home: Boolean = true,
    val discovery: Boolean = true,
    val kids: Boolean = true,
    val weather: Boolean = true,
    val saver: Boolean = true,
    val language: Boolean = true,
    val content: Boolean = true,
    val startup: Boolean = true,
) {
    fun includes(category: BackupCategory): Boolean = when (category) {
        BackupCategory.PROFILES -> profiles
        BackupCategory.HOME -> home
        BackupCategory.DISCOVERY -> discovery
        BackupCategory.KIDS -> kids
        BackupCategory.WEATHER -> weather
        BackupCategory.SAVER -> saver
        BackupCategory.LANGUAGE -> language
        BackupCategory.CONTENT -> content
        BackupCategory.STARTUP -> startup
    }

    fun categories(): Set<BackupCategory> = BackupCategory.entries.filter(::includes).toSet()

    fun without(category: BackupCategory): BackupSelection = when (category) {
        BackupCategory.PROFILES -> copy(profiles = false)
        BackupCategory.HOME -> copy(home = false)
        BackupCategory.DISCOVERY -> copy(discovery = false)
        BackupCategory.KIDS -> copy(kids = false)
        BackupCategory.WEATHER -> copy(weather = false)
        BackupCategory.SAVER -> copy(saver = false)
        BackupCategory.LANGUAGE -> copy(language = false)
        BackupCategory.CONTENT -> copy(content = false)
        BackupCategory.STARTUP -> copy(startup = false)
    }

    fun with(category: BackupCategory, enabled: Boolean): BackupSelection = when (category) {
        BackupCategory.PROFILES -> copy(profiles = enabled)
        BackupCategory.HOME -> copy(home = enabled)
        BackupCategory.DISCOVERY -> copy(discovery = enabled)
        BackupCategory.KIDS -> copy(kids = enabled)
        BackupCategory.WEATHER -> copy(weather = enabled)
        BackupCategory.SAVER -> copy(saver = enabled)
        BackupCategory.LANGUAGE -> copy(language = enabled)
        BackupCategory.CONTENT -> copy(content = enabled)
        BackupCategory.STARTUP -> copy(startup = enabled)
    }

    fun isEmpty(): Boolean = categories().isEmpty()
}

enum class BackupTrigger { MANUAL, AUTO, PRE_UPDATE, PRE_MIGRATION, PRE_RESTORE }

enum class BackupWarning {
    MISSING_APPS,
    KIDS_PIN_RESET,
    PERMISSIONS_NOT_RESTORED,
    DISCOVERY_REQUIRES_ENCRYPTION,
    WEATHER_REQUIRES_ENCRYPTION,
    UNKNOWN_COMPONENT,
    REPAIRED,
    EMPTY,
    PROFILE_LIMIT,
    SOURCE_UNREADABLE,
}

/** Public summary used by the TV UI; contains no titles, links or credentials. */
data class BackupInfo(
    val backupId: String,
    val createdAt: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val dataSchema: Int,
    val encrypted: Boolean,
    val componentCount: Int,
    val bytes: Long,
    val trigger: BackupTrigger,
    val pinned: Boolean,
)

data class BackupIndexEntry(
    val info: BackupInfo,
    val fileName: String,
    val sha256: String,
    val formatVersion: Int,
)

/** Validated archive plus the warnings the confirmation page must show. */
internal data class BackupPreview(
    val info: BackupInfo,
    val warnings: List<BackupWarning>,
    val categories: Set<BackupCategory>,
    val encrypted: Boolean,
    internal val read: ReadBackup,
    internal val validated: ValidatedBackup,
)

internal data class BackupProfile(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Int,
    val kids: Boolean,
)

internal data class ProfilesData(
    val profiles: List<BackupProfile>,
    val currentId: String,
    val nextId: Int,
    val askOnBoot: Boolean,
    val askOnWake: Boolean,
)

internal sealed interface HomeOrderEntry {
    data class App(val packageName: String) : HomeOrderEntry
    data class Folder(
        val id: String,
        val name: String,
        val apps: List<String>,
    ) : HomeOrderEntry
}

internal data class HomeData(
    val order: List<HomeOrderEntry>,
)

internal data class DiscoveryData(
    val recommendations: Boolean,
    val continueWatching: Boolean,
    val personalization: Boolean,
    val favorites: List<DiscoveryItem>,
    val hidden: List<DiscoveryItem>,
    val usage: Map<String, Usage>,
    val shown: Map<String, Long>,
)

internal data class KidsData(val allowlist: Set<String>)

internal data class WeatherData(
    val enabled: Boolean,
    val manual: Boolean,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val units: String,
    val source: String,
    val refreshMinutes: Int,
)

internal data class SaverData(
    val delayMinutes: Int,
    val music: Boolean,
    val refresh: String,
    val themes: Set<String>,
    val hidden: Set<String>,
    /** Added after schema 1; absent in older archives means Aerial. */
    val mode: String = "aerial",
    /** Added after schema 1; absent in older archives means system clock. */
    val clockFormat: String = "system",
    val pixelShift: Boolean = true,
)

internal data class LanguageData(
    val language: String,
    val showPhoneApps: Boolean,
)

internal data class ContentData(
    val recommendations: Boolean,
    val continueWatching: Boolean,
)

internal data class StartupData(
    val target: String,
    val label: String,
    val onWake: Boolean,
)

internal data class BackupData(
    val profiles: ProfilesData? = null,
    val homes: Map<String, HomeData> = emptyMap(),
    val discovery: Map<String, DiscoveryData> = emptyMap(),
    val kids: Map<String, KidsData> = emptyMap(),
    val weather: WeatherData? = null,
    val saver: SaverData? = null,
    val language: LanguageData? = null,
    val content: ContentData? = null,
    val startup: StartupData? = null,
)

internal data class EncryptionParams(
    val cipher: String,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val nonce: String,
    val tagBits: Int,
) {
    fun isValid(): Boolean =
        cipher == "AES-256-GCM" &&
            kdf == "PBKDF2-HMAC-SHA256" &&
            iterations in 100_000..1_000_000 &&
            salt.isNotBlank() &&
            nonce.isNotBlank() &&
            tagBits == 128
}

internal data class PayloadRef(
    val path: String,
    val bytes: Long,
    val sha256: String,
)

internal data class ComponentEntry(
    val type: BackupComponentType,
    val profileId: String?,
    val schema: Int,
    val path: String,
    val bytes: Long,
    val sha256: String,
    val count: Int,
) {
    val key: String get() = type.id + (profileId?.let { ":$it" } ?: "")
}

internal data class BackupManifest(
    val formatVersion: Int,
    val dataSchema: Int,
    val backupId: String,
    val createdAt: String,
    val appVersionCode: Int,
    val appVersionName: String,
    val sourceSdk: Int,
    val sourceModel: String,
    val sourceLocale: String,
    val selection: Set<BackupCategory>,
    val components: List<ComponentEntry>,
    val encryption: EncryptionParams? = null,
    val payload: PayloadRef? = null,
) {
    fun toInfo(trigger: BackupTrigger, pinned: Boolean, bytes: Long): BackupInfo = BackupInfo(
        backupId = backupId,
        createdAt = createdAt,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        dataSchema = dataSchema,
        encrypted = encryption != null,
        componentCount = components.size,
        bytes = bytes,
        trigger = trigger,
        pinned = pinned,
    )
}

internal data class ReadBackup(
    val manifest: BackupManifest,
    val components: Map<String, String>,
    val encrypted: Boolean,
)

enum class BackupStage {
    FORMAT,
    MANIFEST,
    INTEGRITY,
    SCHEMA,
    CRYPTO,
    IO,
    RESTORE,
    MIGRATION,
}

internal class BackupException(
    val stage: BackupStage,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** UI state; no storage work is done while this object is being rendered. */
sealed interface BackupUiState {
    data object Idle : BackupUiState

    data class Working(val stage: BackupStage, val label: String) : BackupUiState

    data class Done(val info: BackupInfo, val message: String? = null) : BackupUiState

    data class Failed(val stage: BackupStage, val message: String, val retry: Boolean) : BackupUiState
}
