package com.freedomplay.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomplay.app.data.local.db.WatchHistoryDao
import com.freedomplay.app.data.repository.StreamRepository
import com.freedomplay.app.domain.model.MusicSection
import com.freedomplay.app.domain.model.StreamItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: StreamRepository,
    watchHistoryDao: WatchHistoryDao
) : ViewModel() {

    private val _homeSections = MutableStateFlow<List<MusicSection>>(emptyList())
    val homeSections: StateFlow<List<MusicSection>> = _homeSections.asStateFlow()

    private val _exploreSections = MutableStateFlow<List<MusicSection>>(emptyList())
    val exploreSections: StateFlow<List<MusicSection>> = _exploreSections.asStateFlow()

    /** Library = locally recorded listening history (newest first). */
    val libraryItems: StateFlow<List<StreamItem>> = watchHistoryDao.getAll()
        .map { entries ->
            entries.map { e ->
                StreamItem(
                    url = "/watch?v=${e.videoId}",
                    videoId = e.videoId,
                    title = e.title,
                    thumbnail = e.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${e.videoId}/hqdefault.jpg" },
                    uploaderName = e.channelName,
                    uploaderUrl = null,
                    uploaderAvatar = null,
                    views = 0,
                    duration = e.duration,
                    uploadedDate = null,
                    uploaded = null
                )
            }
        }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadMusicHome() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.getMusicSections()
                .onSuccess { sections ->
                    _homeSections.value = sections
                    _isLoading.value = false
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to load music"
                    _isLoading.value = false
                }
        }
    }

    fun loadMusicExplore() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repository.getMusicExploreSections()
                .onSuccess { sections ->
                    _exploreSections.value = sections
                    _isLoading.value = false
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to load explore"
                    _isLoading.value = false
                }
        }
    }
}
