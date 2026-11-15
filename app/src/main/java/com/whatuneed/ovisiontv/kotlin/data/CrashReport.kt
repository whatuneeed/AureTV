package com.whatuneed.ovisiontv.kotlin.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last uncaught exception on disk so a launcher that dies on a TV
 * without adb still leaves evidence. The report is local, bounded and contains
 * no user data: the system still receives the original exception, and this
 * handler never tries to recover a process that Android is already killing.
 */
object CrashReport {
    private const val TAG = "CrashReport"
    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_CHARS = 32 * 1024

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
                .onFailure { Log.w(TAG, "could not save the crash report", it) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val file = file(context)
        file.parentFile?.mkdirs()
        val text = describe(System.currentTimeMillis(), thread.name, error).take(MAX_CHARS)
        // Replace the previous crash rather than growing without a limit.
        file.writeText(text, Charsets.UTF_8)
    }

    /** The single local trace; exposed so Storage can delete it without touching other cache. */
    internal fun file(context: Context): File =
        File(File(context.applicationContext.cacheDir, "diagnostics"), FILE_NAME)

    /** Removes only the local crash trace; false means there was nothing to remove. */
    fun clear(context: Context): Boolean = runCatching { file(context).delete() }.getOrDefault(false)

    internal fun describe(at: Long, threadName: String, error: Throwable): String {
        val stack = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString()
        return buildString {
            append("time=").append(at).append('\n')
            append("thread=").append(threadName).append('\n')
            append(stack)
        }
    }
}
