package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.domain.model.Channel
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.usecase.GetSubscriptionsUseCase
import com.streamvault.app.domain.usecase.SubscribeUseCase
import com.streamvault.app.domain.usecase.UnsubscribeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionsUiState(
    val channels: List<Channel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val subscribeUseCase: SubscribeUseCase,
    private val unsubscribeUseCase: UnsubscribeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    init {
        loadSubscriptions()
    }

    private fun loadSubscriptions() {
        viewModelScope.launch {
            getSubscriptionsUseCase()
                .onStart {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
                .collect { channels ->
                    _uiState.value = _uiState.value.copy(
                        channels = channels,
                        isLoading = false
                    )
                }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch {
            unsubscribeUseCase(channelId)
        }
    }
}
