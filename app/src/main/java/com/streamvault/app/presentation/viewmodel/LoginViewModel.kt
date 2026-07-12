package com.streamvault.app.presentation.viewmodel

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.auth.AuthManager
import com.streamvault.app.auth.AuthState
import com.streamvault.app.auth.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val userProfile: UserProfile? = null,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val authState: StateFlow<AuthState> = authManager.authState

    init {
        // Check current state
        val profile = authManager.userProfile.value
        if (profile != null) {
            _uiState.value = LoginUiState(
                isAuthenticated = true,
                userProfile = profile
            )
        }
    }

    fun getSignInIntent(): Intent {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        return authManager.getSignInIntent()
    }

    fun handleSignInResult(task: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>) {
        authManager.handleSignInResult(task, viewModelScope)
        val state = authManager.authState.value
        when (state) {
            is AuthState.Authenticated -> {
                _uiState.value = LoginUiState(
                    isAuthenticated = true,
                    userProfile = state.profile
                )
            }
            is AuthState.Error -> {
                _uiState.value = LoginUiState(
                    error = state.message
                )
            }
            else -> {
                _uiState.value = LoginUiState(
                    error = "Sign-in failed"
                )
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        _uiState.value = LoginUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, isLoading = false)
    }
}
