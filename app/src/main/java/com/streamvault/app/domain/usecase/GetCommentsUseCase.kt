package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.Comment
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetCommentsUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(videoId: String): Result<List<Comment>> {
        return repository.getComments(videoId)
    }
}
