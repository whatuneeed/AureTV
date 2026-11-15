package com.whatuneed.ovisiontv.kotlin.backup

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.Usage
import com.whatuneed.ovisiontv.kotlin.discovery.decodeItems
import com.whatuneed.ovisiontv.kotlin.discovery.decodeShown
import com.whatuneed.ovisiontv.kotlin.discovery.decodeUsage
import com.whatuneed.ovisiontv.kotlin.discovery.encodeItems
import com.whatuneed.ovisiontv.kotlin.discovery.encodeShown
import com.whatuneed.ovisiontv.kotlin.discovery.encodeUsage
import org.json.JSONArray
import org.json.JSONObject

internal data class EncodedComponent(
    val type: BackupComponentType,
    val profileId: String?,
    val schema: Int,
    val json: JSONObject,
    val count: Int,
)

/**
 * The typed payload codec. It deliberately does not dump SharedPreferences XML:
 * a key written by a future build must not become an unvalidated instruction
 * after a restore. All component readers and writers go through this object.
 */
internal object BackupJson {

    // ---------------------------------------------------------------- manifest

    fun encodeManifest(manifest: BackupManifest): JSONObject = JSONObject()
        .put("kind", BackupContract.KIND)
        .put("formatVersion", manifest.formatVersion)
        .put("dataSchema", manifest.dataSchema)
        .put("backupId", manifest.backupId)
        .put("createdAt", manifest.createdAt)
        .put(
            "app",
            JSONObject()
                .put("versionCode", manifest.appVersionCode)
                .put("versionName", manifest.appVersionName),
        )
        .put(
            "source",
            JSONObject()
                .put("sdk", manifest.sourceSdk)
                .put("model", manifest.sourceModel)
                .put("locale", manifest.sourceLocale),
        )
        .put("selection", JSONArray(manifest.selection.map { it.id }))
        .put(
            "components",
            JSONArray().apply {
                manifest.components.forEach { component ->
                    put(
                        JSONObject()
                            .put("type", component.type.id)
                            .put("profileId", component.profileId ?: JSONObject.NULL)
                            .put("schema", component.schema)
                            .put("path", component.path)
                            .put("bytes", component.bytes)
                            .put("sha256", component.sha256)
                            .put("count", component.count),
                    )
                }
            },
        )
        .put(
            "encryption",
            manifest.encryption?.let {
                JSONObject()
                    .put("cipher", it.cipher)
                    .put("kdf", it.kdf)
                    .put("iterations", it.iterations)
                    .put("salt", it.salt)
                    .put("nonce", it.nonce)
                    .put("tagBits", it.tagBits)
            } ?: JSONObject.NULL,
        )
        .put(
            "payload",
            manifest.payload?.let {
                JSONObject()
                    .put("path", it.path)
                    .put("bytes", it.bytes)
                    .put("sha256", it.sha256)
            } ?: JSONObject.NULL,
        )

    fun decodeManifest(raw: String): BackupManifest {
        val root = parseObject(raw, "manifest")
        if (root.optString("kind") != BackupContract.KIND) {
            throw BackupException(BackupStage.MANIFEST, "not an OVisionTV backup")
        }
        val selection = root.optJSONArray("selection")?.toStringSet()
            ?.mapNotNull(BackupCategory::fromId)
            ?.toSet()
            ?: emptySet()
        val components = ArrayList<ComponentEntry>()
        val array = root.optJSONArray("components") ?: JSONArray()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index)
                ?: throw BackupException(BackupStage.MANIFEST, "component entry is not an object")
            val type = BackupComponentType.fromId(entry.optString("type"))
                ?: throw BackupException(BackupStage.MANIFEST, "unknown component type")
            components += ComponentEntry(
                type = type,
                profileId = entry.optString("profileId")
                    .takeIf { it.isNotBlank() && it != "null" },
                schema = entry.optInt("schema", 0),
                path = entry.optString("path"),
                bytes = entry.optLong("bytes", -1L),
                sha256 = entry.optString("sha256"),
                count = entry.optInt("count", 0),
            )
        }
        return BackupManifest(
            formatVersion = root.optInt("formatVersion", -1),
            dataSchema = root.optInt("dataSchema", -1),
            backupId = root.optString("backupId"),
            createdAt = root.optString("createdAt"),
            appVersionCode = root.optJSONObject("app")?.optInt("versionCode", 0) ?: 0,
            appVersionName = root.optJSONObject("app")?.optString("versionName").orEmpty(),
            sourceSdk = root.optJSONObject("source")?.optInt("sdk", 0) ?: 0,
            sourceModel = root.optJSONObject("source")?.optString("model").orEmpty(),
            sourceLocale = root.optJSONObject("source")?.optString("locale").orEmpty(),
            selection = selection,
            components = components,
            encryption = root.optJSONObject("encryption")?.let {
                EncryptionParams(
                    cipher = it.optString("cipher"),
                    kdf = it.optString("kdf"),
                    iterations = it.optInt("iterations", 0),
                    salt = it.optString("salt"),
                    nonce = it.optString("nonce"),
                    tagBits = it.optInt("tagBits", 0),
                )
            },
            payload = root.optJSONObject("payload")?.let {
                PayloadRef(
                    path = it.optString("path"),
                    bytes = it.optLong("bytes", -1L),
                    sha256 = it.optString("sha256"),
                )
            },
        )
    }

    // ------------------------------------------------------------------- data

    fun encode(data: BackupData, selection: BackupSelection): List<EncodedComponent> = buildList {
        if (selection.profiles) {
            data.profiles?.let { profiles ->
                add(
                    EncodedComponent(
                        type = BackupComponentType.PROFILES,
                        profileId = null,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeProfiles(profiles),
                        count = profiles.profiles.size,
                    ),
                )
            }
        }
        if (selection.home) {
            data.homes.forEach { (profileId, home) ->
                add(
                    EncodedComponent(
                        type = BackupComponentType.HOME,
                        profileId = profileId,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeHome(home),
                        count = home.order.size,
                    ),
                )
            }
        }
        if (selection.discovery) {
            data.discovery.forEach { (profileId, discovery) ->
                add(
                    EncodedComponent(
                        type = BackupComponentType.DISCOVERY,
                        profileId = profileId,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeDiscovery(discovery),
                        count = discovery.favorites.size + discovery.hidden.size,
                    ),
                )
            }
        }
        if (selection.kids) {
            data.kids.forEach { (profileId, kids) ->
                add(
                    EncodedComponent(
                        type = BackupComponentType.KIDS,
                        profileId = profileId,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeKids(kids),
                        count = kids.allowlist.size,
                    ),
                )
            }
        }
        if (selection.weather) {
            data.weather?.let {
                add(
                    EncodedComponent(
                        type = BackupComponentType.WEATHER,
                        profileId = null,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeWeather(it),
                        count = 1,
                    ),
                )
            }
        }
        if (selection.saver) {
            data.saver?.let {
                add(
                    EncodedComponent(
                        type = BackupComponentType.SAVER,
                        profileId = null,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeSaver(it),
                        count = it.themes.size + it.hidden.size,
                    ),
                )
            }
        }
        if (selection.language) {
            data.language?.let {
                add(
                    EncodedComponent(
                        type = BackupComponentType.LANGUAGE,
                        profileId = null,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeLanguage(it),
                        count = 1,
                    ),
                )
            }
        }
        if (selection.content) {
            data.content?.let {
                add(
                    EncodedComponent(
                        type = BackupComponentType.CONTENT,
                        profileId = null,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeContent(it),
                        count = 1,
                    ),
                )
            }
        }
        if (selection.startup) {
            data.startup?.let {
                add(
                    EncodedComponent(
                        type = BackupComponentType.STARTUP,
                        profileId = null,
                        schema = BackupContract.DATA_SCHEMA,
                        json = encodeStartup(it),
                        count = 1,
                    ),
                )
            }
        }
    }

    fun decode(components: List<Pair<ComponentEntry, String>>): BackupData {
        var profiles: ProfilesData? = null
        val homes = LinkedHashMap<String, HomeData>()
        val discovery = LinkedHashMap<String, DiscoveryData>()
        val kids = LinkedHashMap<String, KidsData>()
        var weather: WeatherData? = null
        var saver: SaverData? = null
        var language: LanguageData? = null
        var content: ContentData? = null
        var startup: StartupData? = null
        val seen = HashSet<String>()
        components.forEach { (entry, raw) ->
            if (!seen.add(entry.key)) {
                throw BackupException(BackupStage.SCHEMA, "duplicate component ${entry.key}")
            }
            val json = parseObject(raw, entry.key)
            when (entry.type) {
                BackupComponentType.PROFILES -> {
                    if (entry.profileId != null) {
                        throw BackupException(BackupStage.SCHEMA, "profiles must not be profile-scoped")
                    }
                    profiles = decodeProfiles(json)
                }

                BackupComponentType.HOME -> {
                    val id = requireProfileId(entry)
                    homes[id] = decodeHome(json)
                }

                BackupComponentType.DISCOVERY -> {
                    val id = requireProfileId(entry)
                    discovery[id] = decodeDiscovery(json)
                }

                BackupComponentType.KIDS -> {
                    val id = requireProfileId(entry)
                    kids[id] = decodeKids(json)
                }

                BackupComponentType.WEATHER -> weather = decodeWeather(json)
                BackupComponentType.SAVER -> saver = decodeSaver(json)
                BackupComponentType.LANGUAGE -> language = decodeLanguage(json)
                BackupComponentType.CONTENT -> content = decodeContent(json)
                BackupComponentType.STARTUP -> startup = decodeStartup(json)
            }
        }
        return BackupData(
            profiles = profiles,
            homes = homes,
            discovery = discovery,
            kids = kids,
            weather = weather,
            saver = saver,
            language = language,
            content = content,
            startup = startup,
        )
    }

    // --------------------------------------------------------------- profiles

    private fun encodeProfiles(data: ProfilesData): JSONObject = JSONObject()
        .put("current", data.currentId)
        .put("nextId", data.nextId)
        .put("askOnBoot", data.askOnBoot)
        .put("askOnWake", data.askOnWake)
        .put(
            "profiles",
            JSONArray().apply {
                data.profiles.forEach { profile ->
                    put(
                        JSONObject()
                            .put("id", profile.id)
                            .put("name", profile.name)
                            .put("emoji", profile.emoji)
                            .put("color", profile.color)
                            .put("kids", profile.kids),
                    )
                }
            },
        )

    private fun decodeProfiles(json: JSONObject): ProfilesData {
        val array = json.optJSONArray("profiles") ?: JSONArray()
        val profiles = ArrayList<BackupProfile>(array.length())
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index)
                ?: throw BackupException(BackupStage.SCHEMA, "profile is not an object")
            profiles += BackupProfile(
                id = entry.optString("id"),
                name = entry.optString("name"),
                emoji = entry.optString("emoji"),
                color = entry.optInt("color", 0),
                kids = entry.optBoolean("kids", false),
            )
        }
        return ProfilesData(
            profiles = profiles,
            currentId = json.optString("current"),
            nextId = json.optInt("nextId", 0),
            askOnBoot = json.optBoolean("askOnBoot", true),
            askOnWake = json.optBoolean("askOnWake", true),
        )
    }

    // ------------------------------------------------------------------- home

    fun decodeHome(raw: String?): HomeData {
        if (raw.isNullOrBlank()) return HomeData(emptyList())
        val array = try {
            JSONArray(raw)
        } catch (error: Exception) {
            throw BackupException(BackupStage.SCHEMA, "home order is not JSON", error)
        }
        val order = ArrayList<HomeOrderEntry>(array.length())
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            when (value) {
                is String -> order += HomeOrderEntry.App(value)
                is JSONObject -> {
                    val apps = ArrayList<String>()
                    val packages = value.optJSONArray("apps") ?: JSONArray()
                    for (appIndex in 0 until packages.length()) {
                        val packageName = packages.optString(appIndex)
                        if (packageName.isNotBlank()) apps += packageName
                    }
                    order += HomeOrderEntry.Folder(
                        id = value.optString("id"),
                        name = value.optString("name"),
                        apps = apps,
                    )
                }

                else -> throw BackupException(BackupStage.SCHEMA, "home entry has an unknown type")
            }
        }
        return HomeData(order)
    }

    fun encodeHome(data: HomeData): JSONObject = JSONObject().put(
        "order",
        JSONArray().apply {
            data.order.forEach { entry ->
                when (entry) {
                    is HomeOrderEntry.App -> put(entry.packageName)
                    is HomeOrderEntry.Folder -> put(
                        JSONObject()
                            .put("id", entry.id)
                            .put("name", entry.name)
                            .put("apps", JSONArray(entry.apps)),
                    )
                }
            }
        },
    )

    /** The exact array shape HomeOrderStore already reads and writes. */
    fun encodeHomeOrder(data: HomeData): String = JSONArray().apply {
        data.order.forEach { entry ->
            when (entry) {
                is HomeOrderEntry.App -> put(entry.packageName)
                is HomeOrderEntry.Folder -> put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("name", entry.name)
                        .put("apps", JSONArray(entry.apps)),
                )
            }
        }
    }.toString()

    private fun decodeHome(json: JSONObject): HomeData = decodeHome(
        json.optJSONArray("order")?.toString() ?: "[]",
    )

    // -------------------------------------------------------------- discovery

    private fun encodeDiscovery(data: DiscoveryData): JSONObject = JSONObject()
        .put("recommendations", data.recommendations)
        .put("continueWatching", data.continueWatching)
        .put("personalization", data.personalization)
        .put("favorites", JSONArray(encodeItems(data.favorites)))
        .put("hidden", JSONArray(encodeItems(data.hidden)))
        .put("usage", JSONObject(encodeUsage(data.usage)))
        .put("shown", JSONObject(encodeShown(data.shown)))

    private fun decodeDiscovery(json: JSONObject): DiscoveryData = DiscoveryData(
        recommendations = json.optBoolean("recommendations", true),
        continueWatching = json.optBoolean("continueWatching", true),
        personalization = json.optBoolean("personalization", true),
        favorites = decodeItems(json.optJSONArray("favorites")?.toString()),
        hidden = decodeItems(json.optJSONArray("hidden")?.toString()),
        usage = decodeUsage(json.optJSONObject("usage")?.toString()),
        shown = decodeShown(json.optJSONObject("shown")?.toString()),
    )

    // ------------------------------------------------------------------- kids

    private fun encodeKids(data: KidsData): JSONObject = JSONObject().put(
        "allowlist",
        JSONArray(data.allowlist.sorted()),
    )

    private fun decodeKids(json: JSONObject): KidsData {
        val array = json.optJSONArray("allowlist") ?: JSONArray()
        val allow = LinkedHashSet<String>(array.length())
        for (index in 0 until array.length()) {
            val packageName = array.optString(index)
            if (packageName.isNotBlank()) allow += packageName
        }
        return KidsData(allow)
    }

    // ---------------------------------------------------------------- weather

    private fun encodeWeather(data: WeatherData): JSONObject = JSONObject()
        .put("enabled", data.enabled)
        .put("manual", data.manual)
        .put("place", data.place)
        .put("lat", data.latitude)
        .put("lon", data.longitude)
        .put("units", data.units)
        .put("source", data.source)
        .put("refresh", data.refreshMinutes)

    private fun decodeWeather(json: JSONObject): WeatherData = WeatherData(
        enabled = json.optBoolean("enabled", false),
        manual = json.optBoolean("manual", false),
        place = json.optString("place"),
        latitude = json.optDouble("lat", 0.0),
        longitude = json.optDouble("lon", 0.0),
        units = json.optString("units", "auto"),
        source = json.optString("source", "openmeteo"),
        refreshMinutes = json.optInt("refresh", 15),
    )

    // ------------------------------------------------------------------ saver

    private fun encodeSaver(data: SaverData): JSONObject = JSONObject()
        .put("delayMinutes", data.delayMinutes)
        .put("music", data.music)
        .put("refresh", data.refresh)
        .put("themes", JSONArray(data.themes.sorted()))
        .put("hidden", JSONArray(data.hidden.sorted()))
        .put("mode", data.mode)
        .put("clockFormat", data.clockFormat)
        .put("pixelShift", data.pixelShift)

    private fun decodeSaver(json: JSONObject): SaverData {
        val themes = json.optJSONArray("themes")?.stringSet() ?: emptySet()
        val hidden = json.optJSONArray("hidden")?.stringSet() ?: emptySet()
        return SaverData(
            delayMinutes = json.optInt("delayMinutes", 5),
            music = json.optBoolean("music", true),
            refresh = json.optString("refresh", "WEEKLY"),
            themes = themes,
            hidden = hidden,
            mode = json.optString("mode", "aerial"),
            clockFormat = json.optString("clockFormat", "system"),
            pixelShift = json.optBoolean("pixelShift", true),
        )
    }

    // --------------------------------------------------------------- language

    private fun encodeLanguage(data: LanguageData): JSONObject = JSONObject()
        .put("language", data.language)
        .put("showPhoneApps", data.showPhoneApps)

    private fun decodeLanguage(json: JSONObject): LanguageData = LanguageData(
        language = json.optString("language", "system"),
        showPhoneApps = json.optBoolean("showPhoneApps", true),
    )

    // ---------------------------------------------------------------- content

    private fun encodeContent(data: ContentData): JSONObject = JSONObject()
        .put("recommendations", data.recommendations)
        .put("continueWatching", data.continueWatching)

    private fun decodeContent(json: JSONObject): ContentData = ContentData(
        recommendations = json.optBoolean("recommendations", true),
        continueWatching = json.optBoolean("continueWatching", true),
    )

    // ---------------------------------------------------------------- startup

    private fun encodeStartup(data: StartupData): JSONObject = JSONObject()
        .put("target", data.target)
        .put("label", data.label)
        .put("onWake", data.onWake)

    private fun decodeStartup(json: JSONObject): StartupData = StartupData(
        target = json.optString("target"),
        label = json.optString("label"),
        onWake = json.optBoolean("onWake", false),
    )

    // ------------------------------------------------------------------ utils

    private fun requireProfileId(entry: ComponentEntry): String =
        entry.profileId?.takeIf { it.isNotBlank() }
            ?: throw BackupException(BackupStage.SCHEMA, "${entry.key} has no profile id")

    private fun parseObject(raw: String, name: String): JSONObject {
        if (raw.length > BackupContract.MAX_JSON_CHARS) {
            throw BackupException(BackupStage.SCHEMA, "$name is too large")
        }
        return try {
            JSONObject(raw)
        } catch (error: Exception) {
            throw BackupException(BackupStage.SCHEMA, "$name is not JSON", error)
        }
    }

    private fun JSONArray.toStringSet(): Set<String> {
        val values = LinkedHashSet<String>(length())
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) values += value
        }
        return values
    }

    private fun JSONArray.stringSet(): Set<String> = toStringSet()
}
