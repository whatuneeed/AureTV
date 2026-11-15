package com.whatuneed.ovisiontv.kotlin.ui.components

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AerialPosterStoreTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun a_corrupt_poster_file_is_removed_instead_of_retried_forever() {
        val clipId = "corrupt-${System.nanoTime()}"
        val name = MessageDigest.getInstance("SHA-256")
            .digest(clipId.toByteArray())
            .take(16)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val dir = File(context.cacheDir, "aerial_posters").apply { mkdirs() }
        val file = File(dir, "$name.jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        assertNull(AerialPosterStore.load(context, clipId))
        assertFalse("the unreadable JPEG must be removed", file.exists())
    }
}
