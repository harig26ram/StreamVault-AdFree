package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.HomeFeed
import com.streamvault.app.domain.usecase.GetHomeFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val feedItems: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val continuationToken: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeFeedUseCase: GetHomeFeedUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadHomeFeed()
    }

    fun loadHomeFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            getHomeFeedUseCase().fold(
                onSuccess = { feed ->
                    _uiState.value = _uiState.value.copy(
                        feedItems = feed.items,
                        continuationToken = feed.continuationToken,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            )
        }
    }

    fun loadMore() {
        val token = _uiState.value.continuationToken ?: return
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            getHomeFeedUseCase(token).fold(
                onSuccess = { feed ->
                    val existingIds = _uiState.value.feedItems.filterIsInstance<FeedItem.Video>()
                        .map { it.video.id }.toSet()
                    val newItems = feed.items.filter { item ->
                        when (item) {
                            is FeedItem.Video -> item.video.id !in existingIds
                            else -> true
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        feedItems = _uiState.value.feedItems + newItems,
                        continuationToken = feed.continuationToken,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            getHomeFeedUseCase().fold(
                onSuccess = { feed ->
                    _uiState.value = _uiState.value.copy(
                        feedItems = feed.items,
                        continuationToken = feed.continuationToken,
                        isRefreshing = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isRefreshing = false
                    )
                }
            )
        }
    }
}