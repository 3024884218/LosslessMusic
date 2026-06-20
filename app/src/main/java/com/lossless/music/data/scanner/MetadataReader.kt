package com.lossless.music.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.lossless.music.data.local.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频元数据读取器。
 *
 * 使用 Android 系统的 MediaMetadataRetriever 读取 ID3/FLAC 标签:
 *  - 标题、艺术家、专辑、年份、流派
 *  - 时长、采样率、声道数、比特率
 *  - 内嵌封面(提取后保存为图片文件)
 *
 * 注意:MediaMetadataRetriever 无法直接读取位深(bitDepth),
 * 此处根据格式推断(FLAC/WAV 通常 16/24bit,MP3 为 0)。
 * 精确位深需解析文件头,后续可扩展。
 */
@Singleton
class MetadataReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * 从 Uri 读取元数据(导入前调用)。
     * @param sourceUri 用户选择的文件 Uri(SAF)
     * @param fileName 文件名(用于标题回退)
     * @return 解析后的元数据,失败字段为默认值
     */
    fun readFromUri(sourceUri: Uri, fileName: String): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, sourceUri)
            readMetadata(retriever, fileName)
        } catch (e: Exception) {
            // 解析失败,返回基于文件名的最小元数据
            AudioMetadata(
                title = fileName.substringBeforeLast('.'),
                format = fileName.substringAfterLast('.', "").lowercase()
            )
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * 从本地文件读取元数据(已复制到私有目录后调用)。
     */
    fun readFromFile(file: File): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            readMetadata(retriever, file.name)
        } catch (e: Exception) {
            AudioMetadata(
                title = file.nameWithoutExtension,
                format = file.extension.lowercase()
            )
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** 提取内嵌封面并保存为图片文件,返回封面文件路径 */
    fun extractCoverToFile(sourceFile: File, coverDir: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(sourceFile.absolutePath)
            val art = retriever.embeddedPicture ?: return null
            if (!coverDir.exists()) coverDir.mkdirs()
            val coverFile = File(coverDir, sourceFile.nameWithoutExtension + ".jpg")
            coverFile.writeBytes(art)
            coverFile.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun readMetadata(retriever: MediaMetadataRetriever, fileName: String): AudioMetadata {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?: fileName.substringBeforeLast('.')
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: "未知艺术家"
        val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            ?: "未知专辑"
        val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0
        val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 44100
        val channels = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.toIntOrNull() ?: 2
        val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { it.toInt() / 1000 } ?: 0

        // 位深推断:FLAC/WAV/ALAC 有可能 16/24bit,MP3/AAC 为有损填 0
        val bitDepth = when (ext) {
            "flac", "wav", "ape", "alac", "aiff" -> {
                // 精确解析需要读文件头,这里先按采样率/比特率推断
                // 实际无损文件后续可用专门解析库精确读取
                if (bitRate >= 1411 || sampleRate >= 96000) 24 else 16
            }
            else -> 0
        }

        return AudioMetadata(
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre,
            durationMs = durationMs,
            format = ext,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            bitRate = bitRate,
            channels = channels
        )
    }
}

/**
 * 解析后的音频元数据(中间结构,转成 SongEntity 前使用)。
 */
data class AudioMetadata(
    val title: String,
    val artist: String = "未知艺术家",
    val album: String = "未知专辑",
    val year: Int = 0,
    val genre: String = "",
    val durationMs: Long = 0L,
    val format: String = "",
    val sampleRate: Int = 44100,
    val bitDepth: Int = 0,
    val bitRate: Int = 0,
    val channels: Int = 2,
    val fileSize: Long = 0L
)
