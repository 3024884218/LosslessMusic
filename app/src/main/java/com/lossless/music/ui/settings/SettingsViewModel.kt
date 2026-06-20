package com.lossless.music.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lossless.music.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 设置页 ViewModel。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val musicRepository: MusicRepository,
    private val syncManager: com.lossless.music.sync.SyncManager
) : ViewModel() {

    private val _hiRes = MutableStateFlow(settingsRepository.hiResOutput)
    val hiRes: StateFlow<Boolean> = _hiRes.asStateFlow()

    private val _onlyWifi = MutableStateFlow(settingsRepository.downloadOnlyWifi)
    val onlyWifi: StateFlow<Boolean> = _onlyWifi.asStateFlow()

    private val _autoImport = MutableStateFlow(settingsRepository.autoImportOnLaunch)
    val autoImport: StateFlow<Boolean> = _autoImport.asStateFlow()

    private val _loudnessNorm = MutableStateFlow(settingsRepository.loudnessNormalizationEnabled)
    val loudnessNorm: StateFlow<Boolean> = _loudnessNorm.asStateFlow()

    private val _onlineLyrics = MutableStateFlow(settingsRepository.onlineLyricsEnabled)
    val onlineLyrics: StateFlow<Boolean> = _onlineLyrics.asStateFlow()

    private val _webdavUrl = MutableStateFlow(settingsRepository.webdavUrl)
    val webdavUrl: StateFlow<String> = _webdavUrl.asStateFlow()

    private val _webdavUser = MutableStateFlow(settingsRepository.webdavUser)
    val webdavUser: StateFlow<String> = _webdavUser.asStateFlow()

    private val _webdavPass = MutableStateFlow(settingsRepository.webdavPass)
    val webdavPass: StateFlow<String> = _webdavPass.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _cacheSize = MutableStateFlow("计算中...")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun setHiRes(enabled: Boolean) {
        settingsRepository.hiResOutput = enabled
        _hiRes.value = enabled
    }

    fun setOnlyWifi(enabled: Boolean) {
        settingsRepository.downloadOnlyWifi = enabled
        _onlyWifi.value = enabled
    }

    fun setAutoImport(enabled: Boolean) {
        settingsRepository.autoImportOnLaunch = enabled
        _autoImport.value = enabled
    }

    fun setLoudnessNorm(enabled: Boolean) {
        settingsRepository.loudnessNormalizationEnabled = enabled
        _loudnessNorm.value = enabled
    }

    fun setOnlineLyrics(enabled: Boolean) {
        settingsRepository.onlineLyricsEnabled = enabled
        _onlineLyrics.value = enabled
    }

    fun setWebdavUrl(url: String) {
        settingsRepository.webdavUrl = url
        _webdavUrl.value = url
    }

    fun setWebdavUser(user: String) {
        settingsRepository.webdavUser = user
        _webdavUser.value = user
    }

    fun setWebdavPass(pass: String) {
        settingsRepository.webdavPass = pass
        _webdavPass.value = pass
    }

    /** 上传播放进度到 WebDAV */
    fun uploadProgress() {
        viewModelScope.launch {
            _syncing.value = true
            val ok = syncManager.uploadToWebDAV()
            _syncing.value = false
            _toast.value = if (ok) "上传成功" else "上传失败,请检查 WebDAV 配置"
        }
    }

    /** 从 WebDAV 下载并合并 */
    fun downloadProgress() {
        viewModelScope.launch {
            _syncing.value = true
            val n = syncManager.downloadAndMerge()
            _syncing.value = false
            _toast.value = if (n >= 0) "合并 $n 条进度记录" else "下载失败"
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            musicRepository.clearCache()
            refreshCacheSize()
            _toast.value = "缓存已清除"
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val bytes = musicRepository.calculateCacheSize()
            _cacheSize.value = formatBytes(bytes)
        }
    }

    fun clearToast() {
        _toast.value = null
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
