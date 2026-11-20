package app.aure.tv.ambient

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AmbientPhotoDecoderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun a_large_photo_is_bounded_to_the_requested_size() {
        val file = writePhoto("wide.png", 400, 200)
        val decoded = AmbientPhotoDecoder.decodeNow(context, Uri.fromFile(file), 100, 100)

        assertNotNull(decoded)
        assertTrue(decoded!!.width <= 100)
        assertTrue(decoded.height <= 50)
        decoded.recycle()
    }

    @Test
    fun a_broken_file_returns_null_instead_of_crashing() {
        val file = File(context.cacheDir, "broken-photo.jpg").apply {
            writeText("this is not a photo")
        }

        assertNull(AmbientPhotoDecoder.decodeNow(context, Uri.fromFile(file)))
    }

    private fun writePhoto(name: String, width: Int, height: Int): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }
}
