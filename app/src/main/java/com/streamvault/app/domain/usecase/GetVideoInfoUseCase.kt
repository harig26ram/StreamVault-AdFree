package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetVideoInfoUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(videoId: String): Result<Video> {
        return repository.getVideoInfo(videoId)
    }
}