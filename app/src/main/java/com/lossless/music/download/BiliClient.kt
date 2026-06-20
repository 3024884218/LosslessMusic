package com.lossless.music.download

import android.webkit.CookieManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B站 API 客户端。
 *
 * 功能:
 *  1. 解析视频信息(标题/封面/时长/UP主) — 调用 view API,免登录可用
 *  2. 获取音频流地址 — 调用 player API,需登录 Cookie 获取高清
 *  3. 下载音频流到文件 — 用 HttpURLConnection 流式下载
 *
 * 注意:B站部分 API 需要 wbi 签名(反爬),当前实现基础版,
 * 可能对部分视频失败。完整反爬绕过超出当前范围。
 */
@Singleton
class BiliClient @Inject constructor() {

    /**
     * 从 URL 提取 BV 号。
     *
     * 支持:
     *  - 完整链接: https://www.bilibili.com/video/BV1xx411c7mD
     *  - 短链: https://b23.tv/sv1FyZL (手动跟随跨主机 302 → 真实 B站 URL)
     *  - 纯 BV 号: BV1xx411c7mD
     *
     * 注意:Java HttpURLConnection 的 instanceFollowRedirects 只跟随同主机重定向,
     * b23.tv → www.bilibili.com 是跨主机,必须手动读 Location 头。
     */
    fun extractBvId(input: String): String? {
        // 1. 直接正则匹配(完整链接/纯 BV)
        val direct = Regex("BV[0-9a-zA-Z]{10}").find(input)?.value
        if (direct != null && direct.length == 12) return direct

        // 2. 短链/其他 URL → 手动跟随重定向(最多 5 次),从 Location 头提取 BV
        if (input.startsWith("http", ignoreCase = true)) {
            return resolveBvFromRedirect(input, maxHops = 5)
        }
        return null
    }

    /**
     * 手动跟随 HTTP 重定向链,从任一跳转的 Location 或最终 URL 中提取 BV 号。
     * 解决 HttpURLConnection 不跟随跨主机重定向的问题。
     */
    private fun resolveBvFromRedirect(startUrl: String, maxHops: Int): String? {
        var currentUrl: String = startUrl
        for (i in 0 until maxHops) {
            // 先在当前 URL 里找 BV
            Regex("BV[0-9a-zA-Z]{10}").find(currentUrl)?.value?.let { bv ->
                if (bv.length == 12) return bv
            }
            try {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false  // 关键:手动处理
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                // 3xx 重定向:从 Location 头继续找
                if (code in 300..399 && !location.isNullOrBlank()) {
                    // 相对路径 → 拼成绝对 URL
                    currentUrl = if (location.startsWith("http")) location
                    else URL(currentUrl).toURI().resolve(location).toString()
                    continue
                }
                // 2xx/4xx:在最终 URL 里再找一次
                Regex("BV[0-9a-zA-Z]{10}").find(currentUrl)?.value?.let { bv ->
                    if (bv.length == 12) return bv
                }
                break
            } catch (_: Exception) {
                break
            }
        }
        return null
    }

    /** 解析视频信息 */
    fun fetchVideoInfo(bvId: String, cookie: String = ""): VideoInfo? {
        val apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=$bvId"
        val (json, err) = httpGet(apiUrl, cookie) ?: return null
        if (json == null || err != null) return null
        val data = json.optJSONObject("data") ?: return null
        return VideoInfo(
            bvId = bvId,
            title = data.optString("title"),
            cover = data.optString("pic"),
            duration = data.optLong("duration") * 1000,  // 秒→毫秒
            upName = data.optJSONObject("owner")?.optString("name") ?: "",
            aid = data.optLong("aid"),
            cid = data.optLong("cid")
        )
    }

    /**
     * 获取音频流下载地址(多策略降级)。
     *
     * 策略优先级:
     *  1. fnval=80 (DASH) — 最高音质,需登录
     *  2. fnval=16 (MP4) — 兼容性好,大部分视频可用
     *  3. fnval=1  (FLV) — 最兼容兜底
     *
     * @return Pair<url?, errorMsg?> url 为 null 时 errorMsg 说明原因
     */
    fun fetchAudioStreamUrl(bvId: String, cid: Long, cookie: String = ""): Pair<String?, String?> {
        val strategies = listOf(
            80 to "DASH高清",
            16 to "MP4兼容",
            1  to "FLV兜底"
        )
        for ((fnval, desc) in strategies) {
            val apiUrl = "https://api.bilibili.com/x/player/playurl?bvid=$bvId&cid=$cid&fnval=$fnval&fourk=1"
            val result = httpGet(apiUrl, cookie)
            // httpGet 返回 null = 网络层异常(连接失败/超时/非200)
            if (result == null) {
                continue  // 网络异常,换格式也没用,但试下一个策略兜底
            }
            val (json, err) = result
            // json 为 null = API 返回业务错误(如 -403/-509),err 为错误信息
            if (json == null) {
                // 业务错误不直接失败,降级到下一个策略(不同 fnval 可能返回不同结果)
                continue
            }
            val data = json.optJSONObject("data") ?: continue  // 数据为空,降级

            // DASH:取最高 bandwidth 音频流
            if (fnval == 80) {
                val dashAudio = data.optJSONObject("dash")?.optJSONArray("audio")
                if (dashAudio != null && dashAudio.length() > 0) {
                    var best: JSONObject? = null
                    var bestBandwidth = -1L
                    for (i in 0 until dashAudio.length()) {
                        val obj = dashAudio.optJSONObject(i) ?: continue
                        val bw = obj.optLong("bandwidth", 0)
                        if (bw > bestBandwidth) { bestBandwidth = bw; best = obj }
                    }
                    val chosen = best ?: dashAudio.optJSONObject(0)
                    if (chosen != null) {
                        val url = chosen.optString("base_url").ifBlank { chosen.optString("backup_url") }
                        if (url.isNotBlank()) return Pair(url, null)
                    }
                }
                // DASH 数据为空,继续降级
                continue
            }

            // MP4/FLV:从 durl 取 URL
            val durlArr = data.optJSONArray("durl")
            if (durlArr != null && durlArr.length() > 0) {
                val url = durlArr.optJSONObject(0)?.optString("url")
                if (!url.isNullOrBlank()) return Pair(url, null)
            }
        }
        return Pair(null, "所有策略均失败,建议登录后重试以获得更好音质")
    }

    /**
     * 下载文件到本地,返回下载字节数或错误信息。
     *
     * 内部两层循环:
     *  - 外层:重试(最多 2 次,应对偶发超时)
     *  - 内层:跟随重定向(最多 5 跳,应对 B站 CDN 302)
     *
     * @return Pair<字节数, errorMsg?> 成功时 errorMsg 为 null,失败时字节数为 -1
     */
    fun downloadFile(urlStr: String, cookie: String, outputFile: java.io.File, progress: (Int) -> Unit): Pair<Long, String?> {
        var lastError: String? = null

        for (attempt in 0..1) {  // 最多重试 1 次
            var currentUrl = urlStr  // 每次重试从原始 URL 开始
            var redirectHops = 0
            var success = false

            // 内层:跟随重定向
            while (redirectHops < 5) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    setRequestProperty("Referer", "https://www.bilibili.com")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
                    connectTimeout = 15000
                    readTimeout = 60000
                }
                try {
                    conn.connect()
                    val code = conn.responseCode

                    // 处理重定向(不消耗重试次数)
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location.isNullOrBlank()) {
                            lastError = "服务器返回 $code 重定向但无 Location"
                            break
                        }
                        currentUrl = if (location.startsWith("http")) location
                        else URL(currentUrl).toURI().resolve(location).toString()
                        redirectHops++
                        continue
                    }

                    if (code != 200) {
                        conn.disconnect()
                        lastError = "HTTP $code — 可能需要登录或链接已过期"
                        break
                    }

                    // 200 OK:开始下载
                    val total = conn.contentLengthLong
                    outputFile.parentFile?.mkdirs()
                    var downloaded = 0L
                    conn.inputStream.use { input ->
                        outputFile.outputStream().use { output ->
                            val buf = ByteArray(8192)
                            var n = input.read(buf)
                            while (n > 0) {
                                output.write(buf, 0, n)
                                downloaded += n
                                if (total > 0) progress(((downloaded * 100) / total).toInt())
                                n = input.read(buf)
                            }
                        }
                    }
                    conn.disconnect()
                    return if (downloaded > 0) Pair(downloaded, null) else Pair(-1L, "下载文件为空")
                } catch (e: java.net.SocketTimeoutException) {
                    conn.disconnect()
                    lastError = "连接超时,请检查网络"
                    break  // 跳出重定向循环,进入外层重试
                } catch (e: Exception) {
                    conn.disconnect()
                    lastError = e.message ?: "下载异常"
                    break
                }
            }
            // 内层循环结束(重定向耗尽或出错),外层继续重试
        }
        return Pair(-1L, lastError ?: "未知错误")
    }

    /** 获取 B站 Cookie(从 WebView CookieManager) */
    fun getCookie(): String =
        CookieManager.getInstance().getCookie("https://www.bilibili.com") ?: ""

    /**
     * HTTP GET 请求 B站 API。
     * 返回 Pair<JSONObject?, errorMsg?>; 如果 JSONObject 为 null 表示网络异常,
     * 如果 errorMsg 非空表示 API 返回业务错误(如 -403)。
     */
    private fun httpGet(urlStr: String, cookie: String): Pair<JSONObject?, String?>? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            setRequestProperty("Referer", "https://www.bilibili.com")
            if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
            connectTimeout = 10000
            readTimeout = 15000
        }
        return try {
            conn.connect()
            if (conn.responseCode != 200) return null
            val json = conn.inputStream.bufferedReader().use { it.readText() }.let(::JSONObject)
            val code = json.optInt("code", 0)
            if (code != 0) {
                val msg = json.optString("message", "B站 API 错误 code=$code")
                return Pair(null, msg)
            }
            Pair(json, null)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}

data class VideoInfo(
    val bvId: String,
    val title: String,
    val cover: String,
    val duration: Long,   // 毫秒
    val upName: String,
    val aid: Long,
    val cid: Long
)
