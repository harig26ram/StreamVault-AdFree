package com.streamvault.app.domain.usecase

import com.streamvault.app.domain.model.SearchResult
import com.streamvault.app.domain.repository.VideoRepository
import javax.inject.Inject

class SearchUseCase @Inject constructor(
    private val repository: VideoRepository
) {
    suspend operator fun invoke(query: String, continuationToken: String? = null): Result<SearchResult> {
        return repository.search(query, continuationToken)
    }
}