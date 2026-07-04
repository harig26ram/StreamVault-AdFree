package com.streamvault.player.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class CipherDecryptorTest {

    private val decryptor = CipherDecryptor()

    @Test
    fun testReverseOperation() {
        val result = decryptor.decryptDirect("hello", listOf(CipherDecryptor.CipherOp.Reverse))
        assertEquals("olleh", result)
    }

    @Test
    fun testSpliceDeleteOperation() {
        val result = decryptor.decryptDirect("hello", listOf(CipherDecryptor.CipherOp.SpliceDelete(0, 2)))
        assertEquals("llo", result)
    }

    @Test
    fun testSliceOperation() {
        val result = decryptor.decryptDirect("hello", listOf(CipherDecryptor.CipherOp.Slice(2)))
        assertEquals("llo", result)
    }

    @Test
    fun testSwapOperation() {
        val result = decryptor.decryptDirect("abcdef", listOf(CipherDecryptor.CipherOp.Swap(3)))
        assertEquals("dbcaef", result)
    }

    @Test
    fun testReverseThenSplice() {
        val result = decryptor.decryptDirect(
            "ABCDEFGH",
            listOf(
                CipherDecryptor.CipherOp.Reverse,
                CipherDecryptor.CipherOp.SpliceDelete(0, 2)
            )
        )
        assertEquals("FEDCBA", result)
    }

    @Test
    fun testSpliceThenReverse() {
        val result = decryptor.decryptDirect(
            "ABCDEFGH",
            listOf(
                CipherDecryptor.CipherOp.SpliceDelete(0, 3),
                CipherDecryptor.CipherOp.Reverse
            )
        )
        assertEquals("HGFED", result)
    }

    @Test
    fun testFullChain() {
        val result = decryptor.decryptDirect(
            "abcdefghijk",
            listOf(
                CipherDecryptor.CipherOp.Swap(5),
                CipherDecryptor.CipherOp.Reverse,
                CipherDecryptor.CipherOp.SpliceDelete(0, 2)
            )
        )
        assertEquals("ihgaedcbf", result)
    }

    @Test
    fun testParseNameLookupSimple() {
        val jsCode = """function(a){var b=a.split("");b.reverse();b.splice(0,2);var c=b.join("");return c}"""
        val ops = decryptor.parseOperations(jsCode, CipherDecryptor.OperationStrategy.NAME_LOOKUP)
        assertEquals(2, ops.size)
        assertEquals(CipherDecryptor.CipherOp.Reverse::class, ops[0]::class)
        assertEquals(CipherDecryptor.CipherOp.SpliceDelete::class, ops[1]::class)
    }

    @Test
    fun testParseNameLookupReverseSplice() {
        val jsCode = """function(a){a=a.split("");a.reverse();a.splice(0,3);a=a.join("");return a}"""
        val ops = decryptor.parseOperations(jsCode, CipherDecryptor.OperationStrategy.NAME_LOOKUP)
        assertEquals(2, ops.size)
    }

    @Test
    fun testParseAndExecute() {
        val jsCode = """function(a){var b=a.split("");b.reverse();b.splice(0,2);var c=b.join("");return c}"""
        val result = decryptor.decrypt("ABCDEFGH", jsCode, CipherDecryptor.OperationStrategy.NAME_LOOKUP)
        assertEquals("FEDCBA", result)
    }

    @Test
    fun testParseObjectLookup() {
        val jsCode = """function(a){a=a.split("");a=YT["reverse"](a);a=YT["splice"](a,2);a=a.join("");return a}"""
        val ops = decryptor.parseOperations(jsCode, CipherDecryptor.OperationStrategy.OBJECT_LOOKUP)
        assertEquals(2, ops.size)
        assertEquals(CipherDecryptor.CipherOp.Reverse::class, ops[0]::class)
    }

    @Test
    fun testFallbackReverseSplice2() {
        val ops = CipherDecryptor.tryKnownPatterns("b.reverse();b.splice(0,2);var c=b.join(\"\")")
        assertEquals(CipherDecryptor.CipherOp.Reverse::class, ops?.get(0)?.let { it::class })
    }

    @Test
    fun testEmptyInput() {
        val result = decryptor.decrypt("", "some js", CipherDecryptor.OperationStrategy.FALLBACK)
        assertEquals("", result)
    }

    @Test
    fun testSwapModuloLength() {
        val result = decryptor.decryptDirect("ab", listOf(CipherDecryptor.CipherOp.Swap(5)))
        assertEquals("ba", result)
    }

    @Test
    fun testComplexOperationSequence() {
        val result = decryptor.decryptDirect(
            "abc123xyz",
            listOf(
                CipherDecryptor.CipherOp.Reverse,
                CipherDecryptor.CipherOp.SpliceDelete(0, 1),
                CipherDecryptor.CipherOp.Slice(2),
                CipherDecryptor.CipherOp.Reverse
            )
        )
        assertEquals("abc123", result)
    }
}
