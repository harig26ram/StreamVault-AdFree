package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.auth.AuthManager
import com.streamvault.app.auth.CookieStore
import com.streamvault.app.auth.UserProfile
import com.streamvault.app.data.local.SettingsManager
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.usecase.ClearWatchHistoryUseCase
import com.streamvault.app.domain.usecase.GetWatchHistoryUseCase
import com.streamvault.app.presentation.ui.theme.Theme
import com.streamvault.app.presentation.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDarkMode: Boolean = true,
    val isAmoledMode: Boolean = true,
    val videoQuality: String = "Auto",
    val autoplay: Boolean = true,
    val swipeBrightness: Boolean = true,
    val sponsorBlock: Boolean = false,
    val miniPlayer: Boolean = true,
    val backgroundPlay: Boolean = true,
    val gestureControls: Boolean = true,
    val pinchToZoom: Boolean = true,
    val skipSilence: Boolean = false,
    val rememberPlayback: Boolean = true,
    val defaultTab: String = "home",
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: Int = -1,
    val userProfile: UserProfile? = null,
    val watchHistory: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val showClearHistoryDialog: Boolean = false,
    val showAboutDialog: Boolean = false,
    val showQualityDialog: Boolean = false,
    val showDefaultTabDialog: Boolean = false,
    val showEqualizerPresetDialog: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getWatchHistoryUseCase: GetWatchHistoryUseCase,
    private val clearWatchHistoryUseCase: ClearWatchHistoryUseCase,
    private val settingsManager: SettingsManager,
    private val authManager: AuthManager,
    private val themeManager: ThemeManager,
    val cookieStore: CookieStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val selectedTheme: StateFlow<Theme> = themeManager.selectedTheme

    fun selectTheme(theme: Theme) {
        themeManager.setTheme(theme)
    }

    init {
        loadSettings()
        loadWatchHistory()
    }

    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            isDarkMode = settingsManager.isDarkMode,
            isAmoledMode = settingsManager.isAmoledMode,
            videoQuality = settingsManager.videoQuality,
            autoplay = settingsManager.autoplay,
            swipeBrightness = settingsManager.swipeBrightness,
            sponsorBlock = settingsManager.sponsorBlock,
            miniPlayer = settingsManager.miniPlayer,
            backgroundPlay = settingsManager.backgroundPlay,
            gestureControls = settingsManager.gestureControls,
            pinchToZoom = settingsManager.pinchToZoom,
            skipSilence = settingsManager.skipSilence,
            rememberPlayback = settingsManager.rememberPlayback,
            defaultTab = settingsManager.defaultTab,
            equalizerEnabled = settingsManager.equalizerEnabled,
            equalizerPreset = settingsManager.equalizerPreset,
            userProfile = authManager.getUserProfile()
        )
    }

    private fun loadWatchHistory() {
        viewModelScope.launch {
            getWatchHistoryUseCase().collect { history ->
                _uiState.value = _uiState.value.copy(watchHistory = history)
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        settingsManager.isDarkMode = enabled
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
    }

    fun setAmoledMode(enabled: Boolean) {
        settingsManager.isAmoledMode = enabled
        _uiState.value = _uiState.value.copy(isAmoledMode = enabled)
    }

    fun setVideoQuality(quality: String) {
        settingsManager.videoQuality = quality
        _uiState.value = _uiState.value.copy(videoQuality = quality)
    }

    fun setAutoplay(enabled: Boolean) {
        settingsManager.autoplay = enabled
        _uiState.value = _uiState.value.copy(autoplay = enabled)
    }

    fun setSwipeBrightness(enabled: Boolean) {
        settingsManager.swipeBrightness = enabled
        _uiState.value = _uiState.value.copy(swipeBrightness = enabled)
    }

    fun setSponsorBlock(enabled: Boolean) {
        settingsManager.sponsorBlock = enabled
        _uiState.value = _uiState.value.copy(sponsorBlock = enabled)
    }

    fun setMiniPlayer(enabled: Boolean) {
        settingsManager.miniPlayer = enabled
        _uiState.value = _uiState.value.copy(miniPlayer = enabled)
    }

    fun setBackgroundPlay(enabled: Boolean) {
        settingsManager.backgroundPlay = enabled
        _uiState.value = _uiState.value.copy(backgroundPlay = enabled)
    }

    fun setGestureControls(enabled: Boolean) {
        settingsManager.gestureControls = enabled
        _uiState.value = _uiState.value.copy(gestureControls = enabled)
    }

    fun setPinchToZoom(enabled: Boolean) {
        settingsManager.pinchToZoom = enabled
        _uiState.value = _uiState.value.copy(pinchToZoom = enabled)
    }

    fun setSkipSilence(enabled: Boolean) {
        settingsManager.skipSilence = enabled
        _uiState.value = _uiState.value.copy(skipSilence = enabled)
    }

    fun setRememberPlayback(enabled: Boolean) {
        settingsManager.rememberPlayback = enabled
        _uiState.value = _uiState.value.copy(rememberPlayback = enabled)
    }

    fun setDefaultTab(tab: String) {
        settingsManager.defaultTab = tab
        _uiState.value = _uiState.value.copy(defaultTab = tab)
    }

    fun showClearHistoryDialog() {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = true)
    }

    fun dismissClearHistoryDialog() {
        _uiState.value = _uiState.value.copy(showClearHistoryDialog = false)
    }

    fun showAboutDialog() {
        _uiState.value = _uiState.value.copy(showAboutDialog = true)
    }

    fun dismissAboutDialog() {
        _uiState.value = _uiState.value.copy(showAboutDialog = false)
    }

    fun showQualityDialog() {
        _uiState.value = _uiState.value.copy(showQualityDialog = true)
    }

    fun dismissQualityDialog() {
        _uiState.value = _uiState.value.copy(showQualityDialog = false)
    }

    fun showDefaultTabDialog() {
        _uiState.value = _uiState.value.copy(showDefaultTabDialog = true)
    }

    fun dismissDefaultTabDialog() {
        _uiState.value = _uiState.value.copy(showDefaultTabDialog = false)
    }

    fun showEqualizerPresetDialog() {
        _uiState.value = _uiState.value.copy(showEqualizerPresetDialog = true)
    }

    fun dismissEqualizerPresetDialog() {
        _uiState.value = _uiState.value.copy(showEqualizerPresetDialog = false)
    }

    fun setEqualizerPreset(preset: Int) {
        settingsManager.equalizerPreset = preset
        _uiState.value = _uiState.value.copy(equalizerPreset = preset, showEqualizerPresetDialog = false)
    }

    fun signOut() {
        authManager.signOut()
    }

    fun prepareAccountSwitch() {
        authManager.prepareAccountSwitch()
    }

    fun getSignInIntent(): android.content.Intent = authManager.getSignInIntent()

    fun completeSignIn(task: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>) {
        authManager.completeSignIn(task, viewModelScope)
    }

    fun refreshUserProfile() {
        _uiState.value = _uiState.value.copy(userProfile = authManager.getUserProfile())
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            clearWatchHistoryUseCase()
            _uiState.value = _uiState.value.copy(
                watchHistory = emptyList(),
                showClearHistoryDialog = false
            )
        }
    }

    fun connectCookies(cookies: String, sapisid: String) {
        viewModelScope.launch {
            cookieStore.save(cookies, sapisid)
        }
    }

    fun disconnectCookies() {
        viewModelScope.launch {
            cookieStore.clear()
        }
    }
}
