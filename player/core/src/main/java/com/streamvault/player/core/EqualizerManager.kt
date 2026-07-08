package com.streamvault.player.core

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

class EqualizerManager {

    companion object {
        private const val TAG = "EqualizerManager"
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private var _sessionId: Int = 0
    val sessionId: Int get() = _sessionId

    private var _enabled: Boolean = false
    val enabled: Boolean get() = _enabled

    fun initialize(sessionId: Int) {
        _sessionId = sessionId
        release()
        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = _enabled
            }
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = _enabled
            }
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = _enabled
            }
            Log.d(TAG, "Initialized with sessionId=$sessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize: ${e.message}", e)
            release()
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled = enabled
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "setEnabled failed: ${e.message}")
        }
    }

    fun getPresets(): List<String> {
        val eq = equalizer ?: return emptyList()
        return (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
    }

    fun getNumberOfBands(): Int {
        return equalizer?.numberOfBands?.toInt() ?: 0
    }

    fun getBandLevelRange(): Pair<Short, Short> {
        val eq = equalizer ?: return Pair(0, 0)
        val range = eq.bandLevelRange
        return Pair(range[0], range[1])
    }

    fun setPreset(presetIndex: Int) {
        try {
            equalizer?.usePreset(presetIndex.toShort())
            Log.d(TAG, "Preset set to $presetIndex")
        } catch (e: Exception) {
            Log.e(TAG, "setPreset failed: ${e.message}")
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            Log.d(TAG, "Band $band level set to $level")
        } catch (e: Exception) {
            Log.e(TAG, "setBandLevel failed: ${e.message}")
        }
    }

    fun getBandLevel(band: Short): Short {
        return try {
            equalizer?.getBandLevel(band) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getCenterFreq(band: Short): Int {
        return try {
            equalizer?.getCenterFreq(band) ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun setBassBoost(strength: Short) {
        try {
            bassBoost?.setStrength(strength)
            Log.d(TAG, "BassBoost set to $strength")
        } catch (e: Exception) {
            Log.e(TAG, "setBassBoost failed: ${e.message}")
        }
    }

    fun setVirtualizer(strength: Short) {
        try {
            virtualizer?.setStrength(strength)
            Log.d(TAG, "Virtualizer set to $strength")
        } catch (e: Exception) {
            Log.e(TAG, "setVirtualizer failed: ${e.message}")
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}
