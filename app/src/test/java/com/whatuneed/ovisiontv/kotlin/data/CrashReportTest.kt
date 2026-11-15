package com.whatuneed.ovisiontv.kotlin.data

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {

    @Test
    fun a_crash_report_names_the_thread_and_keeps_the_stack() {
        val error = IllegalStateException("catalogue failed")

        val text = CrashReport.describe(123L, "main", error)

        assertTrue(text.startsWith("time=123\nthread=main\n"))
        assertTrue(text.contains("IllegalStateException: catalogue failed"))
    }
}
