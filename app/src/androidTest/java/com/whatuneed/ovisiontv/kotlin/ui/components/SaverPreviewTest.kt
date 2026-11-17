package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.whatuneed.ovisiontv.kotlin.data.AerialClip
import com.whatuneed.ovisiontv.kotlin.data.SaverTheme
import com.whatuneed.ovisiontv.kotlin.ui.OVisionTheme
import com.whatuneed.ovisiontv.kotlin.ui.rememberTvScale
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaverPreviewTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun the_preview_names_the_clip_in_the_language_of_the_screen() {
        val clip = AerialClip(
            id = "goa",
            nameEn = "Goa Beaches",
            nameRu = "Пляжи Гоа",
            theme = SaverTheme.LANDSCAPES,
            videoUrl = "https://cdn.example/goa.mov",
            previewUrl = "",
            order = 0,
        )
        val russian = Configuration().apply { setLocale(Locale("ru", "RU")) }
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides russian) {
                OVisionTheme {
                    SaverPreview(
                        scale = rememberTvScale(),
                        clip = clip,
                        themes = setOf(SaverTheme.LANDSCAPES),
                    )
                }
            }
        }
        rule.onNodeWithText("Пляжи Гоа").assertExists()
    }

    @Test
    fun a_corrupt_oversized_preview_stays_within_the_pixel_budget() {
        val url = "https://sylvan.apple.com/aerial/oversized-load.png"
        val file = seed(url, png(10000, 10000, byteArrayOf(0, 1, 2, 3)))
        val bitmap = AerialPreviewStore.load(targetContext(), url)
        // Skia returns a partial bitmap for a corrupt IDAT instead of null; it must stay bounded.
        if (bitmap == null) assertFalse(file.exists())
        else assertTrue(bitmap.width.toLong() * bitmap.height <= 4_000_000)
    }

    @Test
    fun a_corrupt_oversized_thumbnail_stays_within_the_pixel_budget() {
        val url = "https://sylvan.apple.com/aerial/oversized-thumb.png"
        val file = seed(url, png(10000, 10000, byteArrayOf(0, 1, 2, 3)), thumb = true)
        val bitmap = AerialPreviewStore.loadThumbnail(targetContext(), url)
        if (bitmap == null) assertFalse(file.exists())
        else assertTrue(bitmap.width.toLong() * bitmap.height <= 4_000_000)
    }

    @Test
    fun an_undecodable_file_is_deleted_from_the_disk_cache() {
        val url = "https://sylvan.apple.com/aerial/garbage.png"
        val file = seed(url, "not a png".toByteArray())
        assertNull(AerialPreviewStore.load(targetContext(), url))
        assertFalse(file.exists())

        val thumbUrl = "https://sylvan.apple.com/aerial/garbage-thumb.png"
        val thumbFile = seed(thumbUrl, "not a png".toByteArray(), thumb = true)
        assertNull(AerialPreviewStore.loadThumbnail(targetContext(), thumbUrl))
        assertFalse(thumbFile.exists())
    }

    @Test
    fun an_oversized_but_valid_preview_is_downsampled_to_the_pixel_budget() {
        val url = "https://sylvan.apple.com/aerial/big-valid.png"
        val file = seed(url, png(3000, 3000, solidIdat(3000, 3000)))
        val bitmap = AerialPreviewStore.load(targetContext(), url)
        assertNotNull(bitmap)
        assertTrue(bitmap!!.width.toLong() * bitmap.height <= 4_000_000)
        assertTrue(file.exists())
    }

    @Test
    fun a_loaded_thumbnail_is_available_to_the_next_tile_without_io() {
        val url = "asset:aerial_previews/024891DE-B7F6-4187-BFE0-E6D237702EF0.jpg"
        val loaded = AerialPreviewStore.loadThumbnail(targetContext(), url)
        assertNotNull(loaded)
        assertSame(loaded, AerialPreviewStore.cachedThumbnail(url))
    }

    @Test
    fun a_thumbnail_is_cached_as_a_small_file_not_the_full_png() {
        AerialPreviewStore.clear(targetContext())
        val url = "asset:aerial_previews/024891DE-B7F6-4187-BFE0-E6D237702EF0.jpg"
        val bitmap = AerialPreviewStore.loadThumbnail(targetContext(), url)
        assertNotNull(bitmap)
        assertEquals(450, bitmap!!.width)
        val thumbs = File(targetContext().cacheDir, "aerial_previews/thumbs")
            .listFiles()?.filter { it.isFile }.orEmpty()
        assertTrue(thumbs.any { it.length() > 0 && it.length() < 150_000 })
    }

    @Test
    fun a_bundled_legacy_frame_loads_without_the_network() {
        val url = "asset:aerial_previews/024891DE-B7F6-4187-BFE0-E6D237702EF0.jpg"
        val bitmap = AerialPreviewStore.load(targetContext(), url)
        assertNotNull(bitmap)
        assertEquals(900, bitmap!!.width)
        assertEquals(580, bitmap.height)

        val thumb = AerialPreviewStore.loadThumbnail(targetContext(), url)
        assertNotNull(thumb)
        assertTrue(thumb!!.width > 0)
    }

    @Test
    fun a_normal_small_preview_and_thumbnail_still_load() {
        val url = "https://sylvan.apple.com/aerial/small.png"
        seed(url, png(4, 4, solidIdat(4, 4)))
        val bitmap = AerialPreviewStore.load(targetContext(), url)
        assertNotNull(bitmap)
        assertEquals(4, bitmap!!.width)

        val thumbUrl = "https://sylvan.apple.com/aerial/small-thumb.png"
        seed(thumbUrl, png(4, 4, solidIdat(4, 4)), thumb = true)
        val thumb = AerialPreviewStore.loadThumbnail(targetContext(), thumbUrl)
        assertNotNull(thumb)
        assertEquals(2, thumb!!.width)
    }

    private fun targetContext() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Mirrors AerialPreviewStore's private file naming so the test can seed the disk cache. */
    private fun seed(url: String, bytes: ByteArray, thumb: Boolean = false): File {
        val dir = File(targetContext().cacheDir, if (thumb) "aerial_previews/thumbs" else "aerial_previews")
        dir.mkdirs()
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".png"
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file
    }

    private fun png(width: Int, height: Int, idat: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArrayOutputStream().apply {
            write(be32(width))
            write(be32(height))
            write(8) // bit depth
            write(0) // grayscale
            write(0)
            write(0)
            write(0)
        }.toByteArray()
        chunk(out, "IHDR", ihdr)
        chunk(out, "IDAT", idat)
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        out.write(be32(data.size))
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.write(be32(crc.value.toInt()))
    }

    private fun be32(value: Int): ByteArray =
        byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    /** Valid zlib stream of all-zero grayscale scanlines; compresses to a few KB even for huge frames. */
    private fun solidIdat(width: Int, height: Int): ByteArray {
        val deflater = Deflater()
        deflater.setInput(ByteArray(height * (width + 1)))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }
}
