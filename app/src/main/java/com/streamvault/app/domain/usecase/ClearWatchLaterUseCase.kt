package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class ClearWatchLaterUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke() {
        repository.clearWatchLater()
    }
}
