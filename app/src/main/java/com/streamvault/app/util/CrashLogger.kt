package com.streamvault.app.util

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

/**
 * Crash-safe logger that captures:
 * 1. Uncaught exception stacktraces (written to file immediately on crash)
 * 2. Logcat output for key tags (dumped on demand or on crash)
 *
 * Wire into StreamVaultApplication.onCreate():
 *   CrashLogger.install(this)
 *
 * On crash, the exception is written to getExternalFilesDir/logs/crash_<timestamp>.txt
 * The user can export all crash + logcat logs via DebugLogExporter or this class.
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LOGS_DIR = "logs"
    private const val CRASH_PREFIX = "crash_"
    private const val LOGCAT_DUMP_PREFIX = "logcat_"

    private val keyTags = listOf(
        "PoTokenProvider",
        "VideoRepo",
        "PlayerVM",
        "HomeVM",
        "SearchVM",
        "StreamVault",
        "FreedomPlay",
        "PlaybackService",
        "JsNTransformer",
        "StreamUrlExtractor",
        "MediaCodec",
        "AudioTrack",
        "AndroidRuntime",
        "System.err",
        "DEBUG",
        "crash"
    )

    private val recentLogs = CopyOnWriteArrayList<String>()
    private val maxRecentLogs = 500
    private var installed = false
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Install the crash handler. Call from Application.onCreate().
     */
    fun install(context: Context) {
        if (installed) return
        installed = true

        // Save the previous handler so we still chain to it (e.g. Firebase Crashlytics)
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = writeCrashToFile(context, thread, throwable)
                val logcatFile = dumpLogcat(context)
                Log.e(TAG, "Crash saved: $crashFile")
                Log.e(TAG, "Logcat dump: $logcatFile")
            } catch (_: Exception) {
                // Swallow — we're already crashing, can't do much more
            }

            // Chain to previous handler (e.g. system crash dialog)
            previousHandler?.uncaughtException(thread, throwable)
        }

        Log.d(TAG, "CrashLogger installed")
    }

    /**
     * Add a log entry to the in-memory ring buffer.
     * Call this from any place you want to capture structured logs.
     * Also automatically called by the logcat-based capture.
     */
    fun log(tag: String, message: String, level: String = "I") {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$timestamp $level/$tag: $message"
        recentLogs.add(line)
        // Trim if too large
        while (recentLogs.size > maxRecentLogs) {
            recentLogs.removeAt(0)
        }
    }

    /**
     * Write crash info + stacktrace to file. Returns the file path.
     */
    private fun writeCrashToFile(context: Context, thread: Thread, throwable: Throwable): File {
        val dir = File(context.getExternalFilesDir(null), LOGS_DIR)
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "${CRASH_PREFIX}${timestamp}.txt")

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("=== FreedomPlay Crash Report ===")
        pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        pw.println("Thread: ${thread.name}")
        pw.println("Exception: ${throwable.javaClass.name}: ${throwable.message}")
        pw.println()
        pw.println("--- Stacktrace ---")
        throwable.printStackTrace(pw)
        pw.println()

        // If there's a cause, print that too
        throwable.cause?.let { cause ->
            pw.println("--- Caused by ---")
            cause.printStackTrace(pw)
            pw.println()
        }

        // Append recent in-memory logs
        pw.println("--- Recent logs (${recentLogs.size} entries) ---")
        recentLogs.forEach { pw.println(it) }
        pw.println()

        // Dump current logcat for our tags
        pw.println("--- Logcat (our tags, last 500 lines) ---")
        try {
            val pid = android.os.Process.myPid()
            val tagFilter = keyTags.joinToString("|") { Pattern.quote(it) }
            val cmd = "logcat -b all -v threadtime -d --pid=$pid *:S $tagFilter"
            val process = Runtime.getRuntime().exec(cmd)
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var count = 0
                var line: String?
                while (reader.readLine().also { line = it } != null && count < 500) {
                    pw.println(line)
                    count++
                }
            }
            process.waitFor()
        } catch (_: Exception) {
            pw.println("(logcat dump failed)")
        }

        pw.flush()
        file.writeText(sw.toString())
        return file
    }

    /**
     * Dump logcat for key tags to a file. Returns the file path.
     */
    fun dumpLogcat(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), LOGS_DIR)
        if (!dir.exists()) dir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "${LOGCAT_DUMP_PREFIX}${timestamp}.txt")

        FileWriter(file).use { writer ->
            writer.appendLine("=== FreedomPlay Logcat Dump ===")
            writer.appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            writer.appendLine()

            // Dump with key tags
            try {
                val pid = android.os.Process.myPid()
                val tagFilter = keyTags.joinToString("|") { Pattern.quote(it) }
                val cmd = "logcat -b all -v threadtime -d --pid=$pid *:S $tagFilter"
                val process = Runtime.getRuntime().exec(cmd)
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        writer.appendLine(line)
                    }
                }
                process.waitFor()
            } catch (_: Exception) {
                writer.appendLine("(logcat dump failed)")
            }

            // Also dump in-memory ring buffer
            writer.appendLine()
            writer.appendLine("--- In-memory ring buffer (${recentLogs.size} entries) ---")
            recentLogs.forEach { writer.appendLine(it) }
        }

        return file
    }

    /**
     * Get all crash + logcat files for sharing.
     */
    fun getAllLogFiles(context: Context): List<File> {
        val dir = File(context.getExternalFilesDir(null), LOGS_DIR) ?: return emptyList()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()?.filter {
            it.name.startsWith(CRASH_PREFIX) || it.name.startsWith(LOGCAT_DUMP_PREFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Delete old log files (keep last 10).
     */
    fun pruneOldLogs(context: Context) {
        val dir = File(context.getExternalFilesDir(null), LOGS_DIR) ?: return
        if (!dir.exists()) return
        val files = dir.listFiles()
            ?.filter { it.name.startsWith(CRASH_PREFIX) || it.name.startsWith(LOGCAT_DUMP_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(10).forEach { it.delete() }
    }

    /**
     * Clear all logs.
     */
    fun clearAll(context: Context) {
        val dir = File(context.getExternalFilesDir(null), LOGS_DIR) ?: return
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
        recentLogs.clear()
    }
}
