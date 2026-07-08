package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

open class AddToWatchLaterUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    open suspend operator fun invoke(video: Video) {
        repository.addToWatchLater(video)
    }
}
