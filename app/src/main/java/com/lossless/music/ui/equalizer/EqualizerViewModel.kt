package com.lossless.music.ui.equalizer

import androidx.lifecycle.ViewModel
import com.lossless.music.audio.EqualizerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 均衡器 ViewModel。
 */
@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager
) : ViewModel() {

    private val _enabled = MutableStateFlow(equalizerManager.enabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _gains = MutableStateFlow(equalizerManager.gains.toList())
    val gains: StateFlow<List<Float>> = _gains.asStateFlow()

    private val _loudness = MutableStateFlow(equalizerManager.loudnessGain)
    val loudness: StateFlow<Int> = _loudness.asStateFlow()

    /** 频段标签:60Hz, 230Hz, 910Hz, 4kHz, 14kHz */
    val bandLabels = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")

    fun setEnabled(enabled: Boolean) {
        equalizerManager.enabled = enabled
        _enabled.value = enabled
    }

    fun setGain(index: Int, gain: Float) {
        equalizerManager.setBandGain(index, gain)
        _gains.value = equalizerManager.gains.toList()
    }

    fun setLoudness(gainMb: Int) {
        equalizerManager.loudnessGain = gainMb
        _loudness.value = equalizerManager.loudnessGain
    }

    fun reset() {
        for (i in 0 until EqualizerManager.BAND_COUNT) {
            equalizerManager.setBandGain(i, 0f)
        }
        equalizerManager.loudnessGain = 0
        equalizerManager.enabled = false
        _enabled.value = false
        _gains.value = equalizerManager.gains.toList()
        _loudness.value = 0
    }

    /** 应用预设 */
    fun applyPreset(preset: com.lossless.music.audio.EqualizerPreset) {
        equalizerManager.applyPreset(preset)
        _enabled.value = true
        _gains.value = equalizerManager.gains.toList()
    }

    /** 当前匹配的预设 */
    val currentPreset: com.lossless.music.audio.EqualizerPreset?
        get() = equalizerManager.getCurrentPreset()
}
