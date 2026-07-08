package com.streamvault.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // --- formatDurationLong ---

    @Test
    fun `formatDurationLong zero`() {
        assertEquals("0:00", TimeUtils.formatDurationLong(0L))
    }

    @Test
    fun `formatDurationLong one hour`() {
        assertEquals("1:00:00", TimeUtils.formatDurationLong(3_600_000L))
    }

    @Test
    fun `formatDurationLong thirty seconds`() {
        assertEquals("0:30", TimeUtils.formatDurationLong(30_000L))
    }

    // --- formatViewCount ---

    @Test
    fun `formatViewCount small number`() {
        assertEquals("42 views", TimeUtils.formatViewCount("42"))
    }

    @Test
    fun `formatViewCount exactly one thousand`() {
        assertEquals("1.0K views", TimeUtils.formatViewCount("1000"))
    }

    @Test
    fun `formatViewCount thousands`() {
        assertEquals("12.3K views", TimeUtils.formatViewCount("12345"))
    }

    @Test
    fun `formatViewCount millions`() {
        assertEquals("1.2M views", TimeUtils.formatViewCount("1234567"))
    }

    @Test
    fun `formatViewCount billions`() {
        assertEquals("1.5B views", TimeUtils.formatViewCount("1500000000"))
    }

    @Test
    fun `formatViewCount with commas`() {
        assertEquals("1.2M views", TimeUtils.formatViewCount("1,234,567"))
    }

    @Test
    fun `formatViewCount non-numeric returns original`() {
        assertEquals("N/A", TimeUtils.formatViewCount("N/A"))
    }

    @Test
    fun `formatViewCount empty returns original`() {
        assertEquals("", TimeUtils.formatViewCount(""))
    }

    @Test
    fun `formatViewCount exactly one million`() {
        assertEquals("1.0M views", TimeUtils.formatViewCount("1000000"))
    }

    // --- formatSubscriberCount ---

    @Test
    fun `formatSubscriberCount small number`() {
        assertEquals("500 subscribers", TimeUtils.formatSubscriberCount("500"))
    }

    @Test
    fun `formatSubscriberCount thousands`() {
        assertEquals("1.5K subscribers", TimeUtils.formatSubscriberCount("1500"))
    }

    @Test
    fun `formatSubscriberCount millions`() {
        assertEquals("2.3M subscribers", TimeUtils.formatSubscriberCount("2300000"))
    }

    // --- timeAgo ---

    @Test
    fun `timeAgo seconds`() {
        assertEquals("5s ago", TimeUtils.timeAgo("5 seconds ago"))
    }

    @Test
    fun `timeAgo minutes`() {
        assertEquals("3m ago", TimeUtils.timeAgo("3 minutes ago"))
    }

    @Test
    fun `timeAgo hours`() {
        assertEquals("2h ago", TimeUtils.timeAgo("2 hours ago"))
    }

    @Test
    fun `timeAgo days`() {
        assertEquals("7d ago", TimeUtils.timeAgo("7 days ago"))
    }

    @Test
    fun `timeAgo weeks`() {
        assertEquals("2w ago", TimeUtils.timeAgo("2 weeks ago"))
    }

    @Test
    fun `timeAgo months`() {
        assertEquals("6mo ago", TimeUtils.timeAgo("6 months ago"))
    }

    @Test
    fun `timeAgo years`() {
        assertEquals("1y ago", TimeUtils.timeAgo("1 year ago"))
    }

    @Test
    fun `timeAgo singular form`() {
        assertEquals("1d ago", TimeUtils.timeAgo("1 day ago"))
    }

    @Test
    fun `timeAgo no number returns original`() {
        assertEquals("just now", TimeUtils.timeAgo("just now"))
    }

    @Test
    fun `timeAgo abbreviated forms`() {
        assertEquals("5s ago", TimeUtils.timeAgo("5 sec ago"))
        assertEquals("3m ago", TimeUtils.timeAgo("3 min ago"))
        assertEquals("2w ago", TimeUtils.timeAgo("2 wk ago"))
        assertEquals("1y ago", TimeUtils.timeAgo("1 yr ago"))
    }

    // --- parseDurationToSeconds ---

    @Test
    fun `parseDurationToSeconds mm_ss`() {
        assertEquals(90, TimeUtils.parseDurationToSeconds("1:30"))
    }

    @Test
    fun `parseDurationToSeconds hh_mm_ss`() {
        assertEquals(3661, TimeUtils.parseDurationToSeconds("1:01:01"))
    }

    @Test
    fun `parseDurationToSeconds just seconds`() {
        assertEquals(45, TimeUtils.parseDurationToSeconds("45"))
    }

    @Test
    fun `parseDurationToSeconds zero`() {
        assertEquals(0, TimeUtils.parseDurationToSeconds("0:00"))
    }

    @Test
    fun `parseDurationToSeconds large hours`() {
        assertEquals(36000, TimeUtils.parseDurationToSeconds("10:00:00"))
    }
}
