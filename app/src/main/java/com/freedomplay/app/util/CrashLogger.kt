package com.freedomplay.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val DEBUG_LOG_FILE = "debug_logs.txt"
    private const val CRASH_LOG_FILE = "crash_logs.txt"
    private const val MAX_LOG_SIZE = 512 * 1024L // 512KB per file
    private const val MAX_LINES = 500

    private var appTag = "FreedomPlay"

    fun init(tag: String) {
        appTag = tag
    }

    fun d(message: String, tag: String = appTag) {
        Log.d(tag, message)
        appendDebugLog("D/$tag: $message")
    }

    fun e(message: String, throwable: Throwable? = null, tag: String = appTag) {
        Log.e(tag, message, throwable)
        val entry = buildString {
            append("E/$tag: $message")
            throwable?.let {
                append("\n  ${it.javaClass.simpleName}: ${it.message}")
                val sw = StringWriter()
                it.printStackTrace(PrintWriter(sw))
                append("\n  ${sw.toString().lines().take(10).joinToString("\n  ")}")
            }
        }
        appendDebugLog(entry)
    }

    fun w(message: String, tag: String = appTag) {
        Log.w(tag, message)
        appendDebugLog("W/$tag: $message")
    }

    fun i(message: String, tag: String = appTag) {
        Log.i(tag, message)
        appendDebugLog("I/$tag: $message")
    }

    fun http(method: String, url: String, code: Int, timeMs: Long) {
        val entry = "HTTP $method $url -> $code (${timeMs}ms)"
        Log.d(appTag, entry)
        appendDebugLog(entry)
    }

    fun recordCrash(throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val entry = "\n[$timestamp]\n${sw.toString()}\n${"=".repeat(60)}\n"

        try {
            val file = getCrashFile(null)
            file.appendText(entry)
            trimFile(file, MAX_LINES)
        } catch (_: Exception) { }
    }

    fun getDebugLogs(context: Context): String {
        return try {
            getFileContent(context, DEBUG_LOG_FILE)
        } catch (_: Exception) { "" }
    }

    fun getCrashLogs(context: Context): String {
        return try {
            getFileContent(context, CRASH_LOG_FILE)
        } catch (_: Exception) { "" }
    }

    fun clearLogs(context: Context) {
        try {
            File(context.filesDir, DEBUG_LOG_FILE).delete()
            File(context.filesDir, CRASH_LOG_FILE).delete()
        } catch (_: Exception) { }
    }

    private fun appendDebugLog(entry: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val line = "[$timestamp] $entry\n"
            val file = getDebugFile(null)
            file.appendText(line)
            trimFile(file, MAX_LINES)
        } catch (_: Exception) { }
    }

    private fun getDebugFile(context: Context?): File {
        val dir = context?.filesDir ?: File("/data/data/com.freedomplay.app/files")
        return File(dir, DEBUG_LOG_FILE)
    }

    private fun getCrashFile(context: Context?): File {
        val dir = context?.filesDir ?: File("/data/data/com.freedomplay.app/files")
        return File(dir, CRASH_LOG_FILE)
    }

    private fun getFileContent(context: Context, fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return ""
        return file.readText().takeLast(MAX_LOG_SIZE.toInt())
    }

    private fun trimFile(file: File, maxLines: Int) {
        if (!file.exists()) return
        val lines = file.readLines()
        if (lines.size > maxLines) {
            file.writeText(lines.takeLast(maxLines).joinToString("\n"))
        }
    }
}
