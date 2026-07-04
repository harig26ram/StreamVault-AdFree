package com.streamvault.player.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItagInfoTest {

    @Test
    fun testGetKnownItag18() {
        val itag = ItagInfo.get(18)
        assertNotNull(itag)
        assertEquals(18, itag?.itag)
        assertEquals("mp4", itag?.container)
        assertEquals("360p", itag?.resolution)
        assertEquals("h264", itag?.videoCodec)
        assertEquals("aac", itag?.audioCodec)
        assertEquals(96, itag?.audioBitrate)
        assertEquals(2, itag?.channels)
        assertEquals(false, itag?.isDash)
    }

    @Test
    fun testGetKnownItag22() {
        val itag = ItagInfo.get(22)
        assertNotNull(itag)
        assertEquals(22, itag?.itag)
        assertEquals("720p", itag?.resolution)
    }

    @Test
    fun testGetKnownItag137() {
        val itag = ItagInfo.get(137)
        assertNotNull(itag)
        assertEquals(137, itag?.itag)
        assertEquals("1080p", itag?.resolution)
        assertEquals(true, itag?.isDash)
        assertNull(itag?.audioCodec)
    }

    @Test
    fun testGetKnownItag140() {
        val itag = ItagInfo.get(140)
        assertNotNull(itag)
        assertEquals(140, itag?.itag)
        assertEquals("aac", itag?.audioCodec)
        assertEquals(128, itag?.audioBitrate)
        assertEquals(2, itag?.channels)
    }

    @Test
    fun testGetKnownItag251() {
        val itag = ItagInfo.get(251)
        assertNotNull(itag)
        assertEquals(251, itag?.itag)
        assertEquals("opus", itag?.audioCodec)
        assertEquals(160, itag?.audioBitrate)
    }

    @Test
    fun testGetUnknownItag() {
        val itag = ItagInfo.get(99999)
        assertNull(itag)
    }

    @Test
    fun testGetVideoItagsSorted() {
        val videoItags = ItagInfo.getVideoItagsSorted()
        assertTrue(videoItags.isNotEmpty())
        val resolutions = videoItags.mapNotNull { it.resolution }
        assertTrue(resolutions.isNotEmpty())
        val firstRes = resolutions.first()
        val lastRes = resolutions.last()
        assertTrue(
            "First ($firstRes) should be >= last ($lastRes) in resolution",
            parseNum(firstRes) >= parseNum(lastRes)
        )
    }

    @Test
    fun testGetAudioItagsSorted() {
        val audioItags = ItagInfo.getAudioItagsSorted()
        assertTrue(audioItags.isNotEmpty())
        val bitrates = audioItags.mapNotNull { it.audioBitrate }
        assertTrue(bitrates.isNotEmpty())
        assertTrue("Audio bitrates should be sorted descending", bitrates[0] >= bitrates[bitrates.size - 1])
    }

    @Test
    fun testHdrItag() {
        val itag = ItagInfo.get(330)
        assertNotNull(itag)
    }

    @Test
    fun test3dItag() {
        val itag = ItagInfo.get(82)
        assertNotNull(itag)
        assertEquals(true, itag?.is3d)
    }

    @Test
    fun testAv1Itag() {
        val itag = ItagInfo.get(394)
        assertNotNull(itag)
        assertEquals("av1", itag?.videoCodec)
        assertEquals("144p60", itag?.resolution)
    }

    @Test
    fun testItagCount() {
        assertTrue(ItagInfo.getVideoItagsSorted().size >= 30)
        assertTrue(ItagInfo.getAudioItagsSorted().size >= 10)
    }

    private fun parseNum(res: String): Int {
        return res.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
    }
}
