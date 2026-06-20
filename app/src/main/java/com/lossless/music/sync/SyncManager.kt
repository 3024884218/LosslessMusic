package com.lossless.music.sync

import android.content.Context
import android.util.Log
import com.lossless.music.data.local.dao.SongDao
import com.lossless.music.data.repository.MusicRepository
import com.lossless.music.ui.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * 跨设备同步(通过 WebDAV 导出/导入播放进度)。
 *
 * 功能:
 *  1. exportProgress: 导出所有歌曲的 resumePosition + playCount + isFavorite 到 JSON
 *  2. uploadToWebDAV: 上传 JSON 到 WebDAV 服务器
 *  3. downloadFromWebDAV: 下载 JSON
 *  4. importProgress: 把 JSON 合并到本地数据库(按 filePath 匹配)
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val settingsRepository: SettingsRepository
) {
    /** 导出播放进度到本地 JSON 文件 */
    suspend fun exportProgress(): File = withContext(Dispatchers.IO) {
        val songs = songDao.getAllSongs()
        val arr = JSONArray()
        for (s in songs) {
            arr.put(JSONObject().apply {
                put("filePath", s.filePath)
                put("title", s.title)
                put("resumePosition", s.resumePosition)
                put("playCount", s.playCount)
                put("isFavorite", s.isFavorite)
                put("lastPlayedAt", s.lastPlayedAt)
            })
        }
        val json = JSONObject().apply {
            put("version", 1)
            put("exportTime", System.currentTimeMillis())
            put("songs", arr)
        }
        val outFile = File(context.cacheDir, "playback_progress_export.json")
        outFile.writeText(json.toString(2))
        Log.d("SyncManager", "导出 ${songs.size} 条记录到 ${outFile.absolutePath}")
        outFile
    }

    /** 上传到 WebDAV */
    suspend fun uploadToWebDAV(): Boolean = withContext(Dispatchers.IO) {
        val url = settingsRepository.webdavUrl
        val user = settingsRepository.webdavUser
        val pass = settingsRepository.webdavPass
        if (url.isBlank()) return@withContext false

        val file = exportProgress()
        val conn = (URL("${url.trimEnd('/')}/playback_progress.json").openConnection() as HttpURLConnection).apply {
            doOutput = true
            requestMethod = "PUT"
            if (user.isNotEmpty()) {
                val auth = android.util.Base64.encodeToString("$user:$pass".toByteArray(), android.util.Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $auth")
            }
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 30000
        }
        try {
            conn.outputStream.use { it.write(file.readBytes()) }
            val ok = conn.responseCode in 200..299
            Log.d("SyncManager", "上传结果: ${conn.responseCode}")
            ok
        } catch (e: Exception) {
            Log.e("SyncManager", "上传失败: ${e.message}")
            false
        } finally {
            conn.disconnect()
        }
    }

    /** 从 WebDAV 下载并合并进度 */
    suspend fun downloadAndMerge(): Int = withContext(Dispatchers.IO) {
        val url = settingsRepository.webdavUrl
        val user = settingsRepository.webdavUser
        val pass = settingsRepository.webdavPass
        if (url.isBlank()) return@withContext 0

        val conn = (URL("${url.trimEnd('/')}/playback_progress.json").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            if (user.isNotEmpty()) {
                val auth = android.util.Base64.encodeToString("$user:$pass".toByteArray(), android.util.Base64.NO_WRAP)
                setRequestProperty("Authorization", "Basic $auth")
            }
            connectTimeout = 15000
            readTimeout = 30000
        }
        try {
            if (conn.responseCode != 200) return@withContext 0
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return@withContext mergeProgress(text)
        } catch (e: Exception) {
            Log.e("SyncManager", "下载失败: ${e.message}")
            return@withContext 0
        } finally {
            conn.disconnect()
        }
    }

    /** 把 JSON 合并到本地数据库 */
    private suspend fun mergeProgress(jsonText: String): Int = withContext(Dispatchers.IO) {
        val json = JSONObject(jsonText)
        val arr = json.optJSONArray("songs") ?: return@withContext 0
        val localSongs = songDao.getAllSongs().associateBy { it.filePath }
        var merged = 0
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val path = item.optString("filePath")
            val local = localSongs[path] ?: continue  // 本地没此文件跳过

            // 合并:取最大值
            val newResume = item.optLong("resumePosition")
            val newCount = maxOf(local.playCount, item.optInt("playCount"))
            val newFav = local.isFavorite || item.optBoolean("isFavorite")
            val newLast = maxOf(local.lastPlayedAt, item.optLong("lastPlayedAt"))

            if (newResume != local.resumePosition || newCount != local.playCount ||
                newFav != local.isFavorite || newLast != local.lastPlayedAt) {
                songDao.update(local.copy(
                    resumePosition = newResume,
                    playCount = newCount,
                    isFavorite = newFav,
                    lastPlayedAt = newLast
                ))
                merged++
            }
        }
        Log.d("SyncManager", "合并 $merged 条记录")
        return@withContext merged
    }
}
