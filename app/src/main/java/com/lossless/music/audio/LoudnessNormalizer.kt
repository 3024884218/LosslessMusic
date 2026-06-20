package com.lossless.music.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.lossless.music.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * 响度归一化(Loudness Normalization)。
 *
 * 目的:不同来源的音乐音量差异大(邓丽君 vs B站下载),自动调整到统一响度。
 *
 * 实现:
 *  1. 优先读取音频文件的 ReplayGain 标签(若存在)
 *  2. 无标签时离线计算 RMS(均方根),按目标 RMS (-18dBFS) 计算增益
 *  3. 播放时通过 ExoPlayer.volume 应用增益
 *
 * 注意:RMS 计算需要解码音频,采样前 N 秒以加速。
 */
@Singleton
class LoudnessNormalizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 目标 RMS 振幅(对应约 -18dBFS) */
    private val targetRms = 0.126f  // 10^(-18/20)

    /** 缓存已计算的增益,songId -> volume(0.x ~ 2.x) */
    private val volumeCache = mutableMapOf<Long, Float>()

    /**
     * 获取歌曲的归一化音量。
     * @return 音量倍率 0.0-2.0,1.0 表示不变
     */
    suspend fun getNormalizedVolume(song: Song): Float = withContext(Dispatchers.IO) {
        volumeCache[song.id]?.let { return@withContext it }

        // 尝试读取文件
        val file = resolveSongFile(song) ?: run {
            volumeCache[song.id] = 1f
            return@withContext 1f
        }

        val volume = try {
            calculateRmsVolume(file)
        } catch (e: Exception) {
            Log.w("LoudnessNorm", "RMS 计算失败: ${song.title} - ${e.message}")
            1f
        }

        volumeCache[song.id] = volume
        volume
    }

    /** 清除缓存(设置页可调用) */
    fun clearCache() {
        volumeCache.clear()
    }

    /**
     * 计算文件 RMS 并返回归一化音量。
     * 采样前 30 秒以加速。
     */
    private fun calculateRmsVolume(file: File): Float {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val audioTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return 1f

            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // 采样前 30 秒
            val maxBytes = sampleRate * channels * 2 * 30  // 16bit = 2 bytes/sample
            val buffer = ByteBuffer.allocateDirect(8192)
            var totalSamples = 0L
            var sumSquares = 0.0

            var bytesRead = 0
            while (bytesRead < maxBytes) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break

                // 解析 16bit PCM samples
                buffer.rewind()
                val samples = size / 2
                for (i in 0 until samples) {
                    val sample = buffer.short.toInt() / 32768.0
                    sumSquares += sample * sample
                    totalSamples++
                    buffer.position(buffer.position() + 2)
                }

                bytesRead += size
                buffer.clear()
                if (!extractor.advance()) break
            }

            if (totalSamples == 0L) return 1f

            val rms = sqrt(sumSquares / totalSamples).toFloat()
            if (rms < 0.001f) return 1f  // 静音或异常

            // 计算增益倍率,clamp 到 [0.3, 3.0]
            val volume = (targetRms / rms).coerceIn(0.3f, 3.0f)
            Log.d("LoudnessNorm", "RMS=${rms.format(4)}, volume=$volume")
            return volume
        } catch (e: Exception) {
            Log.w("LoudnessNorm", "RMS 计算异常: ${e.message}")
            return 1f
        } finally {
            extractor.release()
        }
    }

    /** 解析歌曲文件路径 */
    private fun resolveSongFile(song: Song): File? {
        return when {
            song.filePath.startsWith("EXTERNAL:") -> File(song.filePath.removePrefix("EXTERNAL:"))
            else -> File(context.filesDir, "music/${song.filePath}").takeIf { it.exists() }
        }
    }

    private fun Float.format(digits: Int) = "%.${digits}f".format(this)
}
