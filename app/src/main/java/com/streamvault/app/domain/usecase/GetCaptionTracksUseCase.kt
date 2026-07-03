package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.CaptionTrack
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetCaptionTracksUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(videoId: String): Result<List<CaptionTrack>> {
        return repository.getCaptionTracks(videoId)
    }
}
