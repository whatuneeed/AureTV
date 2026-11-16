package com.whatuneed.ovisiontv.kotlin.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDownloaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cacheDir = File(context.cacheDir, "updates")
    private lateinit var downloader: UpdateDownloader

    @Before
    fun setUp() {
        cacheDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun writesAValidatedFileAndReportsProgress() = runBlocking {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val release = release(bytes)
        var calls = 0
        downloader = UpdateDownloader(context, METADATA_URL) {
            calls++
            FakeConnection(it, bytes)
        }

        val result = downloader.download(release) { _, _ -> }

        assertTrue(result is UpdateDownloadResult.Success)
        val success = result as UpdateDownloadResult.Success
        assertEquals(bytes.size.toLong(), success.sizeBytes)
        assertArrayEquals(bytes, success.file.readBytes())
        assertEquals(1, calls)
    }

    @Test
    fun rejectsADigestMismatchAndDeletesThePartial() = runBlocking {
        val bytes = "not the declared bytes".toByteArray()
        val release = release(bytes).copy(
            asset = release(bytes).asset.copy(sha256 = "0".repeat(64)),
        )
        downloader = UpdateDownloader(context, METADATA_URL) { FakeConnection(it, bytes) }

        val result = downloader.download(release) { _, _ -> }

        assertEquals(UpdateError.INTEGRITY, (result as UpdateDownloadResult.Failure).error)
        assertFalse(downloader.apkFile(release).exists())
        assertFalse(File(cacheDir, "update-${release.versionCode}.apk.part").exists())
    }

    @Test
    fun doesNotTouchANetworkHostOutsideThePolicy() = runBlocking {
        val bytes = "payload".toByteArray()
        val release = release(bytes).copy(
            asset = release(bytes).asset.copy(url = "https://evil.example/update.apk"),
        )
        var calls = 0
        downloader = UpdateDownloader(context, METADATA_URL) {
            calls++
            FakeConnection(it, bytes)
        }

        val result = downloader.download(release) { _, _ -> }

        assertEquals(UpdateError.NETWORK, (result as UpdateDownloadResult.Failure).error)
        assertEquals(0, calls)
    }

    @Test
    fun reusesAFileWhoseSizeAndDigestAlreadyMatch() = runBlocking {
        val bytes = "already here".toByteArray()
        val release = release(bytes)
        var calls = 0
        downloader = UpdateDownloader(context, METADATA_URL) {
            calls++
            FakeConnection(it, bytes)
        }
        val target = downloader.apkFile(release)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)

        val result = downloader.download(release) { _, _ -> }

        assertTrue(result is UpdateDownloadResult.Success)
        assertEquals(0, calls)
        assertArrayEquals(bytes, target.readBytes())
    }

    private fun release(bytes: ByteArray): UpdateRelease = UpdateRelease(
        versionCode = 82,
        versionName = "0.5.9-test",
        channel = UpdateChannel.STABLE,
        generatedAt = "2026-11-16T00:00:00Z",
        expiresAt = "2027-11-16T00:00:00Z",
        releaseDate = "2026-11-16T00:00:00Z",
        minAndroidApi = 28,
        minSupportedVersionCode = 0,
        mandatory = false,
        notes = mapOf("en" to listOf("Test")),
        asset = UpdateAsset(
            url = "$METADATA_URL.apk",
            sha256 = sha256(bytes),
            sizeBytes = bytes.size.toLong(),
        ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private class FakeConnection(
        url: URL,
        private val body: ByteArray,
    ) : HttpURLConnection(url) {
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun getResponseCode(): Int = HTTP_OK
        override fun getHeaderField(name: String?): String? = null
        override fun getContentLength(): Int = body.size
        override fun getContentLengthLong(): Long = body.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    }

    private companion object {
        const val METADATA_URL =
            "https://github.com/whatuneed/ovisiontv/releases/download/v0.5.9/update.apk"
    }
}
