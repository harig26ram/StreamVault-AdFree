package com.streamvault.app.util

import java.util.concurrent.TimeUnit

object TimeUtils {

    fun formatDuration(durationSeconds: Int): String {
        if (durationSeconds < 0) return "0:00"
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    fun formatDurationLong(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs).toInt()
        return formatDuration(totalSeconds)
    }

    fun formatViewCount(viewCount: String): String {
        val count = viewCount.replace(",", "").toLongOrNull() ?: return viewCount
        return when {
            count >= 1_000_000_000 -> String.format("%.1fB views", count / 1_000_000_000.0)
            count >= 1_000_000 -> String.format("%.1fM views", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK views", count / 1_000.0)
            else -> "$count views"
        }
    }

    fun formatSubscriberCount(count: String): String {
        val num = count.replace(",", "").toLongOrNull() ?: return count
        return when {
            num >= 1_000_000_000 -> String.format("%.1fB subscribers", num / 1_000_000_000.0)
            num >= 1_000_000 -> String.format("%.1fM subscribers", num / 1_000_000.0)
            num >= 1_000 -> String.format("%.1fK subscribers", num / 1_000.0)
            else -> "$num subscribers"
        }
    }

    fun timeAgo(publishedText: String): String {
        val lower = publishedText.lowercase().trim()
        val numbers = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: return publishedText
        return when {
            lower.contains("second") || lower.contains("sec") -> "${numbers}s ago"
            lower.contains("minute") || lower.contains("min") -> "${numbers}m ago"
            lower.contains("hour") || lower.contains("hr") -> "${numbers}h ago"
            lower.contains("day") -> "${numbers}d ago"
            lower.contains("week") || lower.contains("wk") -> "${numbers}w ago"
            lower.contains("month") -> "${numbers}mo ago"
            lower.contains("year") || lower.contains("yr") -> "${numbers}y ago"
            else -> publishedText
        }
    }

    fun parseDurationToSeconds(duration: String): Int {
        val parts = duration.split(":").map { it.toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }
}
