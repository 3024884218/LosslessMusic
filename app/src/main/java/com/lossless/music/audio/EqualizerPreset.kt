package com.lossless.music.audio

/**
 * 均衡器预设。
 *
 * 5 段频率:60Hz / 230Hz / 910Hz / 4kHz / 14kHz
 * 增益单位:dB,范围 [-12, +12]
 */
enum class EqualizerPreset(
    val displayName: String,
    val gains: FloatArray
) {
    FLAT("平直", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
    POP("流行", floatArrayOf(-1f, 2f, 5f, 1f, -2f)),
    ROCK("摇滚", floatArrayOf(5f, 3f, -1f, 2f, 4f)),
    CLASSICAL("古典", floatArrayOf(4f, 2f, 0f, 2f, 3f)),
    VOCAL("人声", floatArrayOf(-2f, -1f, 3f, 3f, 1f)),
    BASS_BOOST("低音增强", floatArrayOf(8f, 5f, 0f, 0f, 0f)),
    TREBLE_BOOST("高音增强", floatArrayOf(0f, 0f, 0f, 5f, 8f)),
    LIVE("现场", floatArrayOf(-2f, 0f, 2f, 3f, 3f));

    companion object {
        /** 从保存的增益数组匹配最接近的预设 */
        fun match(gains: FloatArray): EqualizerPreset? {
            return values().firstOrNull { preset ->
                preset.gains.indices.all { i ->
                    i < gains.size && kotlin.math.abs(preset.gains[i] - gains[i]) < 0.1f
                }
            }
        }
    }
}
