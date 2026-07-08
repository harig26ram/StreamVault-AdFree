package com.streamvault.player.core

import android.util.Log

object EqualizerManagerHolder {
    private const val TAG = "EqualizerManagerHolder"

    @Volatile
    var equalizerManager: EqualizerManager? = null
        private set

    @Volatile
    var audioSessionId: Int = 0
        private set

    fun register(manager: EqualizerManager, sessionId: Int) {
        equalizerManager = manager
        audioSessionId = sessionId
        Log.d(TAG, "Registered EqualizerManager with sessionId=$sessionId")
    }

    fun unregister() {
        equalizerManager = null
        audioSessionId = 0
        Log.d(TAG, "Unregistered EqualizerManager")
    }
}
