package com.freedomplay.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freedomplay.app.data.local.preferences.PreferencesManager
import com.freedomplay.app.data.manager.InstanceManager
import com.freedomplay.app.presentation.ui.theme.ThemeType
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
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val instanceManager: InstanceManager
) : ViewModel() {

    val themeType = preferencesManager.theme
        .map { try { ThemeType.valueOf(it) } catch (_: Exception) { ThemeType.AMOLED } }
        .catch { emit(ThemeType.AMOLED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeType.AMOLED)

    val defaultQuality = preferencesManager.defaultQuality
        .catch { emit("720p") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "720p")

    val skipSilence = preferencesManager.skipSilence
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val audioOnly = preferencesManager.audioOnlyMode
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rememberPosition = preferencesManager.rememberPosition
        .catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultDownloadQuality = preferencesManager.downloadQuality
        .catch { emit("720p") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "720p")

    val volumeNormalization = preferencesManager.volumeNormalization
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pipedInstanceUrl = preferencesManager.pipedInstanceUrl
        .catch { emit("https://pipedapi.kavin.rocks/") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://pipedapi.kavin.rocks/")

    val signedIn = preferencesManager.hasYouTubeCookies
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _instanceHealth = MutableStateFlow<Map<String, String>>(emptyMap())
    val instanceHealth: StateFlow<Map<String, String>> = _instanceHealth.asStateFlow()

    init {
        viewModelScope.launch {
            instanceManager.initialize()
            loadInstanceHealth()
        }
    }

    fun loadInstanceHealth() {
        _instanceHealth.value = instanceManager.getDebugInfo()
    }

    fun resetInstanceHealth() {
        _instanceHealth.value.keys.forEach { url ->
            instanceManager.resetInstance(url)
        }
        loadInstanceHealth()
    }

    fun saveYouTubeCookies(cookies: String?) {
        viewModelScope.launch { preferencesManager.setYouTubeCookies(cookies) }
    }

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

    fun setVolumeNormalization(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setVolumeNormalization(enabled) }
    }

    fun setPipedInstanceUrl(url: String) {
        viewModelScope.launch { preferencesManager.setPipedInstanceUrl(url) }
    }
}
