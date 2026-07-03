package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.Channel
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetChannelInfoUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(channelId: String): Result<Channel> {
        return repository.getChannelInfo(channelId)
    }
}
