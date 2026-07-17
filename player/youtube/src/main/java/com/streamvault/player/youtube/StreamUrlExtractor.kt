package com.streamvault.player.youtube

import com.google.gson.Gson
import com.google.gson.JsonObject

data class DecryptedStreamFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val bitrate: Int?,
    val contentLength: Long?,
    val audioChannels: Int?,
    val audioSampleRate: Int?,
    val fps: Int?,
    val approxDurationMs: String?,
    val initRange: String?,
    val indexRange: String?,
    val highReplication: Boolean?,
    val type: StreamType
)

enum class StreamType { PROGRESSIVE, VIDEO_ONLY, AUDIO_ONLY }

class StreamUrlExtractor(
    private val cipherDecryptor: CipherDecryptor = CipherDecryptor(),
    private val nParamDecryptor: NParamDecryptor = NParamDecryptor(),
    private val jsNTransformer: JsNTransformer? = null
) {

    fun extract(
        responseJson: String,
        cipherOperations: List<CipherDecryptor.CipherOp>,
        nTransformOp: NParamDecryptor.NTransformOp?
    ): List<DecryptedStreamFormat> {
        val gson = Gson()
        val json = gson.fromJson(responseJson, JsonObject::class.java)
        val streamingData = json.getAsJsonObject("streamingData") ?: run {
            android.util.Log.d("StreamUrlExtractor", "extract: streamingData is NULL")
            return emptyList()
        }

        val allFormats = mutableListOf<DecryptedStreamFormat>()
        val formats = streamingData.getAsJsonArray("formats")
        val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")

        android.util.Log.d("StreamUrlExtractor", "extract: formats=${formats?.size() ?: 0}, adaptiveFormats=${adaptiveFormats?.size() ?: 0}, cipherOps=${cipherOperations.size}, nTransform=${nTransformOp != null}")

        if (formats != null) {
            for (element in formats) {
                val fmt = element.asJsonObject
                val hasUrl = fmt.get("url")?.asString != null
                val hasCipher = fmt.get("signatureCipher")?.asString != null || fmt.get("cipher")?.asString != null
                android.util.Log.d("StreamUrlExtractor", "  format: itag=${fmt.get("itag")?.asInt}, hasUrl=$hasUrl, hasCipher=$hasCipher, mimeType=${fmt.get("mimeType")?.asString?.take(30)}")
                decryptFormat(fmt, cipherOperations, nTransformOp)?.let { allFormats.add(it) }
            }
        }
        if (adaptiveFormats != null) {
            for (element in adaptiveFormats) {
                val fmt = element.asJsonObject
                val hasUrl = fmt.get("url")?.asString != null
                val hasCipher = fmt.get("signatureCipher")?.asString != null || fmt.get("cipher")?.asString != null
                android.util.Log.d("StreamUrlExtractor", "  adaptive: itag=${fmt.get("itag")?.asInt}, hasUrl=$hasUrl, hasCipher=$hasCipher, mimeType=${fmt.get("mimeType")?.asString?.take(30)}")
                decryptFormat(fmt, cipherOperations, nTransformOp)?.let { allFormats.add(it) }
            }
        }

        android.util.Log.d("StreamUrlExtractor", "extract: returning ${allFormats.size} decrypted formats")
        return allFormats
    }

    fun extract(
        streamingData: YouTubeStreamingData,
        cipherOperations: List<CipherDecryptor.CipherOp>,
        nTransformOp: NParamDecryptor.NTransformOp?
    ): List<DecryptedStreamFormat> {
        val allFormats = mutableListOf<DecryptedStreamFormat>()

        streamingData.formats?.forEach { fmt ->
            decryptFormat(fmt, cipherOperations, nTransformOp)?.let { allFormats.add(it) }
        }
        streamingData.adaptiveFormats?.forEach { fmt ->
            decryptFormat(fmt, cipherOperations, nTransformOp)?.let { allFormats.add(it) }
        }

        return allFormats
    }

    fun extractFromResponse(
        response: YouTubePlayerResponse,
        cipherOperations: List<CipherDecryptor.CipherOp>,
        nTransformOp: NParamDecryptor.NTransformOp?
    ): List<DecryptedStreamFormat> {
        val sd = response.streamingData ?: return emptyList()
        return extract(sd, cipherOperations, nTransformOp)
    }

    fun getSortedFormats(
        formats: List<DecryptedStreamFormat>
    ): Map<StreamType, List<DecryptedStreamFormat>> {
        val grouped = formats.groupBy { it.type }
        val result = mutableMapOf<StreamType, List<DecryptedStreamFormat>>()

        grouped.forEach { (type, list) ->
            result[type] = when (type) {
                StreamType.PROGRESSIVE -> list.sortedByDescending { it.height ?: 0 }
                StreamType.VIDEO_ONLY -> list.sortedByDescending { it.height ?: 0 }
                StreamType.AUDIO_ONLY -> list.sortedByDescending { it.bitrate ?: 0 }
            }
        }

        return result
    }

    private fun decryptFormat(
        json: JsonObject,
        cipherOps: List<CipherDecryptor.CipherOp>,
        nTransformOp: NParamDecryptor.NTransformOp?
    ): DecryptedStreamFormat? {
        val itag = json.get("itag")?.asInt ?: return null
        val mimeType = json.get("mimeType")?.asString ?: return null

        val url = resolveUrl(json, cipherOps) ?: return null
        val finalUrl = resolveNParam(url, nTransformOp)

        val type = determineStreamType(itag, mimeType)

        return DecryptedStreamFormat(
            itag = itag,
            url = finalUrl,
            mimeType = mimeType,
            width = json.get("width")?.asInt,
            height = json.get("height")?.asInt,
            bitrate = json.get("bitrate")?.asInt,
            contentLength = json.get("contentLength")?.asString?.toLongOrNull(),
            audioChannels = json.get("audioChannels")?.asInt,
            audioSampleRate = json.get("audioSampleRate")?.asString?.toIntOrNull(),
            fps = json.get("fps")?.asInt,
            approxDurationMs = json.get("approxDurationMs")?.asString,
            initRange = json.getAsJsonObject("initRange")?.get("start")?.asString,
            indexRange = json.getAsJsonObject("indexRange")?.get("start")?.asString,
            highReplication = json.get("highReplication")?.asBoolean,
            type = type
        )
    }

    private fun decryptFormat(
        fmt: YouTubeFormat,
        cipherOps: List<CipherDecryptor.CipherOp>,
        nTransformOp: NParamDecryptor.NTransformOp?
    ): DecryptedStreamFormat? {
        val itag = fmt.itag ?: return null
        val mimeType = fmt.mimeType ?: return null

        val url = resolveUrl(fmt, cipherOps) ?: return null
        val finalUrl = resolveNParam(url, nTransformOp)

        val type = determineStreamType(itag, mimeType)

        return DecryptedStreamFormat(
            itag = itag,
            url = finalUrl,
            mimeType = mimeType,
            width = fmt.width,
            height = fmt.height,
            bitrate = fmt.bitrate,
            contentLength = fmt.contentLength?.toLongOrNull(),
            audioChannels = fmt.audioChannels,
            audioSampleRate = fmt.audioSampleRate?.toIntOrNull(),
            fps = fmt.fps,
            approxDurationMs = fmt.approxDurationMs,
            initRange = fmt.rangeInit?.start,
            indexRange = fmt.rangeIndex?.start,
            highReplication = fmt.highReplication,
            type = type
        )
    }

    private fun resolveUrl(json: JsonObject, cipherOps: List<CipherDecryptor.CipherOp>): String? {
        val directUrl = json.get("url")?.asString
        if (directUrl != null && directUrl.isNotBlank()) return directUrl

        val cipherStr = json.get("signatureCipher")?.asString
            ?: json.get("cipher")?.asString

        if (cipherStr == null || cipherStr.isBlank()) {
            android.util.Log.d("StreamUrlExtractor", "resolveUrl: no url, no cipher")
            return null
        }

        android.util.Log.d("StreamUrlExtractor", "resolveUrl: decrypting cipher (cipherStr len=${cipherStr.length}, ops=${cipherOps.size})")
        val result = decryptCipherUrl(cipherStr, cipherOps)
        android.util.Log.d("StreamUrlExtractor", "resolveUrl: decrypted url starts with=${result.take(100)}")
        return result
    }

    private fun resolveUrl(fmt: YouTubeFormat, cipherOps: List<CipherDecryptor.CipherOp>): String? {
        val directUrl = fmt.url
        if (directUrl != null && directUrl.isNotBlank()) return directUrl

        val cipherStr = fmt.signatureCipher ?: fmt.cipher
        if (cipherStr == null || cipherStr.isBlank()) return null

        return decryptCipherUrl(cipherStr, cipherOps)
    }

    private fun decryptCipherUrl(cipherStr: String, cipherOps: List<CipherDecryptor.CipherOp>): String {
        val params = parseQueryString(cipherStr)
        val url = params["url"] ?: return cipherStr
        val signature = params["s"] ?: return url
        val sp = params["sp"] ?: "sig"

        val decryptedSig = cipherDecryptor.decryptDirect(signature, cipherOps)

        val separator = if (url.contains("?")) "&" else "?"
        return "$url$separator$sp=$decryptedSig"
    }

    private fun resolveNParam(url: String, nTransformOp: NParamDecryptor.NTransformOp?): String {
        // Prefer the WebView JS evaluator — modern YouTube's `n`-throttle is an
        // obfuscated JS class (g.RY / dz) that regex swap-pair extraction cannot
        // replicate. Fall back to the regex path only when JS eval is unavailable.
        if (jsNTransformer != null) {
            return jsNTransformer.transformUrl(url) ?: resolveNParamRegex(url, nTransformOp)
        }
        return resolveNParamRegex(url, nTransformOp)
    }

    private fun findQueryParamIndex(url: String, param: String): Int? {
        val marker = "$param="
        var start = 0
        while (start < url.length) {
            val idx = url.indexOf(marker, start)
            if (idx < 0) return null
            if (idx == 0 || url[idx - 1] == '?' || url[idx - 1] == '&') {
                return idx
            }
            start = idx + marker.length
        }
        return null
    }

    private fun resolveNParamRegex(url: String, nTransformOp: NParamDecryptor.NTransformOp?): String {
        if (nTransformOp == null) return url
        val idx = findQueryParamIndex(url, "n") ?: return url
        val valueStart = idx + "n=".length
        var valueEnd = url.indexOf('&', valueStart)
        if (valueEnd < 0) valueEnd = url.length
        val rawN = url.substring(valueStart, valueEnd)
        if (rawN.isBlank()) return url

        val decoded = java.net.URLDecoder.decode(rawN, "UTF-8")
        val transformed = nParamDecryptor.decryptNParamDirect(decoded, nTransformOp)
        val encoded = java.net.URLEncoder.encode(transformed, "UTF-8")
        android.util.Log.d(
            "StreamUrlExtractor",
            "resolveNParam: transformed n (${rawN.length}->${encoded.length} chars)"
        )
        return url.substring(0, valueStart) + encoded + url.substring(valueEnd)
    }

    /**
     * Deciphers the throttling `n` parameter (and any cipher) of an already
     * resolved stream URL. Required for clients (e.g. IOS, TVHTML5) that return
     * formats with a *direct* `url` still carrying the encrypted `n` param —
     * without this YouTube rejects the stream fetch with HTTP 403.
     */
    fun decryptUrl(url: String, nTransformOp: NParamDecryptor.NTransformOp?): String {
        return resolveNParam(url, nTransformOp)
    }

    private fun parseQueryString(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (query.isBlank()) return params
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                params[java.net.URLDecoder.decode(key, "UTF-8")] =
                    java.net.URLDecoder.decode(value, "UTF-8")
            }
        }
        return params
    }

    private fun determineStreamType(itag: Int, mimeType: String): StreamType {
        val isVideo = mimeType.startsWith("video/")
        val isAudio = mimeType.startsWith("audio/")

        if (isVideo && isAudio) return StreamType.PROGRESSIVE

        if (isVideo) {
            val info = ItagInfo.get(itag)
            if (info != null && !info.isDash) return StreamType.PROGRESSIVE
            return StreamType.VIDEO_ONLY
        }

        if (isAudio) return StreamType.AUDIO_ONLY

        return StreamType.AUDIO_ONLY
    }

    companion object {
        fun buildSortedFormatMap(formats: List<DecryptedStreamFormat>): Map<StreamType, List<DecryptedStreamFormat>> {
            val grouped = formats.groupBy { it.type }
            val result = mutableMapOf<StreamType, List<DecryptedStreamFormat>>()

            grouped.forEach { (type, list) ->
                result[type] = when (type) {
                    StreamType.PROGRESSIVE -> list.sortedByDescending { it.height ?: 0 }
                    StreamType.VIDEO_ONLY -> list.sortedByDescending { it.height ?: 0 }
                    StreamType.AUDIO_ONLY -> list.sortedByDescending { it.bitrate ?: 0 }
                }
            }

            return result
        }
    }
}
