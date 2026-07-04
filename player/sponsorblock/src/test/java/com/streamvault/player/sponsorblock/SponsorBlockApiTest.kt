package com.streamvault.player.sponsorblock

import com.streamvault.player.sponsorblock.data.SponsorBlockApi
import com.streamvault.player.sponsorblock.data.SponsorSegment
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SponsorBlockApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: SponsorBlockApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SponsorBlockApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parse single segment response`() = runTest {
        server.enqueue(
            MockResponse().setBody("""
                [{"segment":[10.5,15.2],"category":"sponsor","UUID":"abc123","videoDuration":300.0}]
            """.trimIndent())
        )
        val segments = api.getSegments("test123", "sponsor")
        assertEquals(1, segments.size)
        assertEquals(listOf(10.5, 15.2), segments[0].segment)
        assertEquals("sponsor", segments[0].category)
        assertEquals("abc123", segments[0].UUID)
        assertEquals(300.0, segments[0].videoDuration, 0.001)
    }

    @Test
    fun `parse empty array response`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))
        val segments = api.getSegments("empty_test", "sponsor")
        assertTrue(segments.isEmpty())
    }

    @Test
    fun `parse multiple segments response`() = runTest {
        server.enqueue(
            MockResponse().setBody("""
                [
                    {"segment":[30.0,45.0],"category":"intro","UUID":"def456","videoDuration":600.0},
                    {"segment":[120.0,130.0],"category":"sponsor","UUID":"ghi789","videoDuration":600.0},
                    {"segment":[580.0,595.0],"category":"outro","UUID":"jkl012","videoDuration":600.0}
                ]
            """.trimIndent())
        )
        val segments = api.getSegments("multi_test", "sponsor,intro,outro")
        assertEquals(3, segments.size)
        assertEquals("intro", segments[0].category)
        assertEquals("sponsor", segments[1].category)
        assertEquals("outro", segments[2].category)
    }

    @Test
    fun `segment with different categories`() = runTest {
        server.enqueue(
            MockResponse().setBody("""
                [
                    {"segment":[5.0,10.0],"category":"selfpromo","UUID":"mno345","videoDuration":200.0},
                    {"segment":[50.0,60.0],"category":"interaction","UUID":"pqr678","videoDuration":200.0},
                    {"segment":[180.0,190.0],"category":"music_offtopic","UUID":"stu901","videoDuration":200.0}
                ]
            """.trimIndent())
        )
        val segments = api.getSegments("cat_test", "selfpromo,interaction,music_offtopic")
        assertEquals(3, segments.size)
        assertEquals("selfpromo", segments[0].category)
        assertEquals("interaction", segments[1].category)
        assertEquals("music_offtopic", segments[2].category)
    }

    @Test(expected = retrofit2.HttpException::class)
    fun `error response throws exception`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        api.getSegments("error_test", "sponsor")
    }
}
