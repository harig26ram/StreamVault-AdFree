package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.streamvault.app.auth.AuthManager
import com.streamvault.app.cast.CastPlayer
import com.streamvault.app.cast.CastSessionManager
import com.streamvault.app.presentation.ui.components.MiniPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val castSessionManager: CastSessionManager,
    private val castPlayer: CastPlayer,
    val miniPlayerManager: MiniPlayerManager
) : ViewModel() {

    fun getAuthManager(): AuthManager = authManager

    fun getCastSessionManager(): CastSessionManager = castSessionManager

    fun getCastPlayer(): CastPlayer = castPlayer
}
