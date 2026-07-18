package com.freedomplay.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object TimeUtils {

    fun formatDuration(seconds: Long): String {
        if (seconds < 0) return "0:00"
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, secs)
        }
    }

    fun formatViewCount(views: Long): String {
        if (views < 0) return "0"
        return when {
            views >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", views / 1_000_000_000.0)
            views >= 1_000_000 -> String.format(Locale.US, "%.1fM", views / 1_000_000.0)
            views >= 1_000 -> String.format(Locale.US, "%.1fK", views / 1_000.0)
            else -> views.toString()
        }
    }

    fun formatDate(dateString: String?): String {
        if (dateString.isNullOrBlank()) return "Unknown"

        val parsers = listOf(
            "yyyy-MM-dd",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyyMMdd"
        )

        val date: Date? = parsers.firstNotNullOfOrNull { pattern ->
            try {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }.parse(dateString)
            } catch (_: Exception) { null }
        }

        if (date == null) {
            return try {
                val timestamp = dateString.toLong()
                formatRelativeTime(Date(timestamp * 1000))
            } catch (_: Exception) {
                dateString
            }
        }

        return formatRelativeTime(date)
    }

    fun formatTimestamp(timestampSeconds: Long): String {
        return formatRelativeTime(Date(timestampSeconds * 1000))
    }

    private fun formatRelativeTime(date: Date): String {
        val now = System.currentTimeMillis()
        val diff = now - date.time

        return when {
            diff < 0 -> "Just now"
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                if (hours == 1L) "1 hour ago" else "$hours hours ago"
            }
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                if (days == 1L) "Yesterday" else "$days days ago"
            }
            diff < TimeUnit.DAYS.toMillis(30) -> {
                val weeks = TimeUnit.MILLISECONDS.toDays(diff) / 7
                if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
            }
            diff < TimeUnit.DAYS.toMillis(365) -> {
                val months = TimeUnit.MILLISECONDS.toDays(diff) / 30
                if (months == 1L) "1 month ago" else "$months months ago"
            }
            else -> {
                val years = TimeUnit.MILLISECONDS.toDays(diff) / 365
                if (years == 1L) "1 year ago" else "$years years ago"
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 0) return "0 B"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatBitrate(bps: Long): String {
        if (bps < 0) return "0 bps"
        return when {
            bps < 1000 -> "$bps bps"
            bps < 1_000_000 -> String.format(Locale.US, "%.0f kbps", bps / 1000.0)
            else -> String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
        }
    }
}
