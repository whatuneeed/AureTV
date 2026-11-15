package com.whatuneed.ovisiontv.kotlin.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashReportInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun clearing_the_diagnostic_removes_only_the_last_trace() {
        val trace = File(File(context.cacheDir, "diagnostics"), "last-crash.txt")
        trace.parentFile?.mkdirs()
        trace.writeText("time=1\nthread=main\njava.lang.IllegalStateException")

        val other = File(File(context.cacheDir, "diagnostics"), "keep.txt")
        other.writeText("not a crash trace")
        try {
            assertTrue(CrashReport.clear(context))
            assertFalse(trace.exists())
            assertTrue("other cache files must survive", other.exists())
        } finally {
            other.delete()
        }
    }

    @Test
    fun clearing_an_absent_trace_is_harmless() {
        val trace = File(File(context.cacheDir, "diagnostics"), "last-crash.txt")
        trace.delete()
        assertFalse(CrashReport.clear(context))
    }
}
