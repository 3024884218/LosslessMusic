package com.lossless.music.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.content.res.AssetFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import com.lossless.music.data.local.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assets 音频扫描器。
 *
 * 扫描 assets/music/ 下所有音频文件:
 *  1. 递归列出所有文件
 *  2. 用 MediaMetadataRetriever 读取元数据(通过 AssetFileDescriptor)
 *  3. 生成 SongEntity(file_path 存 assets 相对路径,如 "music/周杰伦/晴天.mp3")
 *
 * 首次启动时调用,把内置音频导入数据库。
 */
@Singleton
class AssetScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioExtensions = setOf("mp3", "flac", "wav", "ape", "m4a", "ogg", "wma", "alac", "aiff")

    /**
     * 扫描 assets/music/ 返回所有歌曲实体。
     */
    suspend fun scanAssets(): List<SongEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongEntity>()
        val assetManager = context.assets

        // 递归扫描 assets/music/
        val allFiles = mutableListOf<String>()
        listAssetsRecursive("music", allFiles)

        for (filePath in allFiles) {
            val ext = filePath.substringAfterLast('.', "").lowercase()
            if (ext !in audioExtensions) continue

            try {
                val meta = readAssetMetadata("music/$filePath")
                val fileName = filePath.substringAfterLast('/')

                // 文件夹名取路径的第一级(如 "周杰伦" 或 "classical")
                val folder = filePath.substringBeforeLast('/').substringAfterLast('/')
                    .ifBlank { "根目录" }

                val entity = SongEntity(
                    title = meta.title.ifBlank { fileName.substringBeforeLast('.') },
                    artist = meta.artist,
                    album = meta.album,
                    year = meta.year,
                    genre = meta.genre,
                    durationMs = meta.durationMs,
                    // file_path 存 assets 相对路径(不含 "music/" 前缀,播放时再加)
                    filePath = filePath,
                    folderPath = folder,
                    format = ext,
                    sampleRate = meta.sampleRate,
                    bitDepth = meta.bitDepth,
                    bitRate = meta.bitRate,
                    channels = meta.channels,
                    fileSize = meta.fileSize,
                    coverUri = null
                )
                results.add(entity)
            } catch (e: Exception) {
                // 跳过解析失败的文件
            }
        }
        results
    }

    /** 递归列出 assets 下某目录的所有文件路径 */
    private fun listAssetsRecursive(path: String, output: MutableList<String>) {
        val list = context.assets.list(path) ?: return
        for (item in list) {
            val fullPath = if (path.isEmpty()) item else "$path/$item"
            // 判断是文件还是目录:目录 list 非空,文件 list 为 null
            val subList = context.assets.list(fullPath)
            if (subList != null && subList.isNotEmpty()) {
                listAssetsRecursive(fullPath, output)
            } else {
                // 文件,记录相对于 music/ 的路径
                output.add(fullPath.removePrefix("music/"))
            }
        }
    }

    /** 读取 assets 文件元数据 */
    private fun readAssetMetadata(assetPath: String): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        var afd: AssetFileDescriptor? = null
        return try {
            afd = context.assets.openFd(assetPath)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "未知专辑"
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: ""
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 44100
            val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let { it.toInt() / 1000 } ?: 0
            val channels = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS)?.toIntOrNull() ?: 2

            val ext = assetPath.substringAfterLast('.', "").lowercase()
            val bitDepth = when (ext) {
                "flac", "wav", "ape", "alac", "aiff" -> if (bitRate >= 1411 || sampleRate >= 96000) 24 else 16
                else -> 0
            }

            AudioMetadata(
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
                channels = channels,
                fileSize = afd.length
            )
        } catch (e: Exception) {
            AudioMetadata(title = "", format = assetPath.substringAfterLast('.').lowercase())
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { afd?.close() } catch (_: Exception) {}
        }
    }
}
