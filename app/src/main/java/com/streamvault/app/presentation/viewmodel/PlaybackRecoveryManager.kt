package com.streamvault.app.presentation.viewmodel

import android.util.Log
import com.streamvault.player.core.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Manages playback error recovery with exponential backoff, client rotation,
 * and quality degradation.
 *
 * Recovery flow:
 * Level 1: Silent auto-retry (same URL, 0ms delay, max 2 attempts)
 * Level 2: Re-fetch formats (fresh URLs, max 1 attempt)
 * Level 3: Client rotation (next client in chain, max 1 rotation)
 * Level 4: Quality degradation + user error (all failed)
 */
class PlaybackRecoveryManager {

    companion object {
        private const val TAG = "RecoveryMgr"
        private const val MAX_RETRY_ATTEMPTS = 6
        private val BACKOFF_DELAYS = longArrayOf(0L, 1000L, 2000L, 4000L, 8000L, 16000L)
    }

    enum class RecoveryAction {
        RETRY_SAME_URL,
        REFETCH_FORMATS,
        ROTATE_CLIENT,
        DEGRADE_QUALITY,
        SHOW_ERROR
    }

    data class RecoveryState(
        val attempt: Int = 0,
        val isRecovering: Boolean = false,
        val lastError: String? = null,
        val recoveryMessage: String? = null,
        val currentClientIndex: Int = 0
    )

    private val _state = MutableStateFlow(RecoveryState())
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var retryJob: Job? = null

    /**
     * Called when a playback error occurs. Returns the recovery action to take.
     */
    fun handleError(error: PlayerState.Error): RecoveryAction {
        val currentAttempt = _state.value.attempt
        val errorMessage = error.message ?: "Unknown error"

        Log.w(TAG, "handleError: attempt=$currentAttempt, error=$errorMessage")

        val isRecoverable = isRecoverableError(error)

        if (!isRecoverable || currentAttempt >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Non-recoverable or max attempts reached: showing error")
            _state.value = _state.value.copy(
                isRecovering = false,
                lastError = errorMessage,
                recoveryMessage = null
            )
            return RecoveryAction.SHOW_ERROR
        }

        val action = when {
            currentAttempt < 2 -> {
                Log.d(TAG, "Level 1: Retrying same URL (attempt ${currentAttempt + 1})")
                _state.value = _state.value.copy(
                    attempt = currentAttempt + 1,
                    isRecovering = true,
                    recoveryMessage = "Retrying..."
                )
                RecoveryAction.RETRY_SAME_URL
            }
            currentAttempt == 2 -> {
                Log.d(TAG, "Level 2: Re-fetching formats")
                _state.value = _state.value.copy(
                    attempt = currentAttempt + 1,
                    isRecovering = true,
                    recoveryMessage = "Refreshing streams..."
                )
                RecoveryAction.REFETCH_FORMATS
            }
            currentAttempt == 3 -> {
                Log.d(TAG, "Level 3: Rotating client")
                _state.value = _state.value.copy(
                    attempt = currentAttempt + 1,
                    isRecovering = true,
                    currentClientIndex = _state.value.currentClientIndex + 1,
                    recoveryMessage = "Trying different source..."
                )
                RecoveryAction.ROTATE_CLIENT
            }
            else -> {
                Log.d(TAG, "Level 4: Degrading quality")
                _state.value = _state.value.copy(
                    attempt = currentAttempt + 1,
                    isRecovering = true,
                    recoveryMessage = "Lowering quality..."
                )
                RecoveryAction.DEGRADE_QUALITY
            }
        }

        return action
    }

    /**
     * Schedules the next retry after a delay.
     */
    fun scheduleRetry(action: () -> Unit) {
        retryJob?.cancel()
        val delay = BACKOFF_DELAYS[(_state.value.attempt - 1).coerceIn(0, BACKOFF_DELAYS.size - 1)]
        Log.d(TAG, "Scheduling retry in ${delay}ms")
        retryJob = scope.launch {
            delay(delay)
            action()
        }
    }

    /**
     * Resets the recovery state (e.g., on successful playback or user manual retry).
     */
    fun reset() {
        retryJob?.cancel()
        _state.value = RecoveryState()
        Log.d(TAG, "Recovery state reset")
    }

    /**
     * Increments client index for rotation.
     */
    fun advanceClient() {
        _state.value = _state.value.copy(
            currentClientIndex = _state.value.currentClientIndex + 1
        )
    }

    /**
     * Returns the current client index for the fallback chain.
     */
    fun getCurrentClientIndex(): Int = _state.value.currentClientIndex

    /**
     * Determines if an error is recoverable (403, network timeout, etc.)
     */
    private fun isRecoverableError(error: PlayerState.Error): Boolean {
        val msg = error.message?.lowercase() ?: return false
        return when {
            msg.contains("403") -> true
            msg.contains("forbidden") -> true
            msg.contains("timeout") -> true
            msg.contains("unable to connect") -> true
            msg.contains("connection refused") -> true
            msg.contains("connection timed out") -> true
            msg.contains("datasourceexception") -> true
            msg.contains("httpdatasource") -> true
            msg.contains("cleartext") -> false
            msg.contains("ssl") -> false
            msg.contains("player not initialized") -> false
            msg.contains("no stream urls") -> false
            else -> true
        }
    }

    fun release() {
        retryJob?.cancel()
        scope.cancel()
    }
}
