package com.streamvault.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    // --- extractVideoId ---

    @Test
    fun `extractVideoId from standard watch URL`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from watch URL with extra params`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"))
    }

    @Test
    fun `extractVideoId from youtu_be short URL`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from embed URL`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from v URL`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://www.youtube.com/v/dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from shorts URL`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from bare video ID`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from invalid URL returns null`() {
        assertNull(UrlUtils.extractVideoId("https://example.com/video"))
    }

    @Test
    fun `extractVideoId from empty string returns null`() {
        assertNull(UrlUtils.extractVideoId(""))
    }

    @Test
    fun `extractVideoId with underscores and dashes`() {
        assertEquals("ab_cde-fghi", UrlUtils.extractVideoId("https://youtube.com/watch?v=ab_cde-fghi"))
    }

    // --- extractChannelId ---

    @Test
    fun `extractChannelId from channel URL`() {
        assertEquals("UC1234567890", UrlUtils.extractChannelId("https://www.youtube.com/channel/UC1234567890"))
    }

    @Test
    fun `extractChannelId from handle URL`() {
        assertEquals("username", UrlUtils.extractChannelId("https://www.youtube.com/@username"))
    }

    @Test
    fun `extractChannelId from c URL`() {
        assertEquals("SomeChannel", UrlUtils.extractChannelId("https://www.youtube.com/c/SomeChannel"))
    }

    @Test
    fun `extractChannelId from user URL`() {
        assertEquals("SomeUser", UrlUtils.extractChannelId("https://www.youtube.com/user/SomeUser"))
    }

    @Test
    fun `extractChannelId from non-channel URL returns null`() {
        assertNull(UrlUtils.extractChannelId("https://www.youtube.com/watch?v=abc"))
    }

    @Test
    fun `extractChannelId from empty string returns null`() {
        assertNull(UrlUtils.extractChannelId(""))
    }

    // --- isValidUrl ---

    @Test
    fun `isValidUrl with https`() {
        assertTrue(UrlUtils.isValidUrl("https://www.youtube.com"))
    }

    @Test
    fun `isValidUrl with http`() {
        assertTrue(UrlUtils.isValidUrl("http://www.youtube.com"))
    }

    @Test
    fun `isValidUrl without scheme is invalid`() {
        assertFalse(UrlUtils.isValidUrl("www.youtube.com"))
    }

    @Test
    fun `isValidUrl random string is invalid`() {
        assertFalse(UrlUtils.isValidUrl("not-a-url"))
    }

    @Test
    fun `isValidUrl empty string is invalid`() {
        assertFalse(UrlUtils.isValidUrl(""))
    }

    // --- isYouTubeUrl ---

    @Test
    fun `isYouTubeUrl with youtube com`() {
        assertTrue(UrlUtils.isYouTubeUrl("https://www.youtube.com/watch?v=abc"))
    }

    @Test
    fun `isYouTubeUrl with youtu be`() {
        assertTrue(UrlUtils.isYouTubeUrl("https://youtu.be/abc"))
    }

    @Test
    fun `isYouTubeUrl with non-youtube`() {
        assertFalse(UrlUtils.isYouTubeUrl("https://vimeo.com/123"))
    }

    // --- normalizeUrl ---

    @Test
    fun `normalizeUrl already has https`() {
        assertEquals("https://youtube.com", UrlUtils.normalizeUrl("https://youtube.com"))
    }

    @Test
    fun `normalizeUrl has www prefix`() {
        assertEquals("https://www.youtube.com", UrlUtils.normalizeUrl("www.youtube.com"))
    }

    @Test
    fun `normalizeUrl bare domain`() {
        assertEquals("https://youtube.com", UrlUtils.normalizeUrl("youtube.com"))
    }

    @Test
    fun `normalizeUrl trims whitespace`() {
        assertEquals("https://youtube.com", UrlUtils.normalizeUrl("  https://youtube.com  "))
    }

    // --- getYouTubeThumbnailUrl ---

    @Test
    fun `getYouTubeThumbnailUrl returns correct format`() {
        assertEquals(
            "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            UrlUtils.getYouTubeThumbnailUrl("dQw4w9WgXcQ")
        )
    }

    // --- buildYouTubeWatchUrl ---

    @Test
    fun `buildYouTubeWatchUrl returns correct format`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.buildYouTubeWatchUrl("dQw4w9WgXcQ")
        )
    }

    // --- buildYouTubeChannelUrl ---

    @Test
    fun `buildYouTubeChannelUrl returns correct format`() {
        assertEquals(
            "https://www.youtube.com/channel/UC1234567890",
            UrlUtils.buildYouTubeChannelUrl("UC1234567890")
        )
    }
}
