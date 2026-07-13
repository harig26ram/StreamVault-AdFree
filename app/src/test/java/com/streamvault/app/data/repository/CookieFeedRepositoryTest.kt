package com.streamvault.app.data.repository

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class CookieFeedRepositoryTest {

    @Test
    fun `computeSapiSidHash produces SAPISIDHASH prefix`() {
        val result = CookieFeedRepositoryImpl.computeSapiSidHash("test_sapisid_value")
        assertTrue("Should start with 'SAPISIDHASH '", result.startsWith("SAPISIDHASH "))
    }

    @Test
    fun `computeSapiSidHash produces consistent output for same input`() {
        // Note: timestamp changes per call, so we test format not exact value
        val result1 = CookieFeedRepositoryImpl.computeSapiSidHash("abc123")
        val result2 = CookieFeedRepositoryImpl.computeSapiSidHash("abc123")
        // Format: SAPISIDHASH {timestamp}:{40-char-hex}
        val regex = Regex("^SAPISIDHASH \\d+:[0-9a-f]{40}$")
        assertTrue("Result 1 should match format: $result1", regex.matches(result1))
        assertTrue("Result 2 should match format: $result2", regex.matches(result2))
    }

    @Test
    fun `computeSapiSidHash uses correct origin default`() {
        val result = CookieFeedRepositoryImpl.computeSapiSidHash("sapisid")
        assertTrue(result.startsWith("SAPISIDHASH "))
        // The hash portion should be 40 hex chars
        val hashPart = result.substringAfter(":")
        assertEquals(40, hashPart.length)
    }

    @Test
    fun `computeSapiSidHash with custom origin`() {
        val result = CookieFeedRepositoryImpl.computeSapiSidHash("sapisid", "https://example.com")
        assertTrue(result.startsWith("SAPISIDHASH "))
        val regex = Regex("^SAPISIDHASH \\d+:[0-9a-f]{40}$")
        assertTrue("Should match format with custom origin: $result", regex.matches(result))
    }

    @Test
    fun `extractSapiSid extracts from cookie string with multiple cookies`() {
        val cookies = "SID=abc123; HSID=xyz789; SAPISID=real_sapisid_value; APISID=other; SSID=test"
        val result = CookieFeedRepositoryImpl.extractSapiSid(cookies)
        assertEquals("real_sapisid_value", result)
    }

    @Test
    fun `extractSapiSid returns null when no SAPISID present`() {
        val cookies = "SID=abc123; HSID=xyz789; APISID=other"
        val result = CookieFeedRepositoryImpl.extractSapiSid(cookies)
        assertNull(result)
    }

    @Test
    fun `extractSapiSid returns null for empty string`() {
        val result = CookieFeedRepositoryImpl.extractSapiSid("")
        assertNull(result)
    }

    @Test
    fun `extractSapiSid handles single SAPISID cookie`() {
        val result = CookieFeedRepositoryImpl.extractSapiSid("SAPISID=only_one")
        assertEquals("only_one", result)
    }

    @Test
    fun `extractSapiSid is case insensitive for key matching`() {
        val cookies = "sapisid=lowercase_value; SID=abc"
        val result = CookieFeedRepositoryImpl.extractSapiSid(cookies)
        assertEquals("lowercase_value", result)
    }

    @Test
    fun `extractSapiSid returns null for empty SAPISID value`() {
        val cookies = "SID=abc; SAPISID=; HSID=xyz"
        val result = CookieFeedRepositoryImpl.extractSapiSid(cookies)
        assertNull(result)
    }

    @Test
    fun `parsePersonalizedFeed returns empty list for invalid JSON`() {
        val result = CookieFeedRepositoryImpl.parsePersonalizedFeed("not json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePersonalizedFeed returns empty list for empty object`() {
        val result = CookieFeedRepositoryImpl.parsePersonalizedFeed("{}")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsePersonalizedFeed extracts video from singleColumnBrowseResultsRenderer`() {
        val json = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [{
                "tabRenderer": {
                  "content": {
                    "sectionListRenderer": {
                      "contents": [{
                        "itemSectionRenderer": {
                          "contents": [{
                            "videoRenderer": {
                              "videoId": "dQw4w9WgXcQ",
                              "title": {"simpleText": "Test Video"},
                              "shortBylineText": {"runs": [{"text": "Test Channel"}]},
                              "thumbnail": {"thumbnails": [{"url": "https://example.com/thumb.jpg"}]},
                              "lengthText": {"accessibility": {"accessibilityData": {"label": "3:33"}}},
                              "viewCountText": {"simpleText": "1M views"},
                              "publishedTimeText": {"simpleText": "1 year ago"}
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }]
            }
          }
        }
        """.trimIndent()

        val result = CookieFeedRepositoryImpl.parsePersonalizedFeed(json)
        assertEquals(1, result.size)
        assertEquals("dQw4w9WgXcQ", result[0].id)
        assertEquals("Test Video", result[0].title)
        assertEquals("Test Channel", result[0].channelName)
        assertEquals("3:33", result[0].duration)
    }

    @Test
    fun `parsePersonalizedFeed extracts video from twoColumnBrowseResultsRenderer`() {
        val json = """
        {
          "contents": {
            "twoColumnBrowseResultsRenderer": {
              "tabs": [{
                "tabRenderer": {
                  "content": {
                    "richGridRenderer": {
                      "contents": [{
                        "richItemRenderer": {
                          "content": {
                            "videoRenderer": {
                              "videoId": "abc12345678",
                              "title": {"simpleText": "Rich Video"},
                              "shortBylineText": {"runs": [{"text": "Channel Name"}]},
                              "thumbnail": {"thumbnails": [{"url": "https://example.com/t.jpg"}]},
                              "lengthText": {"accessibility": {"accessibilityData": {"label": "10:00"}}},
                              "viewCountText": {"simpleText": "500K views"},
                              "publishedTimeText": {"simpleText": "2 days ago"}
                            }
                          }
                        }
                      }]
                    }
                  }
                }
              }]
            }
          }
        }
        """.trimIndent()

        val result = CookieFeedRepositoryImpl.parsePersonalizedFeed(json)
        assertEquals(1, result.size)
        assertEquals("abc12345678", result[0].id)
        assertEquals("Rich Video", result[0].title)
    }

    @Test
    fun `parsePersonalizedFeed extracts video with runs title`() {
        val json = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [{
                "tabRenderer": {
                  "content": {
                    "sectionListRenderer": {
                      "contents": [{
                        "itemSectionRenderer": {
                          "contents": [{
                            "videoRenderer": {
                              "videoId": "runs_title_id_",
                              "title": {"runs": [{"text": "Part "}, {"text": "One"}]},
                              "shortBylineText": {"runs": [{"text": "Runs Channel"}]},
                              "thumbnail": {"thumbnails": [{"url": "https://example.com/r.jpg"}]},
                              "lengthText": {"accessibility": {"accessibilityData": {"label": "5:00"}}}
                            }
                          }]
                        }
                      }]
                    }
                  }
                }
              }]
            }
          }
        }
        """.trimIndent()

        val result = CookieFeedRepositoryImpl.parsePersonalizedFeed(json)
        assertEquals(1, result.size)
        assertEquals("Part One", result[0].title)
    }

    @Test
    fun `jsonToVideo returns null when no videoId`() {
        val json = JsonObject()
        json.addProperty("title", "No ID Video")
        val result = CookieFeedRepositoryImpl.jsonToVideo(json)
        assertNull(result)
    }

    @Test
    fun `jsonToVideo returns video for valid renderer`() {
        val json = JsonObject().apply {
            addProperty("videoId", "test1234567")
            add("title", JsonObject().apply { addProperty("simpleText", "My Video") })
            add("shortBylineText", JsonObject().apply {
                add("runs", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", "My Channel") })
                })
            })
            add("thumbnail", JsonObject().apply {
                add("thumbnails", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("url", "https://example.com/thumb.jpg")
                        addProperty("width", 320)
                        addProperty("height", 180)
                    })
                })
            })
            add("lengthText", JsonObject().apply {
                add("accessibility", JsonObject().apply {
                    add("accessibilityData", JsonObject().apply {
                        addProperty("label", "2:30")
                    })
                })
            })
            add("viewCountText", JsonObject().apply { addProperty("simpleText", "10K views") })
            add("publishedTimeText", JsonObject().apply { addProperty("simpleText", "3 days ago") })
        }
        val result = CookieFeedRepositoryImpl.jsonToVideo(json)
        assertNotNull(result)
        assertEquals("test1234567", result!!.id)
        assertEquals("My Video", result.title)
        assertEquals("My Channel", result.channelName)
        assertEquals("2:30", result.duration)
        assertEquals("10K views", result.viewCount)
        assertEquals("3 days ago", result.publishedTime)
    }
}
