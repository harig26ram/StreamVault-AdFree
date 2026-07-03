package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.usecase.GetChannelInfoUseCase
import com.streamvault.app.domain.usecase.SubscribeUseCase
import com.streamvault.app.domain.usecase.UnsubscribeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelUiState(
    val channel: com.streamvault.app.domain.model.Channel? = null,
    val videos: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSubscribed: Boolean = false
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getChannelInfoUseCase: GetChannelInfoUseCase,
    private val subscribeUseCase: SubscribeUseCase,
    private val unsubscribeUseCase: UnsubscribeUseCase
) : ViewModel() {

    private val channelId: String = savedStateHandle["channelId"] ?: ""

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    init {
        if (channelId.isNotEmpty()) {
            loadChannel()
        }
    }

    private fun loadChannel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = getChannelInfoUseCase(channelId)
            result.fold(
                onSuccess = { channel ->
                    _uiState.value = _uiState.value.copy(
                        channel = channel,
                        videos = channel.videos.map { FeedItem.Video(it) },
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

    fun subscribe() {
        viewModelScope.launch {
            subscribeUseCase(channelId)
            _uiState.value = _uiState.value.copy(isSubscribed = true)
        }
    }

    fun unsubscribe() {
        viewModelScope.launch {
            unsubscribeUseCase(channelId)
            _uiState.value = _uiState.value.copy(isSubscribed = false)
        }
    }
}
