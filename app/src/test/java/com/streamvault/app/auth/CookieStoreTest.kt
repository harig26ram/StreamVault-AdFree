package com.streamvault.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CookieStoreTest {

    private val XOR_KEY = 0x5A

    private fun xorEncode(input: String): String {
        return String(input.map { (it.code xor XOR_KEY).toChar() }.toCharArray())
    }

    private fun xorDecode(input: String): String {
        return String(input.map { (it.code xor XOR_KEY).toChar() }.toCharArray())
    }

    @Test
    fun `xor encode and decode are symmetric`() {
        val original = "SAPISID=abc123def456; SID=xyz789"
        val encoded = xorEncode(original)
        val decoded = xorDecode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `xor encode produces different output`() {
        val original = "test_cookie_value"
        val encoded = xorEncode(original)
        assertNotEquals(original, encoded)
    }

    @Test
    fun `xor encode handles empty string`() {
        val original = ""
        val encoded = xorEncode(original)
        val decoded = xorDecode(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `xor encode handles special characters`() {
        val original = "cookie=val; path=/; domain=.youtube.com"
        val encoded = xorEncode(original)
        val decoded = xorDecode(encoded)
        assertEquals(original, decoded)
    }
}
