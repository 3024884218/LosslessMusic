package com.lossless.music.ui.download

import android.content.Context
import android.webkit.CookieManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lossless.music.data.repository.MusicRepository
import com.lossless.music.download.BiliClient
import com.lossless.music.download.VideoInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val biliClient: BiliClient,
    private val repository: MusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loginUid = MutableStateFlow("")
    val loginUid: StateFlow<String> = _loginUid.asStateFlow()

    private val _showWebView = MutableStateFlow(false)
    val showWebView: StateFlow<Boolean> = _showWebView.asStateFlow()

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _parseResult = MutableStateFlow<VideoInfoUi?>(null)
    val parseResult: StateFlow<VideoInfoUi?> = _parseResult.asStateFlow()

    private val _isParsing = MutableStateFlow(false)
    val isParsing: StateFlow<Boolean> = _isParsing.asStateFlow()

    private val _tasks = MutableStateFlow<List<DownloadUiModel>>(emptyList())
    val tasks: StateFlow<List<DownloadUiModel>> = _tasks.asStateFlow()

    private var taskCounter = 0L

    fun showLoginWebView() { _showWebView.value = true }
    fun hideLoginWebView() { _showWebView.value = false }

    fun onLoginSuccess() {
        val cookies = CookieManager.getInstance().getCookie("https://www.bilibili.com") ?: ""
        val uid = cookies.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("DedeUserID=") }?.removePrefix("DedeUserID=") ?: ""
        _loginUid.value = uid
        _isLoggedIn.value = true
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        _isLoggedIn.value = false
        _loginUid.value = ""
    }

    fun onUrlChange(url: String) {
        _inputUrl.value = url
        _parseResult.value = null
    }

    /** 解析视频信息(真实调B站API) */
    suspend fun parseVideo(): String = withContext(Dispatchers.IO) {
        val url = _inputUrl.value.trim()
        if (url.isBlank()) return@withContext "请输入链接"

        val bvId = biliClient.extractBvId(url)
        if (bvId == null) {
            return@withContext "无法识别BV号,请检查链接"
        }

        _isParsing.value = true
        try {
            val cookie = biliClient.getCookie()
            val info = biliClient.fetchVideoInfo(bvId, cookie)
            if (info == null) {
                return@withContext "解析失败,可能是网络问题或视频不存在"
            }
            _parseResult.value = VideoInfoUi(
                title = info.title,
                durationText = formatDuration(info.duration),
                isHighQuality = _isLoggedIn.value,
                bvId = info.bvId,
                cid = info.cid
            )
            return@withContext "解析成功:${info.title}"
        } finally {
            _isParsing.value = false
        }
    }

    /** 开始下载(真实下载音频流) */
    suspend fun startDownload(): String = withContext(Dispatchers.IO) {
        val info = _parseResult.value ?: return@withContext "请先解析视频"

        val taskId = ++taskCounter
        val task = DownloadUiModel(
            id = taskId,
            title = info.title,
            status = DownloadStatusUi.DOWNLOADING,
            progress = 0,
            statusText = "下载中..."
        )
        _tasks.value = _tasks.value + task

        try {
            val cookie = biliClient.getCookie()
            // 1. 获取音频流地址(多策略降级)
            updateTask(taskId, status = DownloadStatusUi.DOWNLOADING, statusText = "获取音频流...")
            val (audioUrl, urlErr) = biliClient.fetchAudioStreamUrl(info.bvId, info.cid, cookie)
            if (audioUrl == null) {
                val hint = if (cookie.isBlank()) "\n(提示:未登录可能限制音质,建议先登录B站)" else ""
                updateTask(taskId, DownloadStatusUi.FAILED, 0, "获取失败")
                return@withContext "下载失败:${urlErr ?: "未知错误"}$hint"
            }

            // 2. 下载到文件(跟随重定向+自动重试)
            updateTask(taskId, status = DownloadStatusUi.DOWNLOADING, statusText = "下载中...")
            val downloadDir = File(context.filesDir, "bili_download").apply { mkdirs() }
            val safeTitle = info.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val outFile = File(downloadDir, "${safeTitle}.m4a")

            val (downloaded, dlErr) = biliClient.downloadFile(audioUrl, cookie, outFile) { progress ->
                updateTask(taskId, status = DownloadStatusUi.DOWNLOADING, progress = progress, statusText = "下载中 $progress%")
            }

            if (downloaded < 0) {
                updateTask(taskId, DownloadStatusUi.FAILED, 0, "下载失败")
                return@withContext "下载失败:${dlErr ?: "未知错误"}"
            }

            // 3. 导入到音乐库
            updateTask(taskId, status = DownloadStatusUi.DOWNLOADING, statusText = "导入音乐库...")
            importToLibrary(outFile, info.title)

            updateTask(taskId, DownloadStatusUi.COMPLETED, 100, "已完成")
            _parseResult.value = null
            _inputUrl.value = ""
            return@withContext "下载完成:${info.title}"
        } catch (e: Exception) {
            updateTask(taskId, DownloadStatusUi.FAILED, 0, e.message ?: "未知错误")
            return@withContext "下载失败:${e.message}"
        }
    }

    /** 把下载的音频文件导入到音乐库 */
    private suspend fun importToLibrary(file: File, title: String) {
        val musicDir = File(context.filesDir, "music/B站下载").apply { mkdirs() }
        val targetFile = File(musicDir, file.name)
        file.copyTo(targetFile, overwrite = true)
        // 通过 Repository 导入到数据库(读取元数据 + 插入记录)
        repository.importFromFile(targetFile, "B站下载")
    }

    private fun updateTask(id: Long, status: DownloadStatusUi, progress: Int = 0, statusText: String) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) it.copy(status = status, progress = progress, statusText = statusText)
            else it
        }
    }

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
