package com.lossless.music.ui.lyrics

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在线歌词获取。
 *
 * 数据源:网易云音乐 API(免登录,公开接口)
 *  1. /search 搜索歌曲,按标题+艺术家匹配,优先选时长接近的
 *  2. /lyric 获取歌词(LRC 格式)
 *
 * 备选:LRCLIB(开源歌词库,https://lrclib.net)
 */
@Singleton
class OnlineLyricsFetcher @Inject constructor() {

    /**
     * 获取歌词。
     * @param title 歌曲标题
     * @param artist 艺术家
     * @param durationMs 时长(毫秒,用于匹配验证)
     * @return LRC 格式歌词字符串,失败返回 null
     */
    fun fetchLyrics(title: String, artist: String, durationMs: Long): String? {
        // 1. 先尝试网易云
        val neteaseLrc = fetchFromNetease(title, artist, durationMs)
        if (!neteaseLrc.isNullOrBlank()) {
            Log.d("OnlineLyrics", "网易云歌词获取成功: $title")
            return neteaseLrc
        }

        // 2. 备选 LRCLIB
        val lrclibLrc = fetchFromLrclib(title, artist, durationMs)
        if (!lrclibLrc.isNullOrBlank()) {
            Log.d("OnlineLyrics", "LRCLIB 歌词获取成功: $title")
            return lrclibLrc
        }

        Log.w("OnlineLyrics", "歌词获取失败: $title - $artist")
        return null
    }

    /** 网易云歌词 API */
    private fun fetchFromNetease(title: String, artist: String, durationMs: Long): String? {
        return try {
            // 1. 搜索歌曲
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get?s=$query&type=1&limit=5"
            val searchJson = httpGet(searchUrl) ?: return null

            val songs = searchJson.optJSONObject("result")?.optJSONArray("songs") ?: return null
            if (songs.length() == 0) return null

            // 找时长最接近的(差 < 5 秒)
            val durationSec = durationMs / 1000
            var bestId: Long = -1
            var bestDiff = Long.MAX_VALUE
            for (i in 0 until songs.length()) {
                val s = songs.optJSONObject(i) ?: continue
                val dt = s.optLong("dt") / 1000  // 网易云用 dt(毫秒)或 duration(秒)
                val dur = if (dt > 0) dt else s.optLong("duration")
                val diff = kotlin.math.abs(dur - durationSec)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestId = s.optLong("id")
                }
            }
            if (bestId < 0 || bestDiff > 10) return null  // 时长差超 10 秒认为不匹配

            // 2. 获取歌词
            val lyricUrl = "https://music.163.com/api/song/lyric?id=$bestId&lv=1&kv=1&tv=-1"
            val lyricJson = httpGet(lyricUrl) ?: return null
            val lrc = lyricJson.optJSONObject("lrc")?.optString("lyric") ?: return null
            lrc.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("OnlineLyrics", "网易云歌词异常: ${e.message}")
            null
        }
    }

    /** LRCLIB 歌词 API */
    private fun fetchFromLrclib(title: String, artist: String, durationMs: Long): String? {
        return try {
            val t = URLEncoder.encode(title, "UTF-8")
            val a = URLEncoder.encode(artist, "UTF-8")
            val dur = durationMs / 1000.0
            val url = "https://lrclib.net/api/get?artist_name=$a&track_name=$t&duration=$dur"
            val json = httpGet(url) ?: return null
            val plainLrc = json.optString("syncedLyrics")
            plainLrc.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(urlStr: String): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0")
            connectTimeout = 8000
            readTimeout = 10000
        }
        return try {
            conn.connect()
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
