package com.streamvault.player.youtube

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class PlayerJsFetcher {

    fun getJsUrl(responseJson: String): String? {
        return try {
            val gson = Gson()
            val root = gson.fromJson(responseJson, JsonObject::class.java)

            val jsUrl = root.get("jsUrl")?.asString
            if (jsUrl != null && jsUrl.isNotBlank()) return jsUrl

            val assets = root.getAsJsonObject("assets")
            if (assets != null) {
                val js = assets.get("js")?.asString
                if (js != null && js.isNotBlank()) return js
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    fun fetchJsContent(jsUrl: String): String? {
        return try {
            val fullUrl = if (jsUrl.startsWith("http")) jsUrl
            else "https://www.youtube.com$jsUrl"

            val url = URL(fullUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun extractCipherOperations(js: String): List<String> {
        val operations = mutableListOf<String>()

        val decipherPattern = Regex(
            """function\s*\([^)]*\)\s*\{(?:var\s+\w+\s*=\s*\w+\.split\(["']{2}\)[^;]*;)?[^}]*\.reverse\(\)[^}]*\.splice\([^)]*\)[^}]*\}""",
            RegexOption.DOT_MATCHES_ALL
        )

        val matches = decipherPattern.findAll(js)
        for (match in matches) {
            operations.add(match.value)
        }

        if (operations.isEmpty()) {
            val altPattern = Regex(
                """\w+\.reverse\(\).*?\w+\.splice\([^)]*\).*?\w+\.join\(["']{2}\)""",
                RegexOption.DOT_MATCHES_ALL
            )
            val altMatches = altPattern.findAll(js)
            for (match in altMatches) {
                operations.add("function(a){${
                    match.value.replace("\\s+".toRegex(), " ")
                }}")
            }
        }

        if (operations.isEmpty()) {
            val bodyPattern = Regex(
                """var\s+\w+\s*=\s*\w+\.split\(["']{2}\)[^;]*;[^}]*\.reverse\(\)[^;]*;[^}]*\.splice\([^)]*\)[^;]*;[^}]*\.join\(["']{2}\)""",
                RegexOption.DOT_MATCHES_ALL
            )
            val bodyMatches = bodyPattern.findAll(js)
            for (match in bodyMatches) {
                operations.add(match.value)
            }
        }

        return operations.distinct()
    }

    fun extractNTransformCode(js: String): String {
        val nFuncPattern = Regex(
            """function\s*\([^)]*\)\s*\{(?:var\s+\w+\s*=\s*\w+\.split\(["']{2}\)[^;]*;)?[^}]*?=\s*\w+\[\d+\][^}]*?join\(["']{2}\)[^}]*?\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = nFuncPattern.find(js)
        if (match != null) return match.value

        val nBodyPattern = Regex(
            """\w+\.split\(["']{2}\)[^;]*;(?:[^;]*;){2,10}\w+\.join\(["']{2}\)"""
        )
        val bodyMatch = nBodyPattern.find(js)
        if (bodyMatch != null) return "function(a){${bodyMatch.value}}"

        val nAltPattern = Regex(
            """split\(["']{2}\)[^;]{20,200}join\(["']{2}\)"""
        )
        val altMatch = nAltPattern.find(js)
        if (altMatch != null) return "function(a){var b=a.${altMatch.value}}"

        return ""
    }

    fun fetchPlayerData(responseJson: String): Pair<String?, String?> {
        val jsUrl = getJsUrl(responseJson) ?: return Pair(null, null)
        val jsContent = fetchJsContent(jsUrl) ?: return Pair(null, null)

        val cipherOps = extractCipherOperations(jsContent)
        val cipherJs = cipherOps.firstOrNull()

        val nTransform = extractNTransformCode(jsContent)

        return Pair(cipherJs, nTransform)
    }

    fun extractCipherOpCodes(jsContent: String): List<String> {
        val opCodes = mutableListOf<String>()

        val opDefPattern = Regex(
            """(\w+)\s*:\s*function\s*\([^)]*\)\s*\{[^}]*\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        val matches = opDefPattern.findAll(jsContent)
        for (match in matches) {
            opCodes.add("${match.groupValues[1]}:${match.groupValues[0]}")
        }

        return opCodes
    }
}
