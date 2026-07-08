package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.HomeFeed
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

open class GetHomeFeedUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    open suspend operator fun invoke(continuationToken: String? = null): Result<HomeFeed> {
        return repository.getHomeFeed(continuationToken)
    }
}