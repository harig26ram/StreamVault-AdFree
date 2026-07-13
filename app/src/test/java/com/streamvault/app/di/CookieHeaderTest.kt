package com.streamvault.app.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieHeaderTest {

    @Test
    fun `computeSapiSidHash produces correct format`() {
        val sapisid = "test_sapisid_value"
        val result = NetworkModule.computeSapiSidHash(sapisid)
        assertTrue("Should start with SAPISIDHASH", result.startsWith("SAPISIDHASH "))
        assertTrue("Should contain colon separator", result.contains(":"))
        val parts = result.removePrefix("SAPISIDHASH ").split(":")
        assertEquals("Should have timestamp:hash format", 2, parts.size)
        val timestamp = parts[0].toLongOrNull()
        assertTrue("Timestamp should be numeric", timestamp != null && timestamp > 0)
        assertEquals("Hash should be 40 hex chars (SHA-1)", 40, parts[1].length)
        assertTrue("Hash should be lowercase hex", parts[1].matches(Regex("[0-9a-f]{40}")))
    }

    @Test
    fun `computeSapiSidHash is consistent for same input`() {
        val sapisid = "consistent_test_value"
        val result1 = NetworkModule.computeSapiSidHash(sapisid)
        val result2 = NetworkModule.computeSapiSidHash(sapisid)
        // Timestamps may differ by 1s if test spans a second boundary,
        // so we compare the hash portion
        val hash1 = result1.split(":")[1]
        val hash2 = result2.split(":")[1]
        assertEquals("Hash should be deterministic for same input", hash1, hash2)
    }

    @Test
    fun `computeSapiSidHash uses correct input format`() {
        // Verify the hash matches what we'd compute manually
        val sapisid = "abc123"
        val origin = "https://www.youtube.com"
        val result = NetworkModule.computeSapiSidHash(sapisid, origin)
        val timestamp = result.split(":")[0].removePrefix("SAPISIDHASH ").toLong()
        val expectedInput = "$timestamp.$sapisid.$origin"
        val expectedHash = java.security.MessageDigest.getInstance("SHA-1")
            .digest(expectedInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals("SAPISIDHASH $timestamp:$expectedHash", result)
    }

    @Test
    fun `computeSapiSidHash with custom origin`() {
        val sapisid = "test"
        val origin = "https://custom.origin.com"
        val result = NetworkModule.computeSapiSidHash(sapisid, origin)
        assertTrue(result.startsWith("SAPISIDHASH "))
        assertEquals(40, result.split(":")[1].length)
    }
}
