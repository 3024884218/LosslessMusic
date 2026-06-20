package com.lossless.music.data.scanner

import android.content.Context
import android.os.Environment
import com.lossless.music.data.local.entity.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外部存储音频扫描器。
 *
 * 扫描手机外部存储中的音频目录(如 /sdcard/Music/MP4_Music/),
 * 不复制文件,直接记录绝对路径到数据库。
 *
 * 与 AssetScanner 的区别:
 *  - AssetScanner: 扫描打包在 APK 内的 assets
 *  - ExternalScanner: 扫描用户通过 adb/push 放到手机的外部存储
 */
@Singleton
class ExternalScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataReader: MetadataReader
) {
    private val audioExtensions = setOf("mp3", "flac", "wav", "ape", "m4a", "ogg", "wma", "alac", "aiff")

    /**
     * 扫描默认外部音乐目录 /sdcard/Music/MP4_Music/。
     * 返回可导入的 SongEntity 列表(filePath 存绝对路径)。
     */
    suspend fun scanDefaultExternalMusicDir(): List<SongEntity> = withContext(Dispatchers.IO) {
        val externalStorage = Environment.getExternalStorageDirectory()
        val musicDir = File(externalStorage, "Music/MP4_Music")
        if (!musicDir.exists() || !musicDir.canRead()) {
            emptyList()
        } else {
            scanDirectory(musicDir)
        }
    }

    /**
     * 扫描任意目录。
     */
    suspend fun scanDirectory(dir: File): List<SongEntity> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.canRead()) return@withContext emptyList()

        dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in audioExtensions }
            .mapNotNull { file ->
                try {
                    val meta = metadataReader.readFromFile(file)
                    val folder = file.parentFile?.name ?: "外部音乐"
                    SongEntity(
                        title = meta.title.ifBlank { file.nameWithoutExtension },
                        artist = meta.artist,
                        album = meta.album,
                        year = meta.year,
                        genre = meta.genre,
                        durationMs = meta.durationMs,
                        // 以绝对路径存储,播放时识别为外部文件
                        filePath = "EXTERNAL:${file.absolutePath}",
                        folderPath = folder,
                        format = meta.format,
                        sampleRate = meta.sampleRate,
                        bitDepth = meta.bitDepth,
                        bitRate = meta.bitRate,
                        channels = meta.channels,
                        fileSize = file.length(),
                        coverUri = null
                    )
                } catch (e: Exception) {
                    null
                }
            }
            .toList()
    }
}
