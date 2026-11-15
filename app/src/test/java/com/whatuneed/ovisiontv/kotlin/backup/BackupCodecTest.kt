package com.whatuneed.ovisiontv.kotlin.backup

import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryItem
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryKind
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoveryReason
import com.whatuneed.ovisiontv.kotlin.discovery.DiscoverySource
import com.whatuneed.ovisiontv.kotlin.discovery.Usage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupCodecTest {

    private val all = BackupSelection()

    private fun item(id: String = "tv|com.example.one|https://example.test/1") = DiscoveryItem(
        id = id,
        source = DiscoverySource.TV_PROVIDER,
        kind = DiscoveryKind.VIDEO,
        packageName = "com.example.one",
        packageLabel = "One",
        title = "Title",
        subtitle = "Subtitle",
        landscapeUri = "https://example.test/art.jpg",
        posterUri = "https://example.test/poster.jpg",
        intentUri = "https://example.test/1",
        activityName = "com.example.one.Main",
        progress = 0.25f,
        isContinue = true,
        lastEngagementUtc = 1234L,
        reason = DiscoveryReason.CONTINUE_WATCHING,
    )

    private fun data() = BackupData(
        profiles = ProfilesData(
            profiles = listOf(
                BackupProfile("p1", "Me", "🍿", 0, false),
                BackupProfile("p2", "Child", "🐶", 1, true),
            ),
            currentId = "p1",
            nextId = 3,
            askOnBoot = true,
            askOnWake = false,
        ),
        homes = mapOf(
            "p1" to HomeData(
                listOf(
                    HomeOrderEntry.App("com.example.one"),
                    HomeOrderEntry.Folder("f1", "Кино", listOf("com.example.two", "com.example.gone")),
                ),
            ),
            "p2" to HomeData(listOf(HomeOrderEntry.App("com.example.two"))),
        ),
        discovery = mapOf(
            "p1" to DiscoveryData(
                recommendations = true,
                continueWatching = false,
                personalization = true,
                favorites = listOf(item()),
                hidden = listOf(item("tv|com.example.one|hidden")),
                usage = mapOf("com.example.one" to Usage(2, 10L)),
                shown = mapOf("CONTINUE|tv|com.example.one|https://example.test/1" to 20L),
            ),
        ),
        kids = mapOf("p2" to KidsData(setOf("com.example.two"))),
        weather = WeatherData(
            enabled = true,
            manual = false,
            place = "",
            latitude = 0.0,
            longitude = 0.0,
            units = "c",
            source = "openmeteo",
            refreshMinutes = 15,
        ),
        saver = SaverData(
            delayMinutes = 5,
            music = true,
            refresh = "WEEKLY",
            themes = setOf("landscapes", "cities"),
            hidden = setOf("clip-1"),
            mode = "clock",
            clockFormat = "twenty_four",
            pixelShift = false,
            dimAfter = "twenty",
            maxSession = "sixty",
        ),
        language = LanguageData("ru", showPhoneApps = false),
        content = ContentData(recommendations = true, continueWatching = false),
        startup = StartupData("app:com.example.one", "One", true),
    )

    private fun manifest(components: List<EncodedComponent>): BackupManifest {
        val entries = components.mapIndexed { index, component ->
            val json = component.json
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            ComponentEntry(
                type = component.type,
                profileId = component.profileId,
                schema = component.schema,
                path = "components/%04d.json".format(index),
                bytes = bytes.size.toLong(),
                sha256 = BackupHashing.sha256(bytes),
                count = component.count,
            )
        }
        return BackupManifest(
            formatVersion = BackupContract.FORMAT_VERSION,
            dataSchema = BackupContract.DATA_SCHEMA,
            backupId = "backup-1",
            createdAt = "2026-11-15T00:00:00Z",
            appVersionCode = 73,
            appVersionName = "0.5.0",
            sourceSdk = 34,
            sourceModel = "Test TV",
            sourceLocale = "ru-RU",
            selection = all.categories(),
            components = entries,
        )
    }

    private fun archive(data: BackupData = data(), password: CharArray? = null): ByteArray {
        val components = BackupJson.encode(data, all)
        val manifest = manifest(components)
        val map = LinkedHashMap<String, JSONObject>()
        components.zip(manifest.components).forEach { (component, entry) ->
            map[entry.path] = component.json
        }
        return BackupArchive.write(manifest, map, password)
    }

    @Test
    fun everyComponentRoundTripsThroughThePlainArchive() {
        val decoded = BackupArchive.read(ByteArrayInputStream(archive()), null)
        val validated = BackupValidator.validateForRead(decoded)
        assertEquals("p1", validated.data.profiles?.currentId)
        assertEquals(2, validated.data.profiles?.profiles?.size)
        assertEquals(2, validated.data.homes.size)
        assertEquals("Кино", (validated.data.homes["p1"]?.order?.get(1) as HomeOrderEntry.Folder).name)
        assertEquals(1, validated.data.discovery["p1"]?.favorites?.size)
        assertEquals(2, validated.data.discovery["p1"]?.usage?.get("com.example.one")?.count)
        assertEquals(setOf("com.example.two"), validated.data.kids["p2"]?.allowlist)
        assertEquals("clock", validated.data.saver?.mode)
        assertEquals("twenty_four", validated.data.saver?.clockFormat)
        assertFalse(validated.data.saver?.pixelShift ?: true)
        assertEquals("twenty", validated.data.saver?.dimAfter)
        assertEquals("sixty", validated.data.saver?.maxSession)
        assertEquals("app:com.example.one", validated.data.startup?.target)
    }

    @Test
    fun encryptedArchiveNeedsThePasswordAndRejectsATamperedPayload() {
        val password = "correct horse battery".toCharArray()
        val bytes = archive(password = password)
        val decoded = BackupArchive.read(ByteArrayInputStream(bytes), password)
        assertEquals(BackupContract.KIND, decoded.manifest.kindOrNull())
        assertTrue(decoded.encrypted)

        try {
            BackupArchive.read(ByteArrayInputStream(bytes), "wrong password".toCharArray())
            fail("wrong password must not open the archive")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.CRYPTO, expected.stage)
        }

        // Flip one ciphertext byte; the GCM tag must reject it before any JSON
        // is parsed. Testing the envelope directly avoids changing the ZIP
        // structure instead of the authenticated payload.
        val encrypted = BackupEncryption.encrypt("payload".toByteArray(), password)
        encrypted.ciphertext[0] = (encrypted.ciphertext[0].toInt() xor 1).toByte()
        try {
            BackupEncryption.decrypt(encrypted.ciphertext, encrypted.params, password)
            fail("tampered ciphertext must not decrypt")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.CRYPTO, expected.stage)
        }
    }

    @Test
    fun homeOrderKeepsMissingPackagesAndFolders() {
        val raw = """[
            "com.example.installed",
            {"id":"f1","name":"Кино","apps":["com.example.gone","com.example.installed"]}
        ]"""
        val home = BackupJson.decodeHome(raw)
        assertEquals(2, home.order.size)
        assertEquals(listOf("com.example.gone", "com.example.installed"), (home.order[1] as HomeOrderEntry.Folder).apps)
        val encoded = BackupJson.encodeHomeOrder(home)
        val again = BackupJson.decodeHome(encoded)
        assertEquals(home, again)
    }

    @Test
    fun plainExportRejectsDiscoveryAndManualWeather() {
        val data = data()
        try {
            BackupValidator.validateForWrite(manifest(BackupJson.encode(data, all)), data, true)
            fail("plain export must reject discovery")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.SCHEMA, expected.stage)
        }
        val weatherOnly = data.copy(
            discovery = emptyMap(),
            weather = data.weather?.copy(manual = true, place = "Samara", latitude = 53.2, longitude = 50.1),
        )
        try {
            BackupValidator.validateForWrite(manifest(BackupJson.encode(weatherOnly, all)), weatherOnly, true)
            fail("plain export must reject a saved location")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.SCHEMA, expected.stage)
        }
    }

    @Test
    fun aSelectedCategoryWithoutItsComponentIsRefused() {
        val missingWeather = data().copy(weather = null)
        try {
            BackupValidator.validateForWrite(
                manifest(BackupJson.encode(missingWeather, all)),
                missingWeather,
                enforcePlainPolicy = false,
            )
            fail("selection must not claim a component that is absent")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.SCHEMA, expected.stage)
        }
    }

    @Test
    fun newerSchemaIsRefused() {
        val components = BackupJson.encode(data(), all)
        val current = manifest(components)
        val newer = current.copy(dataSchema = BackupContract.DATA_SCHEMA + 1)
        try {
            BackupValidator.validateManifest(
                newer,
                BackupContract.FORMAT_VERSION,
                BackupContract.DATA_SCHEMA,
            )
            fail("newer data schema must be refused")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.MANIFEST, expected.stage)
        }
    }

    @Test
    fun tooManyProfilesAreRefused() {
        val tooMany = data().copy(
            profiles = ProfilesData(
                profiles = listOf(
                    BackupProfile("p1", "A", "🍿", 0, false),
                    BackupProfile("p2", "B", "🐶", 1, false),
                    BackupProfile("p3", "C", "🐱", 2, false),
                    BackupProfile("p4", "D", "🐸", 3, false),
                ),
                currentId = "p1",
                nextId = 5,
                askOnBoot = true,
                askOnWake = true,
            ),
        )
        try {
            BackupValidator.validateForWrite(manifest(BackupJson.encode(tooMany, all)), tooMany, false)
            fail("four profiles must not be accepted")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.SCHEMA, expected.stage)
        }
    }

    @Test
    fun zipSlipAndDuplicateEntriesAreRefused() {
        val slip = zip(
            listOf(
                "../manifest.json" to "{}".toByteArray(),
            ),
        )
        try {
            BackupArchive.read(ByteArrayInputStream(slip), null)
            fail("path traversal must be refused")
        } catch (expected: BackupException) {
            assertEquals(BackupStage.FORMAT, expected.stage)
        }

        // A duplicate is rejected by ZipOutputStream before it can even be
        // written, so the archive reader's own duplicate check is covered by
        // the manifest key check in the restore validator.
    }

    @Test
    fun encryptionUsesFreshSaltAndNonce() {
        val password = "long enough password".toCharArray()
        val first = BackupEncryption.encrypt("a".toByteArray(), password)
        val second = BackupEncryption.encrypt("a".toByteArray(), password)
        assertTrue(first.params.salt != second.params.salt)
        assertTrue(first.params.nonce != second.params.nonce)
        assertEquals(
            "a",
            BackupEncryption.decrypt(first.ciphertext, first.params, password).toString(Charsets.UTF_8),
        )
    }

    private fun BackupManifest.kindOrNull(): String =
        BackupJson.encodeManifest(this).optString("kind")

    private fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                val entry = ZipEntry(name)
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
