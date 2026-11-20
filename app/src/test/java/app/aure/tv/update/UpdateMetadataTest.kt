package app.aure.tv.update

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateMetadataTest {
    private val now = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
    private val hosts = setOf("updates.example.com")

    private fun json(
        channel: String = "stable",
        versionCode: Int = 81,
        versionName: String = "0.5.8",
        assetUrl: String = "https://updates.example.com/AURE-TV-0.6.0.apk",
        sha: String = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        size: Long = 4_771_546L,
        notesEn: String = "[\"Fixed focus restoration.\"]",
        notesRu: String = "[\"Исправлено восстановление фокуса.\"]",
        expires: String = "2026-12-16T12:00:00Z",
        minimumSupported: Int = 60,
    ): String = """
        {
          "schema": 1,
          "channel": "$channel",
          "generatedAt": "2026-11-16T00:00:00Z",
          "expiresAt": "$expires",
          "releases": [{
            "versionCode": $versionCode,
            "versionName": "$versionName",
            "channel": "$channel",
            "releaseDate": "2026-11-15T00:00:00Z",
            "minimumAndroidApi": 28,
            "minimumSupportedVersionCode": $minimumSupported,
            "mandatory": false,
            "notes": { "en": $notesEn, "ru": $notesRu },
            "assets": [{
              "abi": "universal",
              "url": "$assetUrl",
              "sha256": "$sha",
              "sizeBytes": $size
            }]
          }]
        }
    """.trimIndent()

    @Test
    fun aValidDocumentProducesOneCheckedRelease() {
        val result = UpdateMetadataParser.parse(json(), UpdateChannel.STABLE, hosts, now)
        assertTrue(result is UpdateMetadataResult.Success)
        val release = (result as UpdateMetadataResult.Success).releases.single()
        assertEquals(81, release.versionCode)
        assertEquals("0.5.8", release.versionName)
        assertEquals(listOf("Fixed focus restoration."), release.notesFor("en"))
        assertEquals(listOf("Исправлено восстановление фокуса."), release.notesFor("ru"))
    }

    @Test
    fun aMissingTranslationFallsBackToEnglishWithoutInventingText() {
        val result = UpdateMetadataParser.parse(json(), UpdateChannel.STABLE, hosts, now)
        val release = (result as UpdateMetadataResult.Success).releases.single()
        assertEquals(listOf("Fixed focus restoration."), release.notesFor("de"))
    }

    @Test
    fun unsupportedSchemaIsRejected() {
        val broken = json().replace("\"schema\": 1", "\"schema\": 2")
        assertTrue(UpdateMetadataParser.parse(broken, UpdateChannel.STABLE, hosts, now)
            is UpdateMetadataResult.Failure)
    }

    @Test
    fun expiredMetadataIsRejected() {
        val broken = json(expires = "2026-11-16T11:59:00Z")
        assertTrue(UpdateMetadataParser.parse(broken, UpdateChannel.STABLE, hosts, now)
            is UpdateMetadataResult.Failure)
    }

    @Test
    fun aChannelMismatchIsRejected() {
        assertTrue(UpdateMetadataParser.parse(json(), UpdateChannel.BETA, hosts, now)
            is UpdateMetadataResult.Failure)
    }

    @Test
    fun anHttpOrForeignAssetHostIsRejected() {
        assertTrue(UpdateMetadataParser.parse(
            json(assetUrl = "http://updates.example.com/app.apk"),
            UpdateChannel.STABLE,
            hosts,
            now,
        ) is UpdateMetadataResult.Failure)
        assertTrue(UpdateMetadataParser.parse(
            json(assetUrl = "https://evil.example.net/app.apk"),
            UpdateChannel.STABLE,
            hosts,
            now,
        ) is UpdateMetadataResult.Failure)
        assertTrue(UpdateMetadataParser.parse(
            json(assetUrl = "https://user@updates.example.com/app.apk"),
            UpdateChannel.STABLE,
            hosts,
            now,
        ) is UpdateMetadataResult.Failure)
    }

    @Test
    fun zeroMinimumSupportedVersionMeansNoEndOfLifeBoundary() {
        val parsed = UpdateMetadataParser.parse(
            json(minimumSupported = 0),
            UpdateChannel.STABLE,
            hosts,
            now,
        )
        assertTrue(parsed is UpdateMetadataResult.Success)
        assertEquals(0, (parsed as UpdateMetadataResult.Success).releases.single().minSupportedVersionCode)
    }

    @Test
    fun aBadDigestOrSizeIsRejected() {
        assertTrue(UpdateMetadataParser.parse(
            json(sha = "ABC"),
            UpdateChannel.STABLE,
            hosts,
            now,
        ) is UpdateMetadataResult.Failure)
        assertTrue(UpdateMetadataParser.parse(
            json(size = 0L),
            UpdateChannel.STABLE,
            hosts,
            now,
        ) is UpdateMetadataResult.Failure)
    }

    @Test
    fun duplicateVersionsAreRejected() {
        val root = org.json.JSONObject(json())
        val releases = root.getJSONArray("releases")
        releases.put(releases.getJSONObject(0))
        assertTrue(UpdateMetadataParser.parse(root.toString(), UpdateChannel.STABLE, hosts, now)
            is UpdateMetadataResult.Failure)
    }

    @Test
    fun urlPolicyRejectsCleartextPortsAndUserInfo() {
        assertTrue(UpdateUrlPolicy.isAllowed("https://updates.example.com/app.apk", hosts))
        assertFalse(UpdateUrlPolicy.isAllowed("http://updates.example.com/app.apk", hosts))
        assertFalse(UpdateUrlPolicy.isAllowed("https://updates.example.com:8443/app.apk", hosts))
        assertFalse(UpdateUrlPolicy.isAllowed("https://u:p@updates.example.com/app.apk", hosts))
        assertFalse(UpdateUrlPolicy.isAllowed("https://other.example.com/app.apk", hosts))
    }

    @Test
    fun redirectsMustRemainHttps() {
        assertEquals(
            "https://updates.example.com/next.json",
            UpdateUrlPolicy.resolveRedirect(
                "https://updates.example.com/update.json",
                "/next.json",
            ),
        )
        assertEquals(
            null,
            UpdateUrlPolicy.resolveRedirect(
                "https://updates.example.com/update.json",
                "http://updates.example.com/next.json",
            ),
        )
    }

    @Test
    fun versionComparisonUsesVersionCodeOnly() {
        assertTrue(UpdateVersion.isNewer(81, 80))
        assertFalse(UpdateVersion.isNewer(80, 80))
        assertFalse(UpdateVersion.isNewer(79, 80))
    }

    @Test
    fun compatibilityChecksApiAbiAndVersion() {
        val release = (UpdateMetadataParser.parse(json(), UpdateChannel.STABLE, hosts, now)
            as UpdateMetadataResult.Success).releases.single()
        assertTrue(UpdateCompatibility.check(release, 80, api = 28, abis = listOf("x86")).compatible)
        assertFalse(UpdateCompatibility.check(release, 80, api = 27, abis = listOf("x86")).compatible)
        assertFalse(UpdateCompatibility.check(release, 81, api = 34, abis = listOf("x86")).compatible)
        val armOnly = release.copy(asset = release.asset.copy(abi = "arm64-v8a"))
        assertFalse(UpdateCompatibility.check(armOnly, 80, api = 34, abis = listOf("x86")).compatible)
    }
}
