package com.lossless.music.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把音频文件写入系统公共音乐目录（/sdcard/Music/LosslessMusic/...）。
 *
 * 目的：
 *  - 让系统文件管理器、其他播放器、MediaStore 索引都能看到 B 站下载的歌
 *  - 卸载 App 不会被一并删除
 *
 * 适配策略：
 *  - Android 10 (API 29) 及以上：用 MediaStore.Audio.Media + RELATIVE_PATH + IS_PENDING
 *    写入 MediaStore，写入完成后查 DATA 列拿到绝对路径返回（App 自己写入的文件，
 *    即使 Scoped Storage 下也有持久读写权限，可直接 File API 访问）
 *  - Android 9 (API 28) 及以下：直接用 File API 写
 *    Environment.getExternalStoragePublicDirectory(MUSIC)/LosslessMusic/子目录/
 *
 * 返回值：最终文件在公共目录中的绝对路径，失败返回 null。
 * 失败常见原因：用户没授权存储权限（Android 9-）/ 媒体扫描器异常。
 */
@Singleton
class PublicMusicExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** 公共音乐目录下本 App 专属子目录名 */
    private val appRootDirName = "LosslessMusic"

    /**
     * 把源文件写入公共音乐目录。
     *
     * @param sourceFile 已下载好的临时文件（如 filesDir/bili_download/xxx.m4a）
     * @param displayName 最终文件名（含扩展名，如 "歌名.m4a"）
     * @param mimeType MIME 类型（如 "audio/mp4"、"audio/mpeg"）
     * @param subDir 子目录名（如 "B站下载"），最终落在 Music/LosslessMusic/B站下载/
     * @return 最终文件绝对路径；失败返回 null
     */
    suspend fun export(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        subDir: String
    ): String? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(sourceFile, displayName, mimeType, subDir)
        } else {
            exportViaLegacyFile(sourceFile, displayName, subDir)
        }
    }

    // ==================== Android 10+ : MediaStore ====================

    /**
     * 通过 MediaStore 写入公共音乐目录。
     *
     * 流程：
     *  1. 创建一条 IS_PENDING=1 的 MediaStore 记录，指定 RELATIVE_PATH
     *  2. 打开 OutputStream 写入音频数据
     *  3. 把 IS_PENDING 改成 0，让媒体扫描器索引
     *  4. 查 DATA 列拿绝对路径返回
     */
    private fun exportViaMediaStore(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        subDir: String
    ): String? {
        val resolver = context.contentResolver
        // RELATIVE_PATH 必须以 "Music/" 开头才会落到公共音乐目录
        val relativePath = "Music/$appRootDirName/$subDir"

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri: Uri = resolver.insert(collection, values) ?: return null

        try {
            resolver.openOutputStream(itemUri, "w")?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: return null

            // 写完，解除 PENDING 状态
            val doneValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(itemUri, doneValues, null, null)

            // 查 DATA 列拿绝对路径
            return queryAbsolutePath(itemUri)
        } catch (e: Exception) {
            // 写失败，清理掉这条占位记录
            try { resolver.delete(itemUri, null, null) } catch (_: Exception) {}
            return null
        }
    }

    /** 查 MediaStore Uri 的 DATA 列，返回文件绝对路径 */
    private fun queryAbsolutePath(uri: Uri): String? {
        val resolver = context.contentResolver
        resolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return null
    }

    // ==================== Android 9 及以下 : 直接 File API ====================

    /**
     * 旧版本直接用 File API 写入公共音乐目录。
     * 需要 WRITE_EXTERNAL_STORAGE 权限（已在 manifest 中声明，maxSdkVersion=28）。
     */
    private fun exportViaLegacyFile(
        sourceFile: File,
        displayName: String,
        subDir: String
    ): String? {
        return try {
            val musicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = File(musicRoot, "$appRootDirName/$subDir").apply { if (!exists()) mkdirs() }
            val targetFile = File(targetDir, displayName)
            sourceFile.inputStream().use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
            // 通知媒体扫描器索引新文件，否则系统相册/其他 App 短期内看不到
            android.media.MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf("audio/*"), null)
            targetFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
