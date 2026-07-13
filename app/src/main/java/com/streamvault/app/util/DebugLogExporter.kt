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

    fun export(context: Context): Uri? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "logs")
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
}
