package com.freedomplay.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlUtilsTest {

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

    @Test
    fun `extractVideoId from youtu_be with extra path`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=30"))
    }

    @Test
    fun `extractVideoId from m youtube`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId from music youtube`() {
        assertEquals("dQw4w9WgXcQ", UrlUtils.extractVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `extractVideoId rejects ID with wrong length`() {
        assertNull(UrlUtils.extractVideoId("https://youtube.com/watch?v=short"))
    }

    @Test
    fun `getVideoUrl returns correct format`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.getVideoUrl("dQw4w9WgXcQ")
        )
    }

    @Test
    fun `getChannelUrl returns correct format`() {
        assertEquals(
            "https://www.youtube.com/channel/UC1234567890",
            UrlUtils.getChannelUrl("UC1234567890")
        )
    }

    @Test
    fun `getThumbnailUrl default quality`() {
        assertEquals(
            "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
            UrlUtils.getThumbnailUrl("dQw4w9WgXcQ")
        )
    }

    @Test
    fun `getThumbnailUrl high quality`() {
        assertEquals(
            "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            UrlUtils.getThumbnailUrl("dQw4w9WgXcQ", "high")
        )
    }

    @Test
    fun `getThumbnailUrl maxres quality`() {
        assertEquals(
            "https://img.youtube.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            UrlUtils.getThumbnailUrl("dQw4w9WgXcQ", "maxres")
        )
    }

    @Test
    fun `getThumbnailUrl unknown quality falls back to medium`() {
        assertEquals(
            "https://img.youtube.com/vi/dQw4w9WgXcQ/mqdefault.jpg",
            UrlUtils.getThumbnailUrl("dQw4w9WgXcQ", "unknown")
        )
    }
}
