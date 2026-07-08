package com.streamvault.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamvault.app.data.local.SettingsManager
import com.streamvault.player.core.EqualizerManager
import com.streamvault.player.core.EqualizerManagerHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EqualizerUiState(
    val enabled: Boolean = false,
    val numberOfBands: Int = 0,
    val bandLevels: Map<Short, Short> = emptyMap(),
    val minBandLevel: Short = 0,
    val maxBandLevel: Short = 0,
    val presetIndex: Int = -1,
    val presets: List<String> = emptyList(),
    val bassBoost: Short = 0,
    val virtualizer: Short = 0,
    val bandFrequencies: List<String> = emptyList(),
    val isPlayerActive: Boolean = false
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val settingsManager: SettingsManager
) : ViewModel() {

    companion object {
        private const val TAG = "EqualizerVM"
    }

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        loadFromHolder()
    }

    private fun loadFromHolder() {
        val eq = EqualizerManagerHolder.equalizerManager
        val sessionId = EqualizerManagerHolder.audioSessionId
        if (eq == null || sessionId == 0) {
            loadFromSettings()
            return
        }

        try {
            val numBands = eq.getNumberOfBands()
            val (minLevel, maxLevel) = eq.getBandLevelRange()
            val presets = eq.getPresets()

            val bandLevels = mutableMapOf<Short, Short>()
            val frequencies = mutableListOf<String>()
            for (i in 0 until numBands) {
                val band = i.toShort()
                bandLevels[band] = eq.getBandLevel(band)
                frequencies.add(getFrequencyLabel(eq, band))
            }

            val savedPreset = settingsManager.equalizerPreset
            val savedBass = settingsManager.bassBoost
            val savedVirtualizer = settingsManager.virtualizer
            val savedBandLevels = parseBandLevels(settingsManager.equalizerBandLevels, numBands)

            _uiState.update {
                it.copy(
                    enabled = settingsManager.equalizerEnabled,
                    numberOfBands = numBands,
                    bandLevels = if (savedBandLevels.isNotEmpty()) savedBandLevels else bandLevels,
                    minBandLevel = minLevel,
                    maxBandLevel = maxLevel,
                    presetIndex = savedPreset,
                    presets = presets,
                    bassBoost = savedBass,
                    virtualizer = savedVirtualizer,
                    bandFrequencies = frequencies,
                    isPlayerActive = true
                )
            }

            applyToManager()
        } catch (e: Exception) {
            Log.e(TAG, "loadFromHolder failed: ${e.message}", e)
            loadFromSettings()
        }
    }

    private fun loadFromSettings() {
        _uiState.update {
            it.copy(
                enabled = settingsManager.equalizerEnabled,
                bassBoost = settingsManager.bassBoost,
                virtualizer = settingsManager.virtualizer
            )
        }
    }

    private fun getFrequencyLabel(eq: EqualizerManager, band: Short): String {
        return try {
            val freqMilliHz = eq.getCenterFreq(band)
            val hz = freqMilliHz / 1000
            if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
        } catch (e: Exception) {
            "Band ${band + 1}"
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled) }
        settingsManager.equalizerEnabled = enabled
        EqualizerManagerHolder.equalizerManager?.setEnabled(enabled)
    }

    fun setBandLevel(band: Short, level: Short) {
        _uiState.update { state ->
            state.copy(
                bandLevels = state.bandLevels.toMutableMap().apply { put(band, level) },
                presetIndex = -1
            )
        }
        EqualizerManagerHolder.equalizerManager?.setBandLevel(band, level)
        saveBandLevels()
        settingsManager.equalizerPreset = -1
    }

    fun setPreset(index: Int) {
        if (index < 0 || index >= _uiState.value.presets.size) return
        val eq = EqualizerManagerHolder.equalizerManager
        eq?.setPreset(index)
        _uiState.update { state ->
            val numBands = state.numberOfBands
            val newLevels = mutableMapOf<Short, Short>()
            for (i in 0 until numBands) {
                newLevels[i.toShort()] = eq?.getBandLevel(i.toShort()) ?: 0
            }
            state.copy(presetIndex = index, bandLevels = newLevels)
        }
        settingsManager.equalizerPreset = index
        saveBandLevels()
    }

    fun setBassBoost(value: Short) {
        _uiState.update { it.copy(bassBoost = value) }
        settingsManager.bassBoost = value
        EqualizerManagerHolder.equalizerManager?.setBassBoost(value)
    }

    fun setVirtualizer(value: Short) {
        _uiState.update { it.copy(virtualizer = value) }
        settingsManager.virtualizer = value
        EqualizerManagerHolder.equalizerManager?.setVirtualizer(value)
    }

    fun reset() {
        val state = _uiState.value
        val zeroLevels = mutableMapOf<Short, Short>()
        for (i in 0 until state.numberOfBands) {
            zeroLevels[i.toShort()] = 0
        }
        _uiState.update {
            it.copy(
                bandLevels = zeroLevels,
                presetIndex = 0,
                bassBoost = 0,
                virtualizer = 0
            )
        }
        EqualizerManagerHolder.equalizerManager?.let { eq ->
            for (i in 0 until state.numberOfBands) {
                eq.setBandLevel(i.toShort(), 0)
            }
            if (state.presets.isNotEmpty()) eq.setPreset(0)
            eq.setBassBoost(0)
            eq.setVirtualizer(0)
        }
        settingsManager.equalizerPreset = 0
        settingsManager.bassBoost = 0
        settingsManager.virtualizer = 0
        saveBandLevels()
    }

    private fun saveBandLevels() {
        val levels = _uiState.value.bandLevels
        val serialized = levels.entries.joinToString(",") { "${it.key}:${it.value}" }
        settingsManager.equalizerBandLevels = serialized
    }

    private fun applyToManager() {
        val state = _uiState.value
        val eq = EqualizerManagerHolder.equalizerManager ?: return
        try {
            eq.setEnabled(state.enabled)
            state.bandLevels.forEach { (band, level) ->
                eq.setBandLevel(band, level)
            }
            if (state.presetIndex >= 0) eq.setPreset(state.presetIndex)
            eq.setBassBoost(state.bassBoost)
            eq.setVirtualizer(state.virtualizer)
        } catch (e: Exception) {
            Log.e(TAG, "applyToManager failed: ${e.message}")
        }
    }

    private fun parseBandLevels(serialized: String, numBands: Int): Map<Short, Short> {
        if (serialized.isBlank()) return emptyMap()
        return try {
            serialized.split(",").associate {
                val parts = it.split(":")
                parts[0].trim().toShort() to parts[1].trim().toShort()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
