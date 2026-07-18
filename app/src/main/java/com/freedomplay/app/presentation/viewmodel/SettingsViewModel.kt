package com.freedomplay.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomplay.app.data.local.preferences.PreferencesManager
import com.freedomplay.app.presentation.ui.theme.ThemeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val themeType = preferencesManager.theme
        .map { ThemeType.valueOf(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeType.AMOLED)

    val defaultQuality = preferencesManager.defaultQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "720p")

    val skipSilence = preferencesManager.skipSilence
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val audioOnly = preferencesManager.audioOnlyMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rememberPosition = preferencesManager.rememberPosition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultDownloadQuality = preferencesManager.downloadQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "720p")

    fun setThemeType(type: ThemeType) {
        viewModelScope.launch { preferencesManager.setTheme(type.name) }
    }

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch { preferencesManager.setDefaultQuality(quality) }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setSkipSilence(enabled) }
    }

    fun setAudioOnly(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAudioOnlyMode(enabled) }
    }

    fun setRememberPosition(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setRememberPosition(enabled) }
    }

    fun setDefaultDownloadQuality(quality: String) {
        viewModelScope.launch { preferencesManager.setDownloadQuality(quality) }
    }
}
