package com.whatuneed.ovisiontv.kotlin.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverPreviewBudgetTest {

    private fun file(name: String, bytes: Int, modified: Long): File =
        File.createTempFile("preview-$name", ".png").apply {
            writeBytes(ByteArray(bytes))
            setLastModified(modified)
            deleteOnExit()
        }

    @Test
    fun the_count_cap_removes_the_oldest_files_first() {
        val files = listOf(
            file("old", 10, 1_000),
            file("middle", 10, 2_000),
            file("new", 10, 3_000),
        )

        val removed = previewFilesToDelete(files, keep = 2, maxBytes = 1_000)

        assertEquals(listOf(files[0]), removed)
    }

    @Test
    fun the_byte_cap_removes_more_than_the_count_cap_would() {
        val files = listOf(
            file("a", 10, 1_000),
            file("b", 10, 2_000),
            file("c", 10, 3_000),
        )

        val removed = previewFilesToDelete(files, keep = 3, maxBytes = 15)

        assertEquals(listOf(files[0], files[1]), removed)
        assertTrue(removed.size < files.size)
    }

    @Test
    fun a_cache_within_both_caps_is_left_alone() {
        val files = listOf(file("a", 10, 1_000), file("b", 10, 2_000))

        assertTrue(previewFilesToDelete(files, keep = 2, maxBytes = 20).isEmpty())
    }
}
