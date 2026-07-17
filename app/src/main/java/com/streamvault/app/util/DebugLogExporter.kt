package com.streamvault.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader

object DebugLogExporter {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val FILENAME = "freedomplay_debug_log.txt"
    private const val LOGS_DIR = "logs"

    fun export(context: Context): Uri? {
        return try {
            val dir = File(context.getExternalFilesDir(null), LOGS_DIR)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, FILENAME)
            val pid = android.os.Process.myPid()
            val cmd = "logcat -b all -v threadtime -d --pid=$pid -t 2000"
            val process = Runtime.getRuntime().exec(cmd)
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                FileWriter(file).use { writer ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        writer.appendLine(line)
                    }
                }
            }
            process.waitFor()
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        } catch (e: Exception) {
            null
        }
    }

    fun buildShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "FreedomPlay debug log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Share all crash logs + logcat dumps via a single Intent.
     * Attaches up to 5 most recent files (crash_*.txt + logcat_*.txt).
     */
    fun shareAllLogs(context: Context): Intent? {
        val files = CrashLogger.getAllLogFiles(context).take(5)
        if (files.isEmpty()) return null

        val uris = files.mapNotNull { file ->
            try {
                FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
            } catch (_: Exception) {
                null
            }
        }
        if (uris.isEmpty()) return null

        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, "FreedomPlay crash log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                putExtra(Intent.EXTRA_SUBJECT, "FreedomPlay crash logs (${uris.size} files)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
