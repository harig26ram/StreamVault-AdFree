package com.streamvault.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.streamvault.app.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    fun getAuthManager(): AuthManager = authManager
}
