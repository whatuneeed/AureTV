package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AtomicWriteTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun a_new_file_is_written_and_no_temp_is_left_behind() {
        val target = File(folder.root, "catalogue.json")
        assertTrue(writeFileAtomically(target, "one".toByteArray()))
        assertArrayEquals("one".toByteArray(), target.readBytes())
        assertTrue(folder.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun an_existing_file_is_replaced() {
        val target = File(folder.root, "catalogue.json")
        target.writeText("old")
        assertTrue(writeFileAtomically(target, "new".toByteArray()))
        assertArrayEquals("new".toByteArray(), target.readBytes())
    }

    @Test
    fun a_missing_parent_directory_is_created() {
        val target = File(folder.root, "nested/dir/catalogue.json")
        assertTrue(writeFileAtomically(target, "x".toByteArray()))
        assertArrayEquals("x".toByteArray(), target.readBytes())
    }

    @Test
    fun concurrent_writers_leave_one_complete_file() {
        val target = File(folder.root, "catalogue.json")
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val done = CountDownLatch(4)
        repeat(4) { index ->
            pool.execute {
                start.await()
                writeFileAtomically(target, "writer-$index".toByteArray())
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()
        val text = target.readText()
        assertTrue(text.startsWith("writer-"))
        assertTrue(folder.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun a_directory_target_is_refused_without_touching_it() {
        val target = File(folder.root, "catalogue.json")
        assertTrue(target.mkdirs())
        assertFalse(writeFileAtomically(target, "x".toByteArray()))
        assertTrue(target.isDirectory)
    }
}
