package com.streamvault.app.cast

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _currentDeviceName = MutableStateFlow<String?>(null)
    val currentDeviceName: StateFlow<String?> = _currentDeviceName.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val listeners = CopyOnWriteArrayList<CastSessionListener>()

    private var castContext: CastContext? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            Log.d(TAG, "Session starting")
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d(TAG, "Session started: ${session.castDevice?.friendlyName}")
            _isConnected.value = true
            _currentDeviceName.value = session.castDevice?.friendlyName
            listeners.forEach { it.onConnected(session.castDevice?.friendlyName) }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Session start failed: error=$error")
            _isConnected.value = false
            _currentDeviceName.value = null
        }

        override fun onSessionEnding(session: CastSession) {
            Log.d(TAG, "Session ending")
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d(TAG, "Session ended: error=$error")
            _isConnected.value = false
            _currentDeviceName.value = null
            listeners.forEach { it.onDisconnected() }
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            Log.d(TAG, "Session resuming")
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.d(TAG, "Session resumed: wasSuspended=$wasSuspended")
            _isConnected.value = true
            _currentDeviceName.value = session.castDevice?.friendlyName
            listeners.forEach { it.onConnected(session.castDevice?.friendlyName) }
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.w(TAG, "Session resume failed: error=$error")
            _isConnected.value = false
            _currentDeviceName.value = null
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            Log.d(TAG, "Session suspended: reason=$reason")
            _isConnected.value = false
            _currentDeviceName.value = null
        }
    }

    init {
        detectAvailability()
    }

    private fun detectAvailability() {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            _isAvailable.value = true
            Log.d(TAG, "Cast SDK available")
        } catch (e: Exception) {
            Log.w(TAG, "Cast SDK not available: ${e.message}")
            _isAvailable.value = false
            castContext = null
        }
    }

    fun addListener(listener: CastSessionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: CastSessionListener) {
        listeners.remove(listener)
    }

    fun startSession(): Boolean {
        val ctx = castContext
        if (ctx == null) {
            Log.d(TAG, "Cast context not available")
            return false
        }
        return try {
            ctx.sessionManager.startSession(Intent())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start Cast session: ${e.message}")
            false
        }
    }

    fun stopSession() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop Cast session: ${e.message}")
        }
        _isConnected.value = false
        _currentDeviceName.value = null
        listeners.forEach { it.onDisconnected() }
        Log.d(TAG, "Cast session stopped")
    }

    interface CastSessionListener {
        fun onConnected(deviceName: String?)
        fun onDisconnected()
        fun onPlaybackFinished() {}
    }

    companion object {
        private const val TAG = "CastSessionMgr"
        const val NAMESPACE = "com.streamvault.app.cast"
        const val MESSAGE_TYPE_KEY = "type"
        const val VIDEO_URL_KEY = "videoUrl"
        const val VIDEO_ID_KEY = "videoId"
        const val PLAY_ACTION = "play"
        const val PAUSE_ACTION = "pause"
        const val SEEK_ACTION = "seek"
        const val STOP_ACTION = "stop"
        const val VOLUME_ACTION = "volume"
        const val STATUS_ACTION = "status"
    }
}
