package com.whatuneed.ovisiontv.kotlin.backup

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem

internal data class ValidatedBackup(
    val manifest: BackupManifest,
    val data: BackupData,
    val warnings: List<BackupWarning>,
)

/**
 * Schema and policy validation. It runs before a byte is written to the live
 * preferences, and again right after an archive is produced (self-check).
 */
internal object BackupValidator {
    private val packageName = Regex("[A-Za-z0-9_][A-Za-z0-9_.]*")
    private val profileId = Regex("[A-Za-z0-9_-]{1,${BackupContract.MAX_PROFILE_ID_CHARS}}")
    private val hexSha = Regex("[0-9a-f]{64}")
    private val weatherSources = setOf("openmeteo", "met", "wttr")
    private val weatherRefresh = setOf(10, 15, 30, 60)
    private val saverDelays = setOf(-1, 2, 5, 10, 20)
    private val saverRefresh = setOf("DAILY", "WEEKLY", "MONTHLY", "NEVER")
    private val saverThemes = setOf("landscapes", "cities", "underwater", "earth")
    private val languages = setOf("system", "ru", "en")

    fun validateForRead(
        read: ReadBackup,
        currentFormat: Int = BackupContract.FORMAT_VERSION,
        currentSchema: Int = BackupContract.DATA_SCHEMA,
        enforcePlainPolicy: Boolean = false,
    ): ValidatedBackup {
        validateManifest(read.manifest, currentFormat, currentSchema)
        val entries = read.manifest.components
        val decoded = BackupJson.decode(
            entries.map { component ->
                val raw = read.components[component.path]
                    ?: throw BackupException(
                        BackupStage.FORMAT,
                        "component is missing: ${component.path}",
                    )
                component to raw
            },
        )
        validateSelection(read.manifest.selection, decoded)
        val warnings = validateData(decoded, enforcePlainPolicy = enforcePlainPolicy)
        return ValidatedBackup(read.manifest, decoded, warnings)
    }

    fun validateForWrite(
        manifest: BackupManifest,
        data: BackupData,
        enforcePlainPolicy: Boolean,
    ): List<BackupWarning> {
        validateManifest(manifest, BackupContract.FORMAT_VERSION, BackupContract.DATA_SCHEMA)
        validateSelection(manifest.selection, data)
        return validateData(data, enforcePlainPolicy = enforcePlainPolicy)
    }

    fun validateManifest(manifest: BackupManifest, currentFormat: Int, currentSchema: Int) {
        if (manifest.formatVersion != currentFormat) {
            throw BackupException(
                BackupStage.MANIFEST,
                "unsupported format ${manifest.formatVersion}",
            )
        }
        if (manifest.dataSchema !in 1..currentSchema) {
            throw BackupException(
                BackupStage.MANIFEST,
                "backup schema ${manifest.dataSchema} is newer than this app",
            )
        }
        if (manifest.backupId.isBlank() || manifest.backupId.length > 128) {
            throw BackupException(BackupStage.MANIFEST, "invalid backup id")
        }
        if (manifest.createdAt.isBlank() || manifest.createdAt.length > 64) {
            throw BackupException(BackupStage.MANIFEST, "invalid creation time")
        }
        if (manifest.selection.isEmpty()) {
            throw BackupException(BackupStage.MANIFEST, "backup has no selected categories")
        }
        val paths = HashSet<String>()
        val keys = HashSet<String>()
        manifest.components.forEach { component ->
            if (!keys.add(component.key)) {
                throw BackupException(BackupStage.MANIFEST, "duplicate component ${component.key}")
            }
            if (component.schema !in 1..manifest.dataSchema) {
                throw BackupException(BackupStage.MANIFEST, "invalid component schema")
            }
            if (component.path != component.path.trim() ||
                !component.path.startsWith(BackupContract.COMPONENT_DIR) ||
                component.path.contains("..") ||
                component.path.contains('\\') ||
                component.path.contains(':')
            ) {
                throw BackupException(BackupStage.MANIFEST, "unsafe component path")
            }
            if (!paths.add(component.path)) {
                throw BackupException(BackupStage.MANIFEST, "duplicate component path")
            }
            if (component.bytes < 0 || component.sha256.length != 64 ||
                !hexSha.matches(component.sha256)
            ) {
                throw BackupException(BackupStage.MANIFEST, "invalid component metadata")
            }
            component.profileId?.let {
                if (!profileId.matches(it)) {
                    throw BackupException(BackupStage.MANIFEST, "invalid profile id")
                }
            }
        }
        if (manifest.encryption != null) {
            if (!manifest.encryption.isValid()) {
                throw BackupException(BackupStage.CRYPTO, "unsupported encryption parameters")
            }
            if (manifest.payload == null) {
                throw BackupException(BackupStage.MANIFEST, "encrypted backup has no payload")
            }
            if (manifest.components.isNotEmpty()) {
                throw BackupException(BackupStage.MANIFEST, "encrypted outer manifest has components")
            }
        } else if (manifest.payload != null) {
            throw BackupException(BackupStage.MANIFEST, "payload without encryption")
        }
    }

    /**
     * A selected category must carry its component. Without this check a
     * hand-edited archive can claim "profiles + home" while containing only
     * home, and restore would silently leave the profile catalogue untouched.
     */
    private fun validateSelection(selection: Set<BackupCategory>, data: BackupData) {
        if (BackupCategory.PROFILES in selection && data.profiles == null) {
            throw BackupException(BackupStage.SCHEMA, "profiles were selected but are missing")
        }
        if (BackupCategory.HOME in selection && data.homes.isEmpty()) {
            throw BackupException(BackupStage.SCHEMA, "home layout was selected but is missing")
        }
        if (BackupCategory.DISCOVERY in selection && data.discovery.isEmpty()) {
            throw BackupException(BackupStage.SCHEMA, "discovery was selected but is missing")
        }
        if (BackupCategory.KIDS in selection && data.kids.isEmpty()) {
            throw BackupException(BackupStage.SCHEMA, "kids allowlist was selected but is missing")
        }
        if (BackupCategory.WEATHER in selection && data.weather == null) {
            throw BackupException(BackupStage.SCHEMA, "weather was selected but is missing")
        }
        if (BackupCategory.SAVER in selection && data.saver == null) {
            throw BackupException(BackupStage.SCHEMA, "screen saver was selected but is missing")
        }
        if (BackupCategory.LANGUAGE in selection && data.language == null) {
            throw BackupException(BackupStage.SCHEMA, "language was selected but is missing")
        }
        if (BackupCategory.CONTENT in selection && data.content == null) {
            throw BackupException(BackupStage.SCHEMA, "content was selected but is missing")
        }
        if (BackupCategory.STARTUP in selection && data.startup == null) {
            throw BackupException(BackupStage.SCHEMA, "startup was selected but is missing")
        }
    }

    fun validateData(data: BackupData, enforcePlainPolicy: Boolean): List<BackupWarning> {
        val warnings = LinkedHashSet<BackupWarning>()

        data.profiles?.let { profiles ->
            if (profiles.profiles.isEmpty()) {
                throw BackupException(BackupStage.SCHEMA, "backup has no profiles")
            }
            if (profiles.profiles.size > BackupContract.MAX_PROFILES) {
                throw BackupException(BackupStage.SCHEMA, "too many profiles")
            }
            val ids = HashSet<String>()
            profiles.profiles.forEach { profile ->
                if (!profileId.matches(profile.id) || !ids.add(profile.id)) {
                    throw BackupException(BackupStage.SCHEMA, "invalid or duplicate profile id")
                }
                if (profile.name.isBlank() || profile.name.length > BackupContract.MAX_PROFILE_NAME_CHARS) {
                    throw BackupException(BackupStage.SCHEMA, "invalid profile name")
                }
                if (profile.emoji.length > BackupContract.MAX_EMOJI_CHARS ||
                    profile.color !in 0..6
                ) {
                    throw BackupException(BackupStage.SCHEMA, "invalid profile avatar")
                }
            }
            if (profiles.currentId !in ids) {
                throw BackupException(BackupStage.SCHEMA, "current profile is missing")
            }
            if (profiles.nextId < 0 || profiles.nextId > 1_000_000) {
                throw BackupException(BackupStage.SCHEMA, "invalid profile id floor")
            }
        }

        data.homes.forEach { (profileIdValue, home) ->
            if (!profileId.matches(profileIdValue)) {
                throw BackupException(BackupStage.SCHEMA, "invalid home profile id")
            }
            if (home.order.size > BackupContract.MAX_ORDER_ENTRIES) {
                throw BackupException(BackupStage.SCHEMA, "home order is too long")
            }
            val folderIds = HashSet<String>()
            home.order.forEach { entry ->
                when (entry) {
                    is HomeOrderEntry.App -> requirePackage(entry.packageName)
                    is HomeOrderEntry.Folder -> {
                        if (entry.id.isBlank() || entry.id.length > BackupContract.MAX_PROFILE_NAME_CHARS ||
                            !folderIds.add(entry.id)
                        ) {
                            throw BackupException(BackupStage.SCHEMA, "invalid folder id")
                        }
                        if (entry.name.isBlank() || entry.name.length > BackupContract.MAX_PROFILE_NAME_CHARS) {
                            throw BackupException(BackupStage.SCHEMA, "invalid folder name")
                        }
                        if (entry.apps.size > BackupContract.MAX_FOLDER_APPS) {
                            throw BackupException(BackupStage.SCHEMA, "folder is too large")
                        }
                        entry.apps.forEach(::requirePackage)
                    }
                }
            }
        }

        data.discovery.forEach { (profileIdValue, discovery) ->
            if (!profileId.matches(profileIdValue)) {
                throw BackupException(BackupStage.SCHEMA, "invalid discovery profile id")
            }
            if (discovery.favorites.size > BackupContract.MAX_DISCOVERY_ITEMS ||
                discovery.hidden.size > BackupContract.MAX_DISCOVERY_ITEMS
            ) {
                throw BackupException(BackupStage.SCHEMA, "too many discovery items")
            }
            (discovery.favorites + discovery.hidden).forEach(::requireItem)
            if (discovery.usage.size > BackupContract.MAX_USAGE_KEYS ||
                discovery.shown.size > BackupContract.MAX_SHOWN_KEYS
            ) {
                throw BackupException(BackupStage.SCHEMA, "discovery history is too large")
            }
            discovery.usage.forEach { (packageNameValue, usage) ->
                requirePackage(packageNameValue)
                if (usage.count !in 0..1_000_000 || usage.lastUtc < 0) {
                    throw BackupException(BackupStage.SCHEMA, "invalid discovery usage")
                }
            }
        }

        data.kids.forEach { (profileIdValue, kids) ->
            if (!profileId.matches(profileIdValue)) {
                throw BackupException(BackupStage.SCHEMA, "invalid kids profile id")
            }
            if (kids.allowlist.size > BackupContract.MAX_ALLOWLIST) {
                throw BackupException(BackupStage.SCHEMA, "kids allowlist is too large")
            }
            kids.allowlist.forEach(::requirePackage)
        }

        data.weather?.let { weather ->
            if (weather.units !in setOf("auto", "c", "f")) {
                throw BackupException(BackupStage.SCHEMA, "invalid weather units")
            }
            if (weather.source !in weatherSources || weather.refreshMinutes !in weatherRefresh) {
                throw BackupException(BackupStage.SCHEMA, "invalid weather source")
            }
            if (!weather.latitude.isFinite() || weather.latitude !in -90.0..90.0 ||
                !weather.longitude.isFinite() || weather.longitude !in -180.0..180.0
            ) {
                throw BackupException(BackupStage.SCHEMA, "invalid weather coordinates")
            }
            if (weather.place.length > 256) {
                throw BackupException(BackupStage.SCHEMA, "weather place is too long")
            }
            if (enforcePlainPolicy && weather.manual) {
                throw BackupException(
                    BackupStage.SCHEMA,
                    "weather location requires an encrypted export",
                )
            }
        }

        data.saver?.let { saver ->
            if (saver.delayMinutes !in saverDelays || saver.refresh !in saverRefresh) {
                throw BackupException(BackupStage.SCHEMA, "invalid saver settings")
            }
            if (saver.themes.any { it !in saverThemes }) {
                throw BackupException(BackupStage.SCHEMA, "invalid saver theme")
            }
            if (saver.themes.isEmpty() || saver.hidden.size > BackupContract.MAX_SAVER_IDS) {
                throw BackupException(BackupStage.SCHEMA, "invalid saver selection")
            }
        }

        data.language?.let {
            if (it.language !in languages) {
                throw BackupException(BackupStage.SCHEMA, "invalid language")
            }
        }

        data.startup?.let {
            if (it.target.isNotEmpty() && !it.target.startsWith("app:")) {
                throw BackupException(BackupStage.SCHEMA, "invalid startup target")
            }
            if (it.target.length > BackupContract.MAX_STARTUP_CHARS ||
                it.label.length > BackupContract.MAX_STARTUP_CHARS
            ) {
                throw BackupException(BackupStage.SCHEMA, "startup target is too long")
            }
            if (it.target.startsWith("app:")) {
                requirePackage(it.target.removePrefix("app:"))
            }
        }

        if (enforcePlainPolicy && data.discovery.values.any {
                it.favorites.isNotEmpty() || it.hidden.isNotEmpty() || it.usage.isNotEmpty()
            }
        ) {
            throw BackupException(
                BackupStage.SCHEMA,
                "favorites and history require an encrypted export",
            )
        }

        if (data.profiles != null) {
            val known = data.profiles.profiles.mapTo(HashSet()) { it.id }
            (data.homes.keys + data.discovery.keys + data.kids.keys).forEach { id ->
                if (id !in known) warnings += BackupWarning.REPAIRED
            }
        }
        if (data.homes.values.any { home ->
                home.order.any { it is HomeOrderEntry.App }
            }
        ) {
            // A package name in the layout may be absent on this TV; that is a
            // warning, not a reason to drop the user's position.
            warnings += BackupWarning.MISSING_APPS
        }
        if (data.kids.values.any { it.allowlist.isNotEmpty() }) {
            warnings += BackupWarning.KIDS_PIN_RESET
        }
        if (data.weather?.enabled == true || data.language != null || data.content != null) {
            warnings += BackupWarning.PERMISSIONS_NOT_RESTORED
        }
        // A local copy is intentionally plain, so "needs an encrypted file"
        // would be false on the preview page. Export is where the policy is
        // enforced: a plain export containing discovery or a manual city is
        // rejected above instead of being described as merely risky.
        return warnings.toList()
    }

    private fun requirePackage(value: String) {
        if (value.length !in 1..BackupContract.MAX_PACKAGE_CHARS ||
            !packageName.matches(value)
        ) {
            throw BackupException(BackupStage.SCHEMA, "invalid package name")
        }
    }

    private fun requireItem(item: DiscoveryItem) {
        if (item.id.isBlank() || item.id.length > 512 ||
            item.packageName.length !in 1..BackupContract.MAX_PACKAGE_CHARS ||
            !packageName.matches(item.packageName) ||
            item.title.isBlank() || item.title.length > 256 ||
            (item.subtitle?.length ?: 0) > 256 ||
            (item.packageLabel?.length ?: 0) > 256 ||
            (item.activityName?.length ?: 0) > 256 ||
            (item.landscapeUri?.length ?: 0) > 4_096 ||
            (item.posterUri?.length ?: 0) > 4_096 ||
            (item.intentUri?.length ?: 0) > 4_096
        ) {
            throw BackupException(BackupStage.SCHEMA, "invalid discovery item")
        }
        item.progress?.let {
            if (!it.isFinite() || it !in 0f..1f) {
                throw BackupException(BackupStage.SCHEMA, "invalid discovery progress")
            }
        }
    }
}
