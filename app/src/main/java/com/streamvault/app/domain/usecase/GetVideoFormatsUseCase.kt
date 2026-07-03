package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.VideoFormat
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetVideoFormatsUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(videoId: String): Result<List<VideoFormat>> {
        return repository.getVideoFormats(videoId)
    }
}
