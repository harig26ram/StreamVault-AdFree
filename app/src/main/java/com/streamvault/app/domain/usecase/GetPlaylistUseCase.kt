package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.Playlist
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class GetPlaylistUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(playlistId: String): Result<Playlist> {
        return repository.getPlaylist(playlistId)
    }
}
