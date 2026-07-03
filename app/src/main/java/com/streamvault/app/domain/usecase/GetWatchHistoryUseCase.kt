package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWatchHistoryUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    operator fun invoke(): Flow<List<Video>> {
        return repository.getWatchHistory()
    }
}