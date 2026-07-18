package com.freedomplay.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    // --- formatDuration ---

    @Test
    fun `formatDuration zero seconds`() {
        assertEquals("0:00", TimeUtils.formatDuration(0))
    }

    @Test
    fun `formatDuration seconds only`() {
        assertEquals("0:45", TimeUtils.formatDuration(45))
    }

    @Test
    fun `formatDuration minutes and seconds`() {
        assertEquals("1:30", TimeUtils.formatDuration(90))
    }

    @Test
    fun `formatDuration exactly one minute`() {
        assertEquals("1:00", TimeUtils.formatDuration(60))
    }

    @Test
    fun `formatDuration hours minutes seconds`() {
        assertEquals("1:05:30", TimeUtils.formatDuration(3930))
    }

    @Test
    fun `formatDuration exactly one hour`() {
        assertEquals("1:00:00", TimeUtils.formatDuration(3600))
    }

    @Test
    fun `formatDuration large value`() {
        assertEquals("10:00:00", TimeUtils.formatDuration(36000))
    }

    @Test
    fun `formatDuration negative returns 0_00`() {
        assertEquals("0:00", TimeUtils.formatDuration(-5))
    }

    @Test
    fun `formatDuration single digit seconds padded`() {
        assertEquals("0:09", TimeUtils.formatDuration(9))
    }

    @Test
    fun `formatDuration minutes not padded`() {
        assertEquals("12:34", TimeUtils.formatDuration(754))
    }

    // --- formatViewCount ---

    @Test
    fun `formatViewCount small number`() {
        assertEquals("42", TimeUtils.formatViewCount(42))
    }

    @Test
    fun `formatViewCount exactly one thousand`() {
        assertEquals("1.0K", TimeUtils.formatViewCount(1000))
    }

    @Test
    fun `formatViewCount thousands`() {
        assertEquals("12.3K", TimeUtils.formatViewCount(12345))
    }

    @Test
    fun `formatViewCount millions`() {
        assertEquals("1.2M", TimeUtils.formatViewCount(1234567))
    }

    @Test
    fun `formatViewCount billions`() {
        assertEquals("1.5B", TimeUtils.formatViewCount(1500000000))
    }

    @Test
    fun `formatViewCount negative returns 0`() {
        assertEquals("0", TimeUtils.formatViewCount(-1))
    }

    @Test
    fun `formatViewCount exactly one million`() {
        assertEquals("1.0M", TimeUtils.formatViewCount(1000000))
    }

    // --- formatFileSize ---

    @Test
    fun `formatFileSize bytes`() {
        assertEquals("500 B", TimeUtils.formatFileSize(500))
    }

    @Test
    fun `formatFileSize kilobytes`() {
        assertEquals("1.5 KB", TimeUtils.formatFileSize(1536))
    }

    @Test
    fun `formatFileSize megabytes`() {
        assertEquals("2.0 MB", TimeUtils.formatFileSize(2097152))
    }

    @Test
    fun `formatFileSize gigabytes`() {
        assertEquals("1.50 GB", TimeUtils.formatFileSize(1610612736))
    }

    @Test
    fun `formatFileSize zero`() {
        assertEquals("0 B", TimeUtils.formatFileSize(0))
    }

    @Test
    fun `formatFileSize negative returns 0 B`() {
        assertEquals("0 B", TimeUtils.formatFileSize(-1))
    }

    @Test
    fun `formatFileSize exactly 1 KB`() {
        assertEquals("1.0 KB", TimeUtils.formatFileSize(1024))
    }

    @Test
    fun `formatFileSize exactly 1 MB`() {
        assertEquals("1.0 MB", TimeUtils.formatFileSize(1048576))
    }

    // --- formatBitrate ---

    @Test
    fun `formatBitrate bps`() {
        assertEquals("500 bps", TimeUtils.formatBitrate(500))
    }

    @Test
    fun `formatBitrate kbps`() {
        assertEquals("128 kbps", TimeUtils.formatBitrate(128000))
    }

    @Test
    fun `formatBitrate Mbps`() {
        assertEquals("3.5 Mbps", TimeUtils.formatBitrate(3500000))
    }

    @Test
    fun `formatBitrate zero`() {
        assertEquals("0 bps", TimeUtils.formatBitrate(0))
    }

    @Test
    fun `formatBitrate negative returns 0 bps`() {
        assertEquals("0 bps", TimeUtils.formatBitrate(-1))
    }

    // --- formatDate ---

    @Test
    fun `formatDate null returns Unknown`() {
        assertEquals("Unknown", TimeUtils.formatDate(null))
    }

    @Test
    fun `formatDate blank returns Unknown`() {
        assertEquals("Unknown", TimeUtils.formatDate(""))
    }

    @Test
    fun `formatDate valid yyyy-MM-dd`() {
        val result = TimeUtils.formatDate("2026-07-18")
        assert(result.contains("ago") || result.contains("Just now") || result.contains("Yesterday"))
    }

    @Test
    fun `formatDate timestamp seconds`() {
        val now = System.currentTimeMillis() / 1000
        val result = TimeUtils.formatTimestamp(now - 3600)
        assert(result.contains("hour"))
    }
}
