package com.lossless.music.audio

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.lossless.music.ui.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音效均衡器管理器。
 *
 * 包装系统 Equalizer + LoudnessEnhancer:
 *  - 默认旁路,保证无损音质
 *  - 用户可调节 5 段 EQ(60Hz/230Hz/910Hz/4kHz/14kHz) 和增益
 *  - 关闭时所有效果器设为 0,原始音频直通
 */
@Singleton
class EqualizerManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var audioSessionId: Int = 0

    /** 是否启用均衡器 */
    var enabled: Boolean = settingsRepository.eqEnabled
        set(value) {
            field = value
            settingsRepository.eqEnabled = value
            apply()
        }

    /** 5 段增益 [-15dB, +15dB] */
    val gains: FloatArray = settingsRepository.eqGains.let { list ->
        if (list.size == BAND_COUNT) FloatArray(BAND_COUNT) { list[it] } else FloatArray(BAND_COUNT) { 0f }
    }

    /** 总增益 (mB, 0 = 不变) */
    var loudnessGain: Int = settingsRepository.loudnessGain
        set(value) {
            field = value.coerceIn(-2000, 6000)
            settingsRepository.loudnessGain = field
            apply()
        }

    /** 关联到 ExoPlayer 的 audioSessionId */
    fun attach(audioSessionId: Int) {
        release()
        this.audioSessionId = audioSessionId
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                // 初始化系统均衡器
                val bands = numberOfBands.toInt()
                for (i in 0 until minOf(bands, BAND_COUNT)) {
            val range = bandLevelRange
            val center = getCenterFreq(i.toShort())
            // 映射到我们的目标频段
            setBandLevel(i.toShort(), (gains[i] * 100).toInt().toShort())
            Log.d("Equalizer", "Band $i: ${center / 1000}Hz, gain=${gains[i]}")
                }
                enabled = this@EqualizerManager.enabled
            }
            loudness = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(if (enabled) loudnessGain else 0)
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "attach failed: ${e.message}")
        }
    }

    fun release() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
            equalizer = null
            loudness?.release()
            loudness = null
        } catch (_: Exception) {}
        audioSessionId = 0
    }

    /** 设置某个频段增益 */
    fun setBandGain(index: Int, gainDb: Float) {
        if (index !in gains.indices) return
        gains[index] = gainDb.coerceIn(-15f, 15f)
        settingsRepository.eqGains = gains.toList()
        apply()
    }

    /** 应用预设 */
    fun applyPreset(preset: com.lossless.music.audio.EqualizerPreset) {
        for (i in gains.indices) {
            gains[i] = if (i < preset.gains.size) preset.gains[i] else 0f
        }
        settingsRepository.eqGains = gains.toList()
        enabled = true
        apply()
    }

    /** 获取当前匹配的预设(用于 UI 高亮) */
    fun getCurrentPreset(): com.lossless.music.audio.EqualizerPreset? =
        com.lossless.music.audio.EqualizerPreset.match(gains)

    /** 应用当前设置到效果器 */
    private fun apply() {
        val eq = equalizer ?: return
        try {
            val shouldEnable = enabled && (gains.any { it != 0f } || loudnessGain != 0)
            eq.enabled = shouldEnable
            for (i in 0 until minOf(eq.numberOfBands.toInt(), BAND_COUNT)) {
                eq.setBandLevel(i.toShort(), (gains[i] * 100).toInt().toShort())
            }
            loudness?.setTargetGain(if (enabled) loudnessGain else 0)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "apply failed: ${e.message}")
        }
    }

    companion object {
        const val BAND_COUNT = 5
    }
}
