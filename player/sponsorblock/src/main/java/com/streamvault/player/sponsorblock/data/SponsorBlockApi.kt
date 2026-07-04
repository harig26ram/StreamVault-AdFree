package com.streamvault.player.sponsorblock.data

import retrofit2.http.GET
import retrofit2.http.Query

interface SponsorBlockApi {

    @GET("api/skipSegments")
    suspend fun getSegments(
        @Query("videoID") videoId: String,
        @Query("category") categories: String
    ): List<SponsorSegment>
}
