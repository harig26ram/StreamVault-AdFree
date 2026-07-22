package com.freedomplay.app.data.potoken

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

// Ported from NewPipe (GPLv3). nanojson replaced with Gson (already a project dependency).

/**
 * Parses the raw challenge data obtained from the Create endpoint and returns an object that can be
 * embedded in a JavaScript snippet.
 */
fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JsonParser.parseString(rawChallengeData).asJsonArray

    val challengeData: JsonArray = if (scrambled.size() > 1 && scrambled[1].isJsonPrimitive) {
        val descrambled = descramble(scrambled[1].asString)
        JsonParser.parseString(descrambled).asJsonArray
    } else {
        scrambled[0].asJsonArray
    }

    val messageId = challengeData[0].asString
    val interpreterHash = challengeData[3].asString
    val program = challengeData[4].asString
    val globalName = challengeData[5].asString
    val clientExperimentsStateBlob = challengeData[7].asString

    val privateDoNotAccessOrElseSafeScriptWrappedValue: String? =
        challengeData[1].asJsonArray?.firstOrNull { it.isJsonPrimitive }?.asString
    val privateDoNotAccessOrElseTrustedResourceUrlWrappedValue: String? =
        challengeData[2].asJsonArray?.firstOrNull { it.isJsonPrimitive }?.asString

    val builder = JsonObject().apply {
        addProperty("messageId", messageId)
        add("interpreterJavascript", JsonObject().apply {
            addProperty("privateDoNotAccessOrElseSafeScriptWrappedValue",
                privateDoNotAccessOrElseSafeScriptWrappedValue)
            addProperty("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue",
                privateDoNotAccessOrElseTrustedResourceUrlWrappedValue)
        })
        addProperty("interpreterHash", interpreterHash)
        addProperty("program", program)
        addProperty("globalName", globalName)
        addProperty("clientExperimentsStateBlob", clientExperimentsStateBlob)
    }

    return builder.toString()
}

/**
 * Parses the raw integrity token data obtained from the GenerateIT endpoint to a JavaScript
 * `Uint8Array` that can be embedded directly in JavaScript code, and a [Long] representing the
 * duration of this token in seconds.
 */
fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
    val integrityTokenData = JsonParser.parseString(rawIntegrityTokenData).asJsonArray
    return base64ToU8(integrityTokenData[0].asString) to integrityTokenData[1].asLong
}

/**
 * Converts a string (usually the identifier used as input to `obtainPoToken`) to a JavaScript
 * `Uint8Array` that can be embedded directly in JavaScript code.
 */
fun stringToU8(identifier: String): String {
    return newUint8Array(identifier.toByteArray())
}

/**
 * Takes a poToken encoded as a sequence of bytes represented as integers separated by commas
 * (e.g. "97,98,99" would be "abc"), which is the output of `Uint8Array::toString()` in JavaScript,
 * and converts it to the specific base64 representation for poTokens.
 */
fun u8ToBase64(poToken: String): String {
    return poToken.split(",")
        .map { it.toUByte().toByte() }
        .toByteArray()
        .toByteString()
        .base64()
        .replace("+", "-")
        .replace("/", "_")
}

/**
 * Takes the scrambled challenge, decodes it from base64, adds 97 to each byte.
 */
private fun descramble(scrambledChallenge: String): String {
    return base64ToByteString(scrambledChallenge)
        .map { (it + 97).toByte() }
        .toByteArray()
        .decodeToString()
}

/**
 * Decodes a base64 string encoded in the specific base64 representation used by YouTube, and
 * returns a JavaScript `Uint8Array` that can be embedded directly in JavaScript code.
 */
private fun base64ToU8(base64: String): String {
    return newUint8Array(base64ToByteString(base64))
}

private fun newUint8Array(contents: ByteArray): String {
    return "new Uint8Array([" +
        contents.joinToString(separator = ",") { it.toUByte().toString() } + "])"
}

/**
 * Decodes a base64 string encoded in the specific base64 representation used by YouTube.
 */
private fun base64ToByteString(base64: String): ByteArray {
    val base64Mod = base64
        .replace('-', '+')
        .replace('_', '/')
        .replace('.', '=')

    return (base64Mod.decodeBase64() ?: throw PoTokenException("Cannot base64 decode"))
        .toByteArray()
}
