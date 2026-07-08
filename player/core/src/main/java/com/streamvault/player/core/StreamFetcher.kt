package com.streamvault.player.core

import android.media.MediaExtractor
import android.media.MediaFormat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class FormatInfo(
    val mimeType: String,
    val csd: List<ByteArray> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val durationUs: Long = 0L,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val channelConfig: Int = 0,
    val audioFormat: Int = 0
)

data class SampleInfo(
    val ptsUs: Long,
    val size: Int,
    val isKeyFrame: Boolean
)

interface DataSource {
    suspend fun open(): FormatInfo?
    suspend fun read(buffer: ByteBuffer): Int
    suspend fun readRaw(buffer: ByteBuffer, size: Int): Int
    suspend fun seekTo(targetTimeUs: Long, targetByteOffset: Long)
    fun close()
    fun isOpen(): Boolean
    val contentLength: Long
}

class HttpStreamFetcher(
    private val url: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : DataSource {
    private var response: okhttp3.Response? = null
    private var inputStream: InputStream? = null
    private var dataInput: DataInputStream? = null
    private var streamOffset: Long = 0L
    override var contentLength: Long = -1L
        private set

    private var formatInfo: FormatInfo? = null

    private var moofOffset: Long = 0L
    private var mdatStartOffset: Long = 0L
    private var mdatSize: Long = 0L
    private val samples = mutableListOf<SampleEntry>()
    private var sampleIndex = 0
    private var peekedSamplesEmpty = false

    private data class SampleEntry(
        val offset: Long,
        val size: Int,
        val duration: Int,
        val compositionTimeUs: Long,
        val isKeyFrame: Boolean
    )

    private data class BoxHeader(
        val type: String,
        val size: Long,
        val headerSize: Int,
        val payloadSize: Long
    )

    override suspend fun open(): FormatInfo? {
        close()
        val req = Request.Builder().url(url)
            .header("Accept", "*/*")
            .header("Connection", "keep-alive")
            .build()
        val resp = client.newCall(req).execute()
        response = resp
        val body = resp.body ?: return null
        contentLength = body.contentLength()
        val stream = BufferedInputStream(body.byteStream(), 128 * 1024)
        inputStream = stream
        dataInput = DataInputStream(stream)
        streamOffset = 0L
        return try {
            parseInitSegment().also { formatInfo = it }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    override suspend fun read(buffer: ByteBuffer): Int {
        while (sampleIndex >= samples.size) {
            if (!loadNextFragment()) return -1
        }
        val entry = samples[sampleIndex++]
        val targetOffset = mdatStartOffset + entry.offset
        if (streamOffset != targetOffset) {
            skipTo(targetOffset)
        }
        val buf = ByteArray(entry.size)
        readExact(buf, 0, entry.size)
        buffer.put(buf)
        return entry.size
    }

    override suspend fun readRaw(buffer: ByteBuffer, size: Int): Int {
        val toRead = min(size, buffer.remaining())
        val buf = ByteArray(toRead)
        val n = dataInput?.read(buf) ?: -1
        if (n < 0) return -1
        buffer.put(buf, 0, n)
        streamOffset += n
        return n
    }

    override suspend fun seekTo(targetTimeUs: Long, targetByteOffset: Long) {
        if (targetByteOffset > 0L) {
            close()
            val req = Request.Builder().url(url)
                .header("Range", "bytes=$targetByteOffset-")
                .build()
            try {
                val resp = client.newCall(req).execute()
                response = resp
                val body = resp.body ?: return
                val stream = BufferedInputStream(body.byteStream(), 128 * 1024)
                inputStream = stream
                dataInput = DataInputStream(stream)
                streamOffset = targetByteOffset
            } catch (e: Exception) {
                close()
                throw e
            }
            return
        }
        close()
        open()
        var lastKeyFrameOffset = -1L
        var lastKeyFrameIndex = -1
        while (true) {
            loadNextFragment()
            if (samples.isEmpty()) break
            for ((i, s) in samples.withIndex()) {
                if (s.compositionTimeUs >= targetTimeUs) {
                    val idx = if (s.isKeyFrame) i else lastKeyFrameIndex
                    if (idx >= 0) {
                        sampleIndex = idx
                        return
                    }
                }
                if (s.isKeyFrame) {
                    lastKeyFrameIndex = i
                    lastKeyFrameOffset = mdatStartOffset + s.offset
                }
            }
        }
    }

    override fun close() {
        try {
            dataInput?.close()
            response?.close()
        } catch (_: Exception) {}
        dataInput = null
        inputStream = null
        response = null
        streamOffset = 0L
        samples.clear()
        sampleIndex = 0
        formatInfo = null
        peekedSamplesEmpty = false
        mdatStartOffset = 0L
        mdatSize = 0L
    }

    override fun isOpen(): Boolean = response != null

    private suspend fun parseInitSegment(): FormatInfo? {
        var videoInfo: FormatInfo? = null
        var audioInfo: FormatInfo? = null
        while (true) {
            val header = readBoxHeader() ?: break
            when (header.type) {
                "moov" -> {
                    val result = parseMoov(header)
                    if (result != null) {
                        if (result.mimeType.startsWith("video/")) videoInfo = result
                        else audioInfo = result
                        if (videoInfo != null && audioInfo != null) break
                    }
                }
                "ftyp" -> skipBox(header)
                else -> skipBox(header)
            }
        }
        return videoInfo ?: audioInfo
    }

    private suspend fun loadNextFragment(): Boolean {
        samples.clear()
        sampleIndex = 0
        var moofFound = false
        while (true) {
            val header = readBoxHeader() ?: return false
            when (header.type) {
                "moof" -> {
                    parseMoof(header)
                    moofFound = true
                }
                "mdat" -> {
                    if (moofFound) {
                        mdatStartOffset = streamOffset
                        mdatSize = header.payloadSize
                        skipBox(header)
                        return true
                    }
                    skipBox(header)
                }
                else -> {
                    if (header.type == "moov") return false
                    skipBox(header)
                }
            }
        }
    }

    private suspend fun readBoxHeader(): BoxHeader? {
        val input = dataInput ?: return null
        val sizeBytes = ByteArray(4)
        if (input.read(sizeBytes, 0, 4) < 4) return null
        streamOffset += 4
        val size = readU32(sizeBytes, 0)
        val typeBytes = ByteArray(4)
        if (input.read(typeBytes, 0, 4) < 4) return null
        streamOffset += 4
        val type = String(typeBytes, Charsets.ISO_8859_1)
        var actualSize = size.toLong()
        var headerSize = 8
        if (size == 1) {
            val extSizeBytes = ByteArray(8)
            if (input.read(extSizeBytes, 0, 8) < 8) return null
            streamOffset += 8
            actualSize = readU64BE(extSizeBytes, 0)
            headerSize = 16
        }
        val payloadSize = actualSize - headerSize
        return BoxHeader(type, actualSize, headerSize, payloadSize)
    }

    private suspend fun skipBox(header: BoxHeader) {
        skipBytes(header.payloadSize)
    }

    private suspend fun parseMoov(header: BoxHeader): FormatInfo? {
        val endOffset = streamOffset + header.payloadSize
        var result: FormatInfo? = null
        while (streamOffset < endOffset) {
            val child = readBoxHeader() ?: break
            when (child.type) {
                "trak" -> {
                    val info = parseTrak(child)
                    if (info != null) result = info
                }
                "mvhd" -> skipBox(child)
                else -> skipBox(child)
            }
        }
        return result
    }

    private suspend fun parseTrak(header: BoxHeader): FormatInfo? {
        val endOffset = streamOffset + header.payloadSize
        var mimeType: String? = null
        val csd = mutableListOf<ByteArray>()
        var width = 0
        var height = 0
        var sampleRate = 0
        var channelCount = 0
        var duration = 0L
        while (streamOffset < endOffset) {
            val child = readBoxHeader() ?: break
            when (child.type) {
                "tkhd" -> {
                    val tkhdData = readBytes(child.payloadSize)
                    val buf = ByteBuffer.wrap(tkhdData).order(ByteOrder.BIG_ENDIAN)
                    val version = buf.get().toInt() and 0xFF
                    buf.position(buf.position() + 3)
                    if (version == 1) {
                        buf.position(buf.position() + 8 + 8 + 4 + 8 + 8 + 4 + 4 + 4 + 4)
                        duration = buf.getLong()
                        buf.position(buf.position() + 4 + 4 + 4 + 4 + 4 + 4)
                    } else {
                        buf.position(buf.position() + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4)
                        duration = buf.getInt().toLong() and 0xFFFFFFFFL
                        buf.position(buf.position() + 4 + 4 + 4 + 4 + 4 + 4)
                    }
                    width = (buf.getInt() shr 16)
                    height = (buf.getInt() shr 16)
                }
                "mdia" -> {
                    val mdiaInfo = parseMdia(child)
                    if (mdiaInfo != null) {
                        mimeType = mdiaInfo.mimeType
                        csd.addAll(mdiaInfo.csd)
                        sampleRate = mdiaInfo.sampleRate
                        channelCount = mdiaInfo.channelCount
                    }
                }
                else -> skipBox(child)
            }
        }
        if (mimeType == null) return null
        return FormatInfo(
            mimeType = mimeType,
            csd = csd,
            width = width,
            height = height,
            durationUs = duration,
            sampleRate = sampleRate,
            channelCount = channelCount
        )
    }

    private suspend fun parseMdia(header: BoxHeader): FormatInfo? {
        val endOffset = streamOffset + header.payloadSize
        var mimeType: String? = null
        val csd = mutableListOf<ByteArray>()
        var sampleRate = 0
        var channelCount = 0
        while (streamOffset < endOffset) {
            val child = readBoxHeader() ?: break
            when (child.type) {
                "minf" -> {
                    val minfInfo = parseMinf(child)
                    if (minfInfo != null) {
                        mimeType = minfInfo.mimeType
                        csd.addAll(minfInfo.csd)
                    }
                }
                "mdhd" -> {
                    val mdhdData = readBytes(child.payloadSize)
                    val buf = ByteBuffer.wrap(mdhdData).order(ByteOrder.BIG_ENDIAN)
                    val version = buf.get().toInt() and 0xFF
                    buf.position(buf.position() + 3)
                    if (version == 1) {
                        buf.position(buf.position() + 8)
                        val timescale = buf.getInt()
                        buf.position(buf.position() + 4)
                    } else {
                        buf.position(buf.position() + 4)
                        val timescale = buf.getInt()
                    }
                }
                "hdlr" -> skipBox(child)
                else -> skipBox(child)
            }
        }
        if (mimeType == null) return null
        return FormatInfo(
            mimeType = mimeType,
            csd = csd,
            sampleRate = sampleRate,
            channelCount = channelCount
        )
    }

    private suspend fun parseMinf(header: BoxHeader): FormatInfo? {
        val endOffset = streamOffset + header.payloadSize
        var result: FormatInfo? = null
        while (streamOffset < endOffset) {
            val child = readBoxHeader() ?: break
            when (child.type) {
                "stbl" -> result = parseStbl(child)
                "vmhd" -> skipBox(child)
                "smhd" -> skipBox(child)
                "dinf" -> skipBox(child)
                else -> skipBox(child)
            }
        }
        return result
    }

    private suspend fun parseStbl(header: BoxHeader): FormatInfo? {
        val endOffset = streamOffset + header.payloadSize
        var result: FormatInfo? = null
        while (streamOffset < endOffset) {
            val child = readBoxHeader() ?: break
            when (child.type) {
                "stsd" -> result = parseStsd(child)
                "stts", "stsc", "stsz", "stco", "stss", "ctts" -> skipBox(child)
                else -> skipBox(child)
            }
        }
        return result
    }

    private suspend fun parseStsd(header: BoxHeader): FormatInfo? {
        val endOffset = streamOffset + header.payloadSize
        readBytes(4)
        val entryCount = readU32()
        var result: FormatInfo? = null
        for (i in 0 until entryCount) {
            if (streamOffset >= endOffset) break
            val entry = readBoxHeader() ?: break
            when (entry.type) {
                "avc1", "avc3", "hev1", "hvc1" -> {
                    val csd = mutableListOf<ByteArray>()
                    readBytes(78)
                    val configBox = readBoxHeader() ?: break
                    if (configBox.type == "avcC" || configBox.type == "hvcC") {
                        val csdData = readBytes(configBox.payloadSize)
                        val avcBuf = ByteBuffer.wrap(csdData).order(ByteOrder.BIG_ENDIAN)
                        val configVersion = avcBuf.get().toInt() and 0xFF
                        if (configVersion == 1) {
                            avcBuf.position(avcBuf.position() + 3)
                            avcBuf.get()
                            val numSps = avcBuf.get().toInt() and 0x1F
                            for (s in 0 until numSps) {
                                val spsLen = avcBuf.getShort().toInt() and 0xFFFF
                                val sps = ByteArray(spsLen)
                                avcBuf.get(sps)
                                csd.add(0, sps)
                            }
                            val numPps = avcBuf.get().toInt() and 0xFF
                            for (p in 0 until numPps) {
                                val ppsLen = avcBuf.getShort().toInt() and 0xFFFF
                                val pps = ByteArray(ppsLen)
                                avcBuf.get(pps)
                                csd.add(pps)
                            }
                        }
                    }
                    val videoEntryMime = if (entry.type.startsWith("avc")) "video/avc" else "video/hevc"
                    result = FormatInfo(mimeType = videoEntryMime, csd = csd)
                }
                "mp4a" -> {
                    val csd = mutableListOf<ByteArray>()
                    val entryData = readBytes(28)
                    val entryBuf = ByteBuffer.wrap(entryData).order(ByteOrder.BIG_ENDIAN)
                    entryBuf.position(entryBuf.position() + 6)
                    entryBuf.position(entryBuf.position() + 8)
                    val chCount = entryBuf.getShort().toInt() and 0xFFFF
                    entryBuf.getShort()
                    entryBuf.position(entryBuf.position() + 4)
                    val sampRate = (entryBuf.getInt() ushr 16)
                    val esaBox = readBoxHeader() ?: break
                    if (esaBox.type == "esds") {
                        val esdsData = readBytes(esaBox.payloadSize)
                        val esdsBuf = ByteBuffer.wrap(esdsData).order(ByteOrder.BIG_ENDIAN)
                        esdsBuf.position(esdsBuf.position() + 4)
                        val esTag = esdsBuf.get().toInt() and 0xFF
                        if (esTag == 0x03) {
                            skipMpeg4DescriptorLength(esdsBuf)
                            esdsBuf.position(esdsBuf.position() + 2)
                            esdsBuf.get()
                            val dcTag = esdsBuf.get().toInt() and 0xFF
                            if (dcTag == 0x04) {
                                skipMpeg4DescriptorLength(esdsBuf)
                                esdsBuf.position(esdsBuf.position() + 4)
                                esdsBuf.position(esdsBuf.position() + 8)
                                val dsTag = esdsBuf.get().toInt() and 0xFF
                                if (dsTag == 0x05) {
                                    val dsLen = readMpeg4DescriptorLength(esdsBuf)
                                    val audioCfg = ByteArray(dsLen)
                                    esdsBuf.get(audioCfg)
                                    csd.add(audioCfg)
                                }
                            }
                        }
                    }
                    result = FormatInfo(
                        mimeType = "audio/mp4a-latm",
                        csd = csd,
                        sampleRate = sampRate,
                        channelCount = chCount
                    )
                }
                else -> skipBox(entry)
            }
        }
        return result
    }

    private suspend fun parseMoof(header: BoxHeader) {
        val endOffset = streamOffset + header.payloadSize
        var baseDataOffset = streamOffset - header.headerSize
        var defaultSampleDuration = 0
        var defaultSampleSize = 0
        var defaultSampleFlags = 0
        while (streamOffset < endOffset) {
            val child = readBoxHeader() ?: break
            when (child.type) {
                "traf" -> {
                    val trafResult = parseTraf(
                        child, baseDataOffset, defaultSampleDuration,
                        defaultSampleSize, defaultSampleFlags
                    )
                    if (trafResult != null) {
                        baseDataOffset = trafResult.baseDataOffset
                        defaultSampleDuration = trafResult.defaultSampleDuration
                        defaultSampleSize = trafResult.defaultSampleSize
                        defaultSampleFlags = trafResult.defaultSampleFlags
                        samples.addAll(trafResult.entries)
                    }
                }
                "mfhd" -> skipBox(child)
                else -> skipBox(child)
            }
        }
    }

    private data class TrafResult(
        val baseDataOffset: Long,
        val defaultSampleDuration: Int,
        val defaultSampleSize: Int,
        val defaultSampleFlags: Int,
        val trackId: Int,
        val entries: List<SampleEntry>
    )

    private suspend fun parseTraf(
        header: BoxHeader,
        parentBaseDataOffset: Long,
        parentDefaultDuration: Int,
        parentDefaultSize: Int,
        parentDefaultFlags: Int
    ): TrafResult? {
        val endOffset = streamOffset + header.payloadSize
        var baseDataOffset = parentBaseDataOffset
        var defaultSampleDuration = parentDefaultDuration
        var defaultSampleSize = parentDefaultSize
        var defaultSampleFlags = parentDefaultFlags
        var trackId = 0
        val entries = mutableListOf<SampleEntry>()
        while (streamOffset < endOffset) {
            val child = readBoxHeader() ?: break
            when (child.type) {
                "tfhd" -> {
                    val data = readBytes(child.payloadSize)
                    val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                    val rawFlags = buf.getInt()
                    val flags = rawFlags and 0x00FFFFFF
                    trackId = buf.getInt()
                    if (flags and 0x000001 != 0) baseDataOffset = buf.getLong()
                    if (flags and 0x000002 != 0) defaultSampleDuration = buf.getInt()
                    if (flags and 0x000008 != 0) defaultSampleSize = buf.getInt()
                    if (flags and 0x000010 != 0) defaultSampleFlags = buf.getInt()
                }
                "tfdt" -> {
                    val data = readBytes(child.payloadSize)
                    val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                    val version = buf.get().toInt() and 0xFF
                    buf.position(buf.position() + 3)
                }
                "trun" -> {
                    val data = readBytes(child.payloadSize)
                    val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                    val rawFlags = buf.getInt()
                    val flags = rawFlags and 0x00FFFFFF
                    val sampleCount = buf.getInt()
                    var dataOffset: Long = 0
                    var firstSampleFlags = defaultSampleFlags
                    if (flags and 0x000001 != 0) dataOffset = buf.getInt().toLong()
                    if (flags and 0x000004 != 0) firstSampleFlags = buf.getInt()
                    var runningOffset = dataOffset
                    var cumulativeTime = 0L
                    for (i in 0 until sampleCount) {
                        val dur = if (flags and 0x000100 != 0) buf.getInt() else defaultSampleDuration
                        val size = if (flags and 0x000200 != 0) buf.getInt() else defaultSampleSize
                        val sampFlags = if (flags and 0x000400 != 0) buf.getInt() else if (i == 0) firstSampleFlags else defaultSampleFlags
                        val compTime = if (flags and 0x000800 != 0) buf.getInt() else 0
                        val isKeyFrame = (sampFlags and 0x01000000) == 0
                        entries.add(
                            SampleEntry(
                                offset = runningOffset,
                                size = size,
                                duration = dur,
                                compositionTimeUs = cumulativeTime + compTime,
                                isKeyFrame = isKeyFrame
                            )
                        )
                        runningOffset += size.toLong()
                        cumulativeTime += dur.toLong()
                    }
                }
                else -> skipBox(child)
            }
        }
        return TrafResult(
            baseDataOffset = baseDataOffset,
            defaultSampleDuration = defaultSampleDuration,
            defaultSampleSize = defaultSampleSize,
            defaultSampleFlags = defaultSampleFlags,
            trackId = trackId,
            entries = entries
        )
    }

    private suspend fun skipBytes(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = dataInput?.skip(remaining) ?: 0L
            if (skipped <= 0L) {
                if (dataInput?.read() ?: -1 < 0) break
                streamOffset++
                remaining--
            } else {
                streamOffset += skipped
                remaining -= skipped
            }
        }
    }

    private suspend fun readBytes(count: Long): ByteArray {
        val bytes = ByteArray(count.toInt())
        readExact(bytes, 0, bytes.size)
        return bytes
    }

    private suspend fun readExact(bytes: ByteArray, offset: Int, length: Int) {
        var pos = offset
        var remaining = length
        while (remaining > 0) {
            val read = dataInput?.read(bytes, pos, remaining) ?: -1
            if (read < 0) throw java.io.EOFException("Unexpected end of stream")
            pos += read
            remaining -= read
        }
        streamOffset += length
    }

    private fun readU64BE(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFFL) shl 56) or
                ((data[offset + 1].toLong() and 0xFFL) shl 48) or
                ((data[offset + 2].toLong() and 0xFFL) shl 40) or
                ((data[offset + 3].toLong() and 0xFFL) shl 32) or
                ((data[offset + 4].toLong() and 0xFFL) shl 24) or
                ((data[offset + 5].toLong() and 0xFFL) shl 16) or
                ((data[offset + 6].toLong() and 0xFFL) shl 8) or
                (data[offset + 7].toLong() and 0xFFL)
    }

    private fun readU32(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private suspend fun readU32(): Int {
        val bytes = ByteArray(4)
        readExact(bytes, 0, 4)
        return readU32(bytes, 0)
    }

    private suspend fun skipTo(targetOffset: Long) {
        if (targetOffset < streamOffset) throw IllegalStateException("Cannot seek backwards in HTTP stream without reopening")
        skipBytes(targetOffset - streamOffset)
    }

    private fun skipMpeg4DescriptorLength(buf: ByteBuffer) {
        readMpeg4DescriptorLength(buf)
    }

    private fun readMpeg4DescriptorLength(buf: ByteBuffer): Int {
        var length = 0
        var b: Int
        do {
            b = buf.get().toInt() and 0xFF
            length = (length shl 7) or (b and 0x7F)
        } while (b and 0x80 != 0)
        return length
    }
}

class MediaExtractorDataSource(
    private val extractor: MediaExtractor,
    private val mimeType: String,
    private val trackFormat: MediaFormat,
    private val isVideoTrack: Boolean
) : DataSource {
    private var isOpen = true
    private var formatInfo: FormatInfo? = null
    override var contentLength: Long = -1L

    init {
        val csd = mutableListOf<ByteArray>()
        for (key in listOf("csd-0", "csd-1", "csd-2")) {
            @Suppress("DEPRECATION")
            val buf = trackFormat.getByteBuffer(key)
            if (buf != null) {
                val arr = ByteArray(buf.remaining())
                buf.get(arr)
                buf.rewind()
                csd.add(arr)
            }
        }
        val width = if (isVideoTrack && trackFormat.containsKey(MediaFormat.KEY_WIDTH)) {
            trackFormat.getInteger(MediaFormat.KEY_WIDTH)
        } else 0
        val height = if (isVideoTrack && trackFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
            trackFormat.getInteger(MediaFormat.KEY_HEIGHT)
        } else 0
        val sampleRate = if (!isVideoTrack && trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 0
        val channelCount = if (!isVideoTrack && trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 0
        val duration = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
            trackFormat.getLong(MediaFormat.KEY_DURATION)
        } else 0L

        formatInfo = FormatInfo(
            mimeType = mimeType,
            csd = csd,
            width = width,
            height = height,
            durationUs = duration,
            sampleRate = sampleRate,
            channelCount = channelCount
        )
    }

    override suspend fun open(): FormatInfo? = formatInfo

    override suspend fun read(buffer: ByteBuffer): Int {
        val info = android.media.MediaCodec.BufferInfo()
        val bytesRead = extractor.readSampleData(buffer, 0)
        if (bytesRead < 0) return -1
        extractor.advance()
        return bytesRead
    }

    override suspend fun readRaw(buffer: ByteBuffer, size: Int): Int {
        return read(buffer)
    }

    override suspend fun seekTo(targetTimeUs: Long, targetByteOffset: Long) {
        extractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    }

    override fun close() {
        isOpen = false
    }

    override fun isOpen(): Boolean = isOpen
}
