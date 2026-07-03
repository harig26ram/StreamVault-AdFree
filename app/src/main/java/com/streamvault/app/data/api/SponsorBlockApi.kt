package com.streamvault.app.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class SponsorBlockResponse(
    val skipSegments: List<SponsorSegment>?
)

data class SponsorSegment(
    val segment: List<Double>?,
    val category: String?,
    val actionType: String?
)

interface SponsorBlockApi {
    @GET("skipSegments/{videoId}")
    suspend fun getSkipSegments(
        @Path("videoId") videoId: String,
        @Query("categories") categories: String = "[\"sponsor\",\"intro\",\"outro\",\"selfpromo\",\"interaction\",\"music_offtopic\",\"preview\",\"filler\"]"
    ): SponsorBlockResponse
}
