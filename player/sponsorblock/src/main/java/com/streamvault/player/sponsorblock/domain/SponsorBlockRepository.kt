package com.streamvault.player.sponsorblock.domain

import com.streamvault.player.sponsorblock.data.SponsorBlockApi
import com.streamvault.player.sponsorblock.data.SponsorSegment
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SponsorBlockRepository {

    private val ALL_CATEGORIES = listOf(
        "sponsor", "intro", "outro", "selfpromo",
        "interaction", "music_offtopic", "preview", "filler"
    )

    private val api: SponsorBlockApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SponsorBlockApi::class.java)
    }

    suspend fun getSegments(videoId: String): Result<List<SponsorSegment>> {
        return try {
            val segments = api.getSegments(videoId, ALL_CATEGORIES.joinToString(","))
            Result.success(segments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun skipSegmentsForDuration(
        videoId: String,
        duration: Double
    ): Result<List<SponsorSegment>> {
        return getSegments(videoId).map { segments ->
            segments.filter { kotlin.math.abs(it.videoDuration - duration) < 1.0 }
        }
    }

    companion object {
        const val BASE_URL = "https://sponsor.ajay.app/"
    }
}
