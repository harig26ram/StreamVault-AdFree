package com.streamvault.player.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NParamDecryptorTest {

    private val decryptor = NParamDecryptor()

    @Test
    fun testDirectSwapPairTransform() {
        val op = NParamDecryptor.NTransformOp(listOf(Pair(0, 3), Pair(1, 2)))
        val result = decryptor.decryptNParamDirect("abcd", op)
        assertEquals("dcba", result)
    }

    @Test
    fun testNoTransformationOnEmpty() {
        val result = decryptor.decryptNParam("", "")
        assertEquals("", result)
    }

    @Test
    fun testNoTransformationNoCode() {
        val result = decryptor.decryptNParam("abc123", "")
        assertEquals("abc123", result)
    }

    @Test
    fun testParseTransformFromJsCode() {
        val jsCode = """function(a){var b=a.split("");var c=[0,3,1,2];for(var d=0;d<c.length;d+=2){var e=b[c[d]];b[c[d]]=b[c[d+1]];b[c[d+1]]=e}return b.join("")}"""
        val op = decryptor.parseNTransformCode(jsCode)
        assertNotNull(op)
        val result = decryptor.decryptNParamDirect("abcd", op!!)
        assertEquals("bcda", result)
    }

    @Test
    fun testParseAndDecryptNParam() {
        val jsCode = """function(a){var b=a.split("");for(var c=0;c<6;c+=2){var d=b[0];b[0]=b[c+3%b.length];b[c+3]=d}return b.join("")}"""
        val result = decryptor.decryptNParam("abcdef", jsCode)
        assertEquals(6, result.length)
    }

    @Test
    fun testKnownAlgorithmFallback() {
        val knownOp = NParamDecryptor.KNOWN_ALGORITHMS[0]
        val result = decryptor.decryptNParamDirect("abcdefghijklmnopqrstuvwxyz", knownOp)
        assertEquals(26, result.length)
    }

    @Test
    fun testMultipleSwapPairs() {
        val op = NParamDecryptor.NTransformOp(listOf(
            Pair(0, 5), Pair(1, 4), Pair(2, 3)
        ))
        val result = decryptor.decryptNParamDirect("abcdef", op)
        assertEquals("fedcba", result)
    }

    @Test
    fun testSwapPairsModuloWrap() {
        val op = NParamDecryptor.NTransformOp(listOf(Pair(0, 10)))
        val result = decryptor.decryptNParamDirect("abc", op)
        assertEquals("bac", result)
    }
}
