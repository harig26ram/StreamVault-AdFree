package com.streamvault.player.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamUrlExtractorTest {

    private val extractor = StreamUrlExtractor()
    private val simpleCipherOps = listOf(
        CipherDecryptor.CipherOp.Reverse,
        CipherDecryptor.CipherOp.SpliceDelete(0, 2)
    )
    private val nTransformOp = NParamDecryptor.NTransformOp(listOf(
        Pair(0, 3), Pair(1, 2)
    ))

    @Test
    fun testExtractDirectUrls() {
        val json = """{
            "streamingData": {
                "formats": [
                    {
                        "itag": 18,
                        "url": "https://example.com/video.mp4?foo=bar",
                        "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
                        "bitrate": 500000,
                        "width": 640,
                        "height": 360,
                        "contentLength": "1000000"
                    }
                ],
                "adaptiveFormats": [
                    {
                        "itag": 140,
                        "url": "https://example.com/audio.m4a",
                        "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                        "bitrate": 128000,
                        "contentLength": "500000",
                        "audioChannels": 2,
                        "audioSampleRate": "44100"
                    }
                ]
            }
        }"""

        val formats = extractor.extract(json, simpleCipherOps, nTransformOp)
        assertEquals(2, formats.size)

        val video = formats.find { it.type == StreamType.PROGRESSIVE }
        assertNotNull(video)
        assertEquals(18, video?.itag)
        assertEquals(360, video?.height)
        assertEquals("https://example.com/video.mp4?foo=bar", video?.url)

        val audio = formats.find { it.type == StreamType.AUDIO_ONLY }
        assertNotNull(audio)
        assertEquals(140, audio?.itag)
    }

    @Test
    fun testExtractSignatureCipher() {
        val json = """{
            "streamingData": {
                "formats": [
                    {
                        "itag": 22,
                        "signatureCipher": "s=abcdefgh&sp=sig&url=https://example.com/video.mp4",
                        "mimeType": "video/mp4; codecs=\"avc1.64001F, mp4a.40.2\"",
                        "width": 1280,
                        "height": 720,
                        "bitrate": 2000000
                    }
                ]
            }
        }"""

        val formats = extractor.extract(json, simpleCipherOps, nTransformOp)
        assertEquals(1, formats.size)
        val fmt = formats[0]
        assertEquals(22, fmt.itag)
        assertTrue(fmt.url.startsWith("https://example.com/video.mp4"))
        assertTrue(fmt.url.contains("?sig="))
    }

    @Test
    fun testExtractCipherField() {
        val json = """{
            "streamingData": {
                "formats": [
                    {
                        "itag": 18,
                        "cipher": "s=123456&sp=signature&url=https://example.com/video.mp4",
                        "mimeType": "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
                        "width": 640,
                        "height": 360
                    }
                ]
            }
        }"""

        val formats = extractor.extract(json, simpleCipherOps, nTransformOp)
        assertEquals(1, formats.size)
        val fmt = formats[0]
        assertTrue(fmt.url.contains("?signature="))
    }

    @Test
    fun testExtractNParamTransformation() {
        val json = """{
            "streamingData": {
                "formats": [
                    {
                        "itag": 137,
                        "url": "https://example.com/video.mp4?n=abcdef",
                        "mimeType": "video/mp4; codecs=\"avc1.640028\"",
                        "width": 1920,
                        "height": 1080,
                        "bitrate": 3000000
                    }
                ]
            }
        }"""

        val formats = extractor.extract(json, simpleCipherOps, nTransformOp)
        assertEquals(1, formats.size)
        val fmt = formats[0]
        assertTrue(fmt.url.contains("n="))
        assertEquals("dcbaef", fmt.url.substringAfter("n="))
    }

    @Test
    fun testExtractWithYouTubePlayerResponse() {
        val streamingData = YouTubeStreamingData(
            formats = listOf(
                YouTubeFormat(
                    itag = 18,
                    url = "https://example.com/video.mp4",
                    mimeType = "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"",
                    bitrate = 500000,
                    width = 640,
                    height = 360,
                    contentLength = "1000000",
                    rangeInit = Range("0", "500"),
                    rangeIndex = Range("501", "1000"),
                    audioChannels = 2,
                    audioSampleRate = "44100",
                    quality = "medium",
                    projectionType = "RECTANGULAR",
                    highReplication = false,
                    audioQuality = "AUDIO_QUALITY_MEDIUM",
                    approxDurationMs = "60000",
                    fps = 30,
                    signatureCipher = null,
                    cipher = null
                )
            ),
            adaptiveFormats = null,
            dashManifestUrl = null,
            hlsManifestUrl = null,
            expiresInSeconds = null
        )

        val formats = extractor.extract(streamingData, simpleCipherOps, nTransformOp)
        assertEquals(1, formats.size)
        assertEquals(18, formats[0].itag)
    }

    @Test
    fun testGetSortedFormats() {
        val formats = listOf(
            DecryptedStreamFormat(137, "url1", "video/mp4", 1920, 1080, 3000000, null, null, null, null, null, null, null, null, StreamType.VIDEO_ONLY),
            DecryptedStreamFormat(136, "url2", "video/mp4", 1280, 720, 2000000, null, null, null, null, null, null, null, null, StreamType.VIDEO_ONLY),
            DecryptedStreamFormat(251, "url3", "audio/webm", null, null, 160000, null, 2, 48000, null, null, null, null, null, StreamType.AUDIO_ONLY),
            DecryptedStreamFormat(140, "url4", "audio/mp4", null, null, 128000, null, 2, 44100, null, null, null, null, null, StreamType.AUDIO_ONLY)
        )

        val sorted = extractor.getSortedFormats(formats)
        assertEquals(2, sorted.size)

        val videos = sorted[StreamType.VIDEO_ONLY]
        assertNotNull(videos)
        assertEquals(2, videos?.size)
        assertEquals(137, videos?.get(0)?.itag)

        val audios = sorted[StreamType.AUDIO_ONLY]
        assertNotNull(audios)
        assertEquals(2, audios?.size)
        assertEquals(251, audios?.get(0)?.itag)
    }

    @Test
    fun testDetermineStreamType() {
        val progressive = DecryptedStreamFormat(18, "url", "video/mp4; codecs=\"avc1.42001E, mp4a.40.2\"", 640, 360, 500000, null, null, null, null, null, null, null, null, StreamType.PROGRESSIVE)
        val videoOnly = DecryptedStreamFormat(137, "url", "video/mp4; codecs=\"avc1.640028\"", 1920, 1080, 3000000, null, null, null, null, null, null, null, null, StreamType.VIDEO_ONLY)
        val audioOnly = DecryptedStreamFormat(140, "url", "audio/mp4; codecs=\"mp4a.40.2\"", null, null, 128000, null, 2, 44100, null, null, null, null, null, StreamType.AUDIO_ONLY)

        val formats = listOf(progressive, videoOnly, audioOnly)
        val extracted = extractor.extract("""{"streamingData":{"formats":[],"adaptiveFormats":[]}}""", simpleCipherOps, nTransformOp)
        assertTrue(formats.any { it.type == StreamType.PROGRESSIVE })
        assertTrue(formats.any { it.type == StreamType.VIDEO_ONLY })
        assertTrue(formats.any { it.type == StreamType.AUDIO_ONLY })
    }

    @Test
    fun testEmptyResponse() {
        val formats = extractor.extract("{}", simpleCipherOps, nTransformOp)
        assertTrue(formats.isEmpty())
    }

    @Test
    fun testFormatWithAllFields() {
        val json = """{
            "streamingData": {
                "formats": [{
                    "itag": 299,
                    "url": "https://example.com/video.mp4",
                    "mimeType": "video/mp4; codecs=\"avc1.64002a\"",
                    "bitrate": 4500000,
                    "width": 1920,
                    "height": 1080,
                    "contentLength": "50000000",
                    "fps": 60,
                    "approxDurationMs": "300000",
                    "audioChannels": 2,
                    "audioSampleRate": "48000",
                    "highReplication": false,
                    "projectionType": "RECTANGULAR",
                    "quality": "hd1080"
                }]
            }
        }"""

        val formats = extractor.extract(json, simpleCipherOps, nTransformOp)
        assertEquals(1, formats.size)
        val fmt = formats[0]
        assertEquals(299, fmt.itag)
        assertEquals(1920, fmt.width)
        assertEquals(1080, fmt.height)
        assertEquals(4500000, fmt.bitrate)
        assertEquals(60, fmt.fps)
        assertEquals("300000", fmt.approxDurationMs)
    }
}
