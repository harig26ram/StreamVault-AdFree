package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetVideoStreamUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(videoId: String): Result<String> {
        return repository.getVideoStreamUrl(videoId)
    }
}