package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.HomeFeed
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetHomeFeedUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(continuationToken: String? = null): Result<HomeFeed> {
        return repository.getHomeFeed(continuationToken)
    }
}