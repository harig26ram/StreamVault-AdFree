package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.Channel
import com.streamvault.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSubscriptionsUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    operator fun invoke(): Flow<List<Channel>> {
        return repository.getSubscriptionsList()
    }
}